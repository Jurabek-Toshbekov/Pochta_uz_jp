package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.TrackEventBatchRequest;
import uz.pochtajp.api.miniapp.dto.TrackEventRequest;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.RateLimitService;

/**
 * {@code POST /api/miniapp/events} — event batch (§6.2, §12).
 *
 * <p>{@code user_id} body'dan olinmaydi — faqat tekshirilgan {@code initData}dan (§7.1).
 * Noto'g'ri nomli event'lar butun batch'ni yiqitmaydi, faqat tashlab yuboriladi:
 * analitika yozilmasa ham ilova ishlashda davom etishi kerak (§6.2).
 */
@RestController
@RequestMapping("/api/miniapp/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final EventLogger eventLogger;
    private final RateLimitService rateLimitService;

    public EventController(EventLogger eventLogger, RateLimitService rateLimitService) {
        this.eventLogger = eventLogger;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ResponseEntity<Void> track(@Valid @RequestBody TrackEventBatchRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/events");

        List<TrackedEvent> accepted = new ArrayList<>(request.events().size());
        int rejected = 0;
        for (TrackEventRequest event : request.events()) {
            if (!EventName.isAllowedFromClient(event.name())) {
                rejected++;
                continue;
            }
            accepted.add(TrackedEvent.of(event.name(), EventSource.MINIAPP)
                    .user(principal.userId())
                    .session(event.sessionId())
                    .post(event.postId())
                    .platform(event.platform())
                    .occurredAt(event.occurredAt())
                    .properties(event.properties())
                    .build());
        }

        if (rejected > 0) {
            log.warn("Ro'yxatda yo'q {} event tashlab yuborildi: user_id={}", rejected, principal.userId());
        }
        eventLogger.trackAll(accepted);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
