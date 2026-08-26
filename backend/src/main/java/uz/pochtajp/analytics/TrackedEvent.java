package uz.pochtajp.analytics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import uz.pochtajp.domain.enums.EventSource;

/**
 * Yozilishi kerak bo'lgan bitta event (§6.1).
 *
 * <p>Immutable — {@link EventLogger} uni boshqa thread'da saqlaydi.
 */
public record TrackedEvent(
        String eventName,
        UUID userId,
        UUID sessionId,
        UUID postId,
        EventSource source,
        String platform,
        Map<String, Object> properties,
        Instant occurredAt
) {

    public TrackedEvent {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static Builder of(String eventName, EventSource source) {
        return new Builder(eventName, source);
    }

    /** Qisqa yo'l: tizim ichidagi event (foydalanuvchisiz). */
    public static TrackedEvent system(String eventName, Map<String, Object> properties) {
        return of(eventName, EventSource.SYSTEM).properties(properties).build();
    }

    public static final class Builder {

        private final String eventName;
        private final EventSource source;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private UUID userId;
        private UUID sessionId;
        private UUID postId;
        private String platform;
        private Instant occurredAt;

        private Builder(String eventName, EventSource source) {
            this.eventName = eventName;
            this.source = source;
        }

        public Builder user(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder session(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder post(UUID postId) {
            this.postId = postId;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder property(String key, Object value) {
            if (key != null && value != null) {
                properties.put(key, value);
            }
            return this;
        }

        public Builder properties(Map<String, Object> values) {
            if (values != null) {
                values.forEach(this::property);
            }
            return this;
        }

        public TrackedEvent build() {
            return new TrackedEvent(eventName, userId, sessionId, postId, source, platform,
                    properties, occurredAt);
        }
    }
}
