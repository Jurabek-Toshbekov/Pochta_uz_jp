package uz.pochtajp.api.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.admin.dto.AnalyticsDto;
import uz.pochtajp.service.AdminAnalyticsService;

/**
 * Analitika va qidiruv tahlili (§11.2, §12).
 *
 * <p>Sana oralig'i berilmasa oxirgi 30 kun olinadi — cheklovsiz so'rov
 * butun jadvalni skanerlaydi, bu esa vaqt o'tishi bilan sekinlashadi.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAnalyticsController {

    /** Standart oraliq: oxirgi 30 kun. */
    private static final int DEFAULT_DAYS = 30;

    /** Uzoq muddatli grafiklar uchun: oxirgi 12 oy. */
    private static final int DEFAULT_MONTHS = 12;

    private final AdminAnalyticsService analytics;

    public AdminAnalyticsController(AdminAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public AnalyticsDto.Overview overview() {
        return analytics.overview();
    }

    @GetMapping("/analytics/posts-daily")
    public List<AnalyticsDto.PostDailyPoint> postsDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.postsDaily(fromOrDefault(from, DEFAULT_DAYS), toOrToday(to));
    }

    @GetMapping("/analytics/funnel")
    public List<AnalyticsDto.FunnelStep> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.funnel(fromOrDefault(from, DEFAULT_DAYS), toOrToday(to));
    }

    @GetMapping("/analytics/abandon")
    public List<AnalyticsDto.AbandonRow> abandon(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.abandonByStep(fromOrDefault(from, DEFAULT_DAYS), toOrToday(to));
    }

    @GetMapping("/analytics/cohorts")
    public List<AnalyticsDto.CohortRow> cohorts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.cohorts(fromOrDefault(from, 90), toOrToday(to));
    }

    @GetMapping("/analytics/price-index")
    public List<AnalyticsDto.PriceIndexPoint> priceIndex(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String direction) {
        return analytics.priceIndex(monthsAgo(from), toOrToday(to), direction);
    }

    @GetMapping("/analytics/supply-demand")
    public List<AnalyticsDto.SupplyDemandCell> supplyDemand(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.supplyDemand(fromOrDefault(from, 180), toOrToday(to));
    }

    @GetMapping("/analytics/match-latency")
    public List<AnalyticsDto.MatchLatencyPoint> matchLatency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.matchLatency(monthsAgo(from), toOrToday(to));
    }

    @GetMapping("/analytics/deal-confirmation")
    public List<AnalyticsDto.DealConfirmationPoint> dealConfirmation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.dealConfirmation(monthsAgo(from), toOrToday(to));
    }

    @GetMapping("/analytics/close-reasons")
    public List<AnalyticsDto.CloseReasonRow> closeReasons(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.closeReasons(monthsAgo(from), toOrToday(to));
    }

    @GetMapping("/analytics/reviews")
    public List<AnalyticsDto.ReviewStatsPoint> reviewStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.reviewStats(monthsAgo(from), toOrToday(to));
    }

    @GetMapping("/analytics/seasonality")
    public List<AnalyticsDto.SeasonalityCell> seasonality() {
        return analytics.seasonality();
    }

    // ------------------------------------------------------------------
    // Qidiruv tahlili (§11.2 — eng qimmatli sahifa)
    // ------------------------------------------------------------------

    @GetMapping("/search-insights/zero-results")
    public List<AnalyticsDto.ZeroResultRoute> zeroResults(@RequestParam(defaultValue = "50") int limit) {
        return analytics.zeroResultRoutes(limit);
    }

    @GetMapping("/search-insights/daily")
    public List<AnalyticsDto.SearchDailyPoint> searchDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.searchDaily(fromOrDefault(from, DEFAULT_DAYS), toOrToday(to));
    }

    @GetMapping("/search-insights/demand-supply")
    public List<AnalyticsDto.RouteDemandSupply> demandSupply(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "25") int limit) {
        return analytics.routeDemandVsSupply(fromOrDefault(from, DEFAULT_DAYS), limit);
    }

    @GetMapping("/notifications/stats")
    public AnalyticsDto.NotificationStats notifications(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.notifications(fromOrDefault(from, DEFAULT_DAYS), toOrToday(to));
    }

    private static LocalDate fromOrDefault(LocalDate from, int days) {
        return from == null ? LocalDate.now().minusDays(days) : from;
    }

    private static LocalDate monthsAgo(LocalDate from) {
        return from == null ? LocalDate.now().minusMonths(DEFAULT_MONTHS).withDayOfMonth(1) : from;
    }

    private static LocalDate toOrToday(LocalDate to) {
        return to == null ? LocalDate.now() : to;
    }
}
