package uz.pochtajp.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.domain.EventLog;
import uz.pochtajp.repository.EventLogRepository;

/**
 * Analitika event'larini yozadi (§6.2).
 *
 * <p>Ikki mutlaq qoida:
 * <ul>
 *   <li><b>Asosiy oqim to'xtamaydi.</b> Yozish {@code @Async} va har qanday
 *       xato tutib olinadi — event yo'qolsa ham foydalanuvchi so'rovi bajariladi.</li>
 *   <li><b>Append-only.</b> Faqat INSERT. UPDATE ham, DELETE ham qilinmaydi (§1.1).</li>
 * </ul>
 *
 * <p>PII himoyasi: {@code properties} ichidan telefon/ism kabi kalitlar tashlab
 * yuboriladi (§1.7) — analitikaga ular kerak emas.
 */
@Service
public class EventLogger {

    private static final Logger log = LoggerFactory.getLogger(EventLogger.class);

    private static final int EVENT_NAME_MAX = 64;
    private static final int PLATFORM_MAX = 24;
    private static final int MAX_PROPERTIES = 32;
    private static final int MAX_VALUE_LENGTH = 512;

    /** Bu kalitlar hech qachon saqlanmaydi — PII (§1.7). */
    private static final Set<String> BLOCKED_PROPERTY_KEYS = Set.of(
            "phone", "phone_number", "telefon", "contact", "contact_phone",
            "first_name", "last_name", "full_name", "ism", "email", "init_data", "token"
    );

    private final EventLogRepository eventLogRepository;

    public EventLogger(EventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    /** Bitta event. Chaqiruvchi natijani kutmaydi. */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void track(TrackedEvent event) {
        persist(List.of(event));
    }

    /** Batch (§6.2 — Mini App 10 event yoki 5 soniyada bir yuboradi). */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackAll(Collection<TrackedEvent> events) {
        persist(events);
    }

    /**
     * REQUIRES_NEW ataylab: event chaqiruvchi tranzaksiya bilan birga rollback
     * BO'LMASLIGI kerak. Masalan chegara oshganda {@code rate_limit_hit} yoziladi
     * va shundan keyin 429 tashlanadi — event yo'qolsa, chegaraga urilgan
     * foydalanuvchilarni sanab bo'lmaydi (§1.6).
     *
     * <p>Bu metod {@code private}: {@link #track} va {@link #trackAll} bir-birini
     * chaqirmaydi, aks holda Spring proksisi chetlab o'tilib REQUIRES_NEW
     * ishlamay qolardi.
     */
    private void persist(Collection<TrackedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try {
            List<EventLog> entities = new ArrayList<>(events.size());
            for (TrackedEvent event : events) {
                if (event == null || event.eventName() == null || event.eventName().isBlank()) {
                    continue;
                }
                entities.add(toEntity(event));
            }
            if (!entities.isEmpty()) {
                eventLogRepository.saveAll(entities);
            }
        } catch (Exception ex) {
            // Event yo'qolishi — yomon, lekin so'rovni buzish undan yomonroq (§6.2).
            log.warn("Event yozilmadi ({} dona): {}", events.size(), ex.getMessage());
        }
    }

    private EventLog toEntity(TrackedEvent event) {
        EventLog entity = new EventLog();
        entity.setEventName(truncate(event.eventName(), EVENT_NAME_MAX));
        entity.setUserId(event.userId());
        entity.setSessionId(event.sessionId());
        entity.setPostId(event.postId());
        entity.setSource(event.source());
        entity.setPlatform(truncate(event.platform(), PLATFORM_MAX));
        entity.setProperties(sanitize(event.properties()));
        entity.setOccurredAt(event.occurredAt());
        return entity;
    }

    /** PII kalitlarini olib tashlaydi, hajmni cheklaydi. */
    private Map<String, Object> sanitize(Map<String, Object> properties) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (properties == null) {
            return clean;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (clean.size() >= MAX_PROPERTIES) {
                break;
            }
            String key = entry.getKey();
            if (key == null || BLOCKED_PROPERTY_KEYS.contains(key.toLowerCase())) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String text) {
                clean.put(key, truncate(text, MAX_VALUE_LENGTH));
            } else if (value != null) {
                clean.put(key, value);
            }
        }
        return clean;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
