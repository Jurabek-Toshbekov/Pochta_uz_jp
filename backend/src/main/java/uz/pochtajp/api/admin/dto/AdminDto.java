package uz.pochtajp.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API javoblari (§11.2, §12).
 *
 * <p>Entity hech qachon API'dan qaytarilmaydi (§14) — bu yerdagi record'lar
 * o'sha chegara. Telefon raqami faqat moderatsiya uchun zarur bo'lgan
 * joyda va faqat maskalangan holda beriladi (§1.7).
 */
public final class AdminDto {

    private AdminDto() {
    }

    /** Sahifalangan ro'yxat. Offset pagination — admin jadvali uchun yetarli. */
    public record Page<T>(List<T> items, long total, int page, int size) {

        public static <T> Page<T> of(List<T> items, long total, int page, int size) {
            return new Page<>(items, total, page, size);
        }
    }

    /** Kirish javobi (§11.1). */
    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UUID userId,
            String role
    ) {
    }

    /** Moderatsiya jadvalining bitta qatori (§11.2 — /posts). */
    public record PostRow(
            UUID id,
            Instant createdAt,
            Instant publishedAt,
            String status,
            String postType,
            String direction,
            String originAirport,
            String destAirport,
            LocalDate departDate,
            LocalDate deadlineDate,
            BigDecimal priceAmount,
            String priceCurrency,
            String priceUnit,
            BigDecimal weightKg,
            String riskLevel,
            List<String> categories,
            UUID userId,
            String username,
            String userDisplayName,
            int viewCount,
            int contactRevealCount,
            long reportCount,
            Long channelMessageId,
            String rejectReason
    ) {
    }

    /** E'lon tafsiloti — moderator ko'radigan to'liq ko'rinish. */
    public record PostDetail(
            PostRow row,
            String comment,
            String finalDestination,
            String originCityFree,
            String destCityFree,
            boolean safetyChecklistOk,
            String contactTelegram,
            String contactPhoneMasked,
            String source
    ) {
    }

    /** Foydalanuvchilar jadvali (§11.2 — /users). */
    public record UserRow(
            UUID id,
            long telegramId,
            String username,
            String displayName,
            String role,
            String status,
            String verificationLevel,
            int trustScore,
            long postCount,
            long reportCount,
            Instant lastSeenAt,
            Instant createdAt
    ) {
    }

    /** Foydalanuvchi profili: tarix va oxirgi harakatlar. */
    public record UserDetail(
            UserRow row,
            String uiLanguage,
            String blockedReason,
            boolean phoneVerified,
            long publishedCount,
            long revealsMade,
            long revealsReceived,
            List<PostRow> recentPosts,
            List<EventRow> recentEvents
    ) {
    }

    /** Foydalanuvchi harakatlari lentasi. */
    public record EventRow(
            String eventName,
            String source,
            Map<String, Object> properties,
            Instant occurredAt
    ) {
    }

    /** Shikoyatlar navbati (§11.2 — /reports). */
    public record ReportRow(
            UUID id,
            String reason,
            String status,
            String details,
            UUID postId,
            UUID reportedUserId,
            String reportedUsername,
            UUID reporterId,
            Instant createdAt,
            Instant resolvedAt
    ) {
    }

    /** Audit jurnali qatori (§11.2 — /audit). */
    public record AuditRow(
            long id,
            UUID actorId,
            String actorUsername,
            String action,
            String entity,
            String entityId,
            Map<String, Object> payload,
            Instant createdAt
    ) {
    }

    /** Sozlama (§11.2 — /settings). */
    public record SettingRow(
            String key,
            Object value,
            String valueType,
            String titleUz,
            String descriptionUz,
            Instant updatedAt
    ) {
    }
}
