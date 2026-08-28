package uz.pochtajp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.common.exception.RateLimitException;
import uz.pochtajp.config.AppProperties;
import uz.pochtajp.config.SettingKeys;
import uz.pochtajp.domain.enums.EventSource;

/**
 * So'rov chegaralari (§7.2): 60 API so'rov/daqiqa. Oshsa 429 + {@code rate_limit_hit}.
 *
 * <p>Hozircha xotirada — bitta monolit instansiya uchun yetarli (§15: mikroservis yo'q).
 * Bir necha instansiya bo'lganda bu klass Redis'ga o'tadi, tashqi interfeys o'zgarmaydi.
 *
 * <p>E'lon soni chegarasi (5 ta/kun) e'lon yaratish oqimi bilan birga 1-bosqichda
 * {@code posts} jadvalidan hisoblanadi — u xotiradagi hisoblagichga ishonmaydi.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_USERS = 50_000;

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final AppProperties appProperties;
    private final SettingsService settingsService;
    private final EventLogger eventLogger;

    public RateLimitService(AppProperties appProperties,
                            SettingsService settingsService,
                            EventLogger eventLogger) {
        this.appProperties = appProperties;
        this.settingsService = settingsService;
        this.eventLogger = eventLogger;
    }

    /**
     * Daqiqadagi so'rov chegarasini tekshiradi.
     *
     * @throws RateLimitException chegara oshsa (429)
     */
    public void checkApiRequest(UUID userId, String endpoint) {
        if (userId == null) {
            return;
        }
        // Chegara admin panelidan boshqariladi (§11.2); sozlama yo'q bo'lsa
        // environment qiymati ishlatiladi.
        int limit = settingsService.number(SettingKeys.RATE_LIMIT_REQUESTS_PER_MINUTE,
                appProperties.rateLimitRequestsPerMinute());
        Instant now = Instant.now();

        Window window = windows.compute(userId, (key, existing) -> {
            if (existing == null || existing.startedAt.plus(WINDOW).isBefore(now)) {
                return new Window(now);
            }
            return existing;
        });
        int used = window.counter.incrementAndGet();

        if (windows.size() > MAX_TRACKED_USERS) {
            evictExpired(now);
        }

        if (used > limit) {
            long retryAfter = Math.max(1, Duration.between(now, window.startedAt.plus(WINDOW)).getSeconds());
            log.warn("Rate limit: user_id={} endpoint={} used={} limit={}", userId, endpoint, used, limit);
            eventLogger.track(TrackedEvent.of(EventName.RATE_LIMIT_HIT, EventSource.MINIAPP)
                    .user(userId)
                    .property("endpoint", endpoint)
                    .property("limit", limit)
                    .property("window_seconds", WINDOW.getSeconds())
                    .build());
            throw new RateLimitException(
                    "Juda ko'p so'rov yubordingiz. " + retryAfter + " soniyadan keyin qayta urinib ko'ring.",
                    retryAfter);
        }
    }

    private void evictExpired(Instant now) {
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt.plus(WINDOW).isBefore(now));
    }

    private static final class Window {

        private final Instant startedAt;
        private final AtomicInteger counter = new AtomicInteger();

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
