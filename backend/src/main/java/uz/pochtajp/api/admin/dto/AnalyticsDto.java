package uz.pochtajp.api.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Analitika javoblari (§11.2 — /analytics, /search-insights).
 *
 * <p>Ataylab bitta faylda ichma-ich yozilgan: bularning hammasi bitta
 * sahifaning bo'laklari va alohida-alohida hech qayerda ishlatilmaydi.
 * O'ntadan ortiq bir qatorlik faylni yoyib yuborish o'qishni osonlashtirmaydi.
 *
 * <p>Har bir raqam qaysi view'dan kelishini `AdminAnalyticsService` da
 * ko'rish mumkin — SQL faqat o'sha yerda.
 */
public final class AnalyticsDto {

    private AnalyticsDto() {
    }

    /** Bosh sahifadagi KPI kartalari (§11.2 — Umumiy ko'rinish). */
    public record Overview(
            long postsToday,
            long postsYesterday,
            long publishedToday,
            long dauToday,
            long dauYesterday,
            BigDecimal publishConversion,
            BigDecimal fillRate,
            long openReports,
            long pendingPosts,
            BigDecimal zeroResultRate,
            BigDecimal medianTimeToPublishSeconds
    ) {
    }

    /** Kunlik e'lonlar grafigi: SEND va CARRY alohida chiziq. */
    public record PostDailyPoint(
            LocalDate date,
            String postType,
            String direction,
            long createdCount,
            long publishedCount
    ) {
    }

    /** Voronka qadami. {@code conversionFromPrevious} — oldingi qadamdan o'tish ulushi. */
    public record FunnelStep(
            String stepKey,
            int stepIndex,
            long usersCount,
            BigDecimal conversionFromPrevious,
            BigDecimal conversionFromStart
    ) {
    }

    /** Tashlab ketish: qaysi qadamda nechta odam to'xtagan. */
    public record AbandonRow(
            String lastStep,
            long abandonCount
    ) {
    }

    /** Kogorta qatori (§6.3 — D1/D7/D30). */
    public record CohortRow(
            LocalDate cohortDate,
            long cohortSize,
            long d1,
            long d7,
            long d30,
            BigDecimal d1Rate,
            BigDecimal d7Rate,
            BigDecimal d30Rate
    ) {
    }

    /** Narx indeksi: yo'nalish x oy, mediana. */
    public record PriceIndexPoint(
            LocalDate month,
            String direction,
            String originAirport,
            String destAirport,
            String currency,
            long sampleSize,
            BigDecimal medianPerKg
    ) {
    }

    /** Talab/taklif issiqlik xaritasining bitta katagi. */
    public record SupplyDemandCell(
            LocalDate week,
            String direction,
            long carryCount,
            long sendCount,
            BigDecimal ratio
    ) {
    }

    /** Natijasiz qidiruv yo'nalishi — qoplanmagan talab. */
    public record ZeroResultRoute(
            String originAirport,
            String destAirport,
            String direction,
            String postType,
            long searchCount
    ) {
    }

    /** Qidiruv salomatligi (kunlik). */
    public record SearchDailyPoint(
            LocalDate date,
            long searchCount,
            long zeroResultCount,
            long clickedCount,
            BigDecimal avgLatencyMs
    ) {
    }

    /** Eng ko'p qidirilgan va eng ko'p e'lon bor yo'nalishlar yonma-yon. */
    public record RouteDemandSupply(
            String originAirport,
            String destAirport,
            long searchCount,
            long postCount
    ) {
    }

    /** Mavsumiylik katagi. */
    public record SeasonalityCell(
            int dayOfWeek,
            int monthOfYear,
            String postType,
            long postCount
    ) {
    }

    /** Xabarnoma statistikasi (§11.2 — /notifications). */
    public record NotificationStats(
            long sentTotal,
            long openedTotal,
            long failedTotal,
            BigDecimal ctr,
            long activeSubscriptions,
            List<NotificationDailyPoint> daily
    ) {
    }

    public record NotificationDailyPoint(
            LocalDate date,
            long sentCount,
            long openedCount,
            long failedCount
    ) {
    }

    /** Bitim tasdiqlash ulushi (§6.4, 1-band) — haqiqiy natija. */
    public record DealConfirmationPoint(
            LocalDate month,
            String direction,
            long publishedCount,
            long confirmedCount,
            BigDecimal confirmationRate
    ) {
    }

    /** Yopish sabablari taqsimoti (§6.4, 3-band). */
    public record CloseReasonRow(
            String reason,
            long postCount
    ) {
    }

    /** Reytting taqsimoti. */
    public record ReviewStatsPoint(
            LocalDate month,
            long reviewCount,
            BigDecimal avgRating,
            long negativeCount
    ) {
    }

    /** Match latency taqsimoti (oylik mediana). */
    public record MatchLatencyPoint(
            LocalDate month,
            String direction,
            long sampleSize,
            BigDecimal medianMinutes
    ) {
    }
}
