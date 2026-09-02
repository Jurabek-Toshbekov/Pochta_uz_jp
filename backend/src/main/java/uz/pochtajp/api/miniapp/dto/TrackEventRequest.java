package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mini App yuboradigan bitta event (§6.2).
 *
 * <p>{@code userId} bu yerda YO'Q — u faqat {@code initData}dan olinadi (§7.1).
 *
 * @param name       event nomi ({@link uz.pochtajp.analytics.EventName#CLIENT_ALLOWED} ichidan)
 * @param sessionId  Mini App ochilganda generatsiya qilinadi
 * @param postId     tegishli e'lon (bo'lsa)
 * @param platform   ios | android | tdesktop | web
 * @param properties event xossalari (PII bo'lmaydi — server tomonda ham filtrlanadi)
 * @param occurredAt klientdagi vaqt; kelmasa server vaqti olinadi
 */
public record TrackEventRequest(
        @NotBlank @Size(max = 64) String name,
        UUID sessionId,
        UUID postId,
        @Size(max = 24) String platform,
        Map<String, Object> properties,
        Instant occurredAt
) {
}
