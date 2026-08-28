package uz.pochtajp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.api.admin.dto.AnalyticsDto;

/**
 * Analitika so'rovlari (§6.3, §11.2).
 *
 * <p>Bu yagona joy: butun loyihada analitika SQL'i faqat shu klassda va
 * `V5__admin_and_metrics.sql` dagi view'larda bo'ladi. Controller'da SQL yo'q.
 *
 * <p>Nima uchun JPA emas, {@link JdbcTemplate}: bular hisobot so'rovlari —
 * agregat, percentile va pivot. Ularni entity grafiga tiqishtirish faqat
 * zarar keltiradi.
 *
 * <p>Barcha metodlar faqat o'qiydi. Sana oralig'i majburiy: cheklovsiz
 * so'rov butun jadvalni skanerlaydi.
 */
@Service
public class AdminAnalyticsService {

    private static final int MAX_ZERO_RESULT_ROWS = 50;

    private final JdbcTemplate jdbc;

    public AdminAnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Umumiy ko'rinish
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AnalyticsDto.Overview overview() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        long postsToday = countPosts(today);
        long postsYesterday = countPosts(yesterday);
        long publishedToday = queryLong("""
                SELECT count(*) FROM posts
                WHERE deleted_at IS NULL AND published_at::date = ?
                """, today);

        long dauToday = queryLong("SELECT coalesce(dau, 0) FROM v_metrics_active_users WHERE metric_date = ?",
                today);
        long dauYesterday = queryLong("SELECT coalesce(dau, 0) FROM v_metrics_active_users WHERE metric_date = ?",
                yesterday);

        // Publish konversiyasi: forma ochganlarning qanchasi e'lon chiqargan (oxirgi 30 kun).
        LocalDate from = today.minusDays(30);
        long formOpens = queryLong("""
                SELECT coalesce(sum(users_count), 0) FROM v_metrics_funnel_daily
                WHERE step_key = 'form_open' AND metric_date >= ?
                """, from);
        long published = queryLong("""
                SELECT coalesce(sum(users_count), 0) FROM v_metrics_funnel_daily
                WHERE step_key = 'published' AND metric_date >= ?
                """, from);

        BigDecimal fillRate = queryDecimal("""
                SELECT round(
                    count(*) FILTER (WHERE r.post_id IS NOT NULL)::numeric / nullif(count(*), 0), 4)
                FROM posts p
                LEFT JOIN (SELECT DISTINCT post_id FROM contact_reveals) r ON r.post_id = p.id
                WHERE p.deleted_at IS NULL AND p.published_at >= ?
                """, from);

        long openReports = queryLong("SELECT count(*) FROM reports WHERE status IN ('OPEN','REVIEWING')");
        long pendingPosts = queryLong("""
                SELECT count(*) FROM posts WHERE status = 'PENDING' AND deleted_at IS NULL
                """);

        BigDecimal zeroResultRate = queryDecimal("""
                SELECT round(
                    coalesce(sum(zero_result_count), 0)::numeric / nullif(sum(search_count), 0), 4)
                FROM v_metrics_search_daily WHERE metric_date >= ?
                """, from);

        BigDecimal medianTimeToPublish = queryDecimal("""
                SELECT round(avg(median_seconds)::numeric, 1)
                FROM v_metrics_time_to_publish WHERE metric_date >= ?
                """, from);

        return new AnalyticsDto.Overview(
                postsToday, postsYesterday, publishedToday,
                dauToday, dauYesterday,
                ratio(published, formOpens),
                orZero(fillRate),
                openReports, pendingPosts,
                orZero(zeroResultRate),
                orZero(medianTimeToPublish));
    }

    private long countPosts(LocalDate day) {
        return queryLong("SELECT count(*) FROM posts WHERE deleted_at IS NULL AND created_at::date = ?", day);
    }

    // ------------------------------------------------------------------
    // Grafiklar
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AnalyticsDto.PostDailyPoint> postsDaily(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_date, post_type, direction, created_count, published_count
                FROM v_metrics_post_daily
                WHERE metric_date BETWEEN ? AND ?
                ORDER BY metric_date, post_type, direction
                """,
                (rs, i) -> new AnalyticsDto.PostDailyPoint(
                        rs.getObject("metric_date", LocalDate.class),
                        rs.getString("post_type"),
                        rs.getString("direction"),
                        rs.getLong("created_count"),
                        rs.getLong("published_count")),
                from, to);
    }

    /**
     * Voronka (§6.3). Qadamlar tartibi view'da qat'iy belgilangan, shuning
     * uchun bu yerda faqat konversiya hisoblanadi.
     */
    @Transactional(readOnly = true)
    public List<AnalyticsDto.FunnelStep> funnel(LocalDate from, LocalDate to) {
        record Raw(String key, int index, long users) {
        }
        List<Raw> raw = jdbc.query("""
                SELECT step_key, step_index, sum(users_count) AS users_count
                FROM v_metrics_funnel_daily
                WHERE metric_date BETWEEN ? AND ?
                GROUP BY step_key, step_index
                ORDER BY step_index
                """,
                (rs, i) -> new Raw(rs.getString("step_key"), rs.getInt("step_index"),
                        rs.getLong("users_count")),
                from, to);

        List<AnalyticsDto.FunnelStep> steps = new ArrayList<>(raw.size());
        long start = raw.isEmpty() ? 0 : raw.get(0).users();
        long previous = start;
        for (Raw row : raw) {
            steps.add(new AnalyticsDto.FunnelStep(row.key(), row.index(), row.users(),
                    ratio(row.users(), previous), ratio(row.users(), start)));
            previous = row.users();
        }
        return steps;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.AbandonRow> abandonByStep(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT last_step, sum(abandon_count) AS abandon_count
                FROM v_metrics_abandon_daily
                WHERE metric_date BETWEEN ? AND ?
                GROUP BY last_step
                ORDER BY abandon_count DESC
                """,
                (rs, i) -> new AnalyticsDto.AbandonRow(rs.getString("last_step"),
                        rs.getLong("abandon_count")),
                from, to);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.CohortRow> cohorts(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT cohort_date, cohort_size, d1, d7, d30
                FROM v_metrics_cohort_retention
                WHERE cohort_date BETWEEN ? AND ?
                ORDER BY cohort_date DESC
                """,
                (rs, i) -> {
                    long size = rs.getLong("cohort_size");
                    long d1 = rs.getLong("d1");
                    long d7 = rs.getLong("d7");
                    long d30 = rs.getLong("d30");
                    return new AnalyticsDto.CohortRow(
                            rs.getObject("cohort_date", LocalDate.class), size, d1, d7, d30,
                            ratio(d1, size), ratio(d7, size), ratio(d30, size));
                },
                from, to);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.PriceIndexPoint> priceIndex(LocalDate from, LocalDate to, String direction) {
        StringBuilder sql = new StringBuilder("""
                SELECT metric_month, direction, origin_airport, dest_airport,
                       price_currency, sample_size, round(median_per_kg::numeric, 2) AS median_per_kg
                FROM v_metrics_price_index
                WHERE metric_month BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>(List.of(from, to));
        if (direction != null && !direction.isBlank()) {
            sql.append(" AND direction = ?");
            args.add(direction);
        }
        sql.append(" ORDER BY metric_month, origin_airport, dest_airport");

        return jdbc.query(sql.toString(),
                (rs, i) -> new AnalyticsDto.PriceIndexPoint(
                        rs.getObject("metric_month", LocalDate.class),
                        rs.getString("direction"),
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getString("price_currency"),
                        rs.getLong("sample_size"),
                        rs.getBigDecimal("median_per_kg")),
                args.toArray());
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.SupplyDemandCell> supplyDemand(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_week, direction, carry_count, send_count, supply_demand_ratio
                FROM v_metrics_supply_demand
                WHERE metric_week BETWEEN ? AND ?
                ORDER BY metric_week, direction
                """,
                (rs, i) -> new AnalyticsDto.SupplyDemandCell(
                        rs.getObject("metric_week", LocalDate.class),
                        rs.getString("direction"),
                        rs.getLong("carry_count"),
                        rs.getLong("send_count"),
                        rs.getBigDecimal("supply_demand_ratio")),
                from, to);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.MatchLatencyPoint> matchLatency(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_month, direction, sample_size, round(median_minutes::numeric, 1) AS median_minutes
                FROM v_metrics_match_latency
                WHERE metric_month BETWEEN ? AND ?
                ORDER BY metric_month, direction
                """,
                (rs, i) -> new AnalyticsDto.MatchLatencyPoint(
                        rs.getObject("metric_month", LocalDate.class),
                        rs.getString("direction"),
                        rs.getLong("sample_size"),
                        rs.getBigDecimal("median_minutes")),
                from, to);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.SeasonalityCell> seasonality() {
        return jdbc.query("""
                SELECT day_of_week, month_of_year, post_type, post_count
                FROM v_metrics_seasonality
                ORDER BY month_of_year, day_of_week
                """,
                (rs, i) -> new AnalyticsDto.SeasonalityCell(
                        rs.getInt("day_of_week"),
                        rs.getInt("month_of_year"),
                        rs.getString("post_type"),
                        rs.getLong("post_count")));
    }

    /**
     * Bitim tasdiqlash ulushi (§6.4, 1-band).
     *
     * <p>Fill rate'dan farqi: fill rate kontakt ochilganini sanaydi (niyat),
     * bu esa "odam topildi" javobini (natija). Ikkisi orasidagi farq
     * mahsulot qayerda uzilib qolayotganini ko'rsatadi.
     */
    @Transactional(readOnly = true)
    public List<AnalyticsDto.DealConfirmationPoint> dealConfirmation(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_month, direction, published_count, confirmed_count, confirmation_rate
                FROM v_metrics_deal_confirmation
                WHERE metric_month BETWEEN ? AND ?
                ORDER BY metric_month, direction
                """,
                (rs, i) -> new AnalyticsDto.DealConfirmationPoint(
                        rs.getObject("metric_month", LocalDate.class),
                        rs.getString("direction"),
                        rs.getLong("published_count"),
                        rs.getLong("confirmed_count"),
                        rs.getBigDecimal("confirmation_rate")),
                from, to);
    }

    /** Yopish sabablari (§6.4, 3-band): "javob bo'lmadi" ko'p bo'lsa — mahsulot muammosi. */
    @Transactional(readOnly = true)
    public List<AnalyticsDto.CloseReasonRow> closeReasons(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT closed_reason, sum(post_count) AS post_count
                FROM v_metrics_close_reasons
                WHERE metric_month BETWEEN ? AND ?
                GROUP BY closed_reason
                ORDER BY post_count DESC
                """,
                (rs, i) -> new AnalyticsDto.CloseReasonRow(
                        rs.getString("closed_reason"),
                        rs.getLong("post_count")),
                from, to);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.ReviewStatsPoint> reviewStats(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_month, review_count, avg_rating, negative_count
                FROM v_metrics_reviews
                WHERE metric_month BETWEEN ? AND ?
                ORDER BY metric_month
                """,
                (rs, i) -> new AnalyticsDto.ReviewStatsPoint(
                        rs.getObject("metric_month", LocalDate.class),
                        rs.getLong("review_count"),
                        rs.getBigDecimal("avg_rating"),
                        rs.getLong("negative_count")),
                from, to);
    }

    // ------------------------------------------------------------------
    // Qidiruv tahlili (§11.2 — eng qimmatli sahifa)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AnalyticsDto.ZeroResultRoute> zeroResultRoutes(int limit) {
        int safeLimit = limit <= 0 || limit > MAX_ZERO_RESULT_ROWS ? MAX_ZERO_RESULT_ROWS : limit;
        return jdbc.query("""
                SELECT origin_airport, dest_airport, direction, post_type, search_count
                FROM v_metrics_zero_result_routes
                ORDER BY search_count DESC
                LIMIT ?
                """,
                (rs, i) -> new AnalyticsDto.ZeroResultRoute(
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getString("direction"),
                        rs.getString("post_type"),
                        rs.getLong("search_count")),
                safeLimit);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDto.SearchDailyPoint> searchDaily(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_date, search_count, zero_result_count, clicked_count, avg_latency_ms
                FROM v_metrics_search_daily
                WHERE metric_date BETWEEN ? AND ?
                ORDER BY metric_date
                """,
                (rs, i) -> new AnalyticsDto.SearchDailyPoint(
                        rs.getObject("metric_date", LocalDate.class),
                        rs.getLong("search_count"),
                        rs.getLong("zero_result_count"),
                        rs.getLong("clicked_count"),
                        rs.getBigDecimal("avg_latency_ms")),
                from, to);
    }

    /**
     * Talab va taklifni yonma-yon qo'yadi: qaysi yo'nalish ko'p qidiriladi
     * va o'sha yo'nalishda nechta e'lon bor (§11.2).
     */
    @Transactional(readOnly = true)
    public List<AnalyticsDto.RouteDemandSupply> routeDemandVsSupply(LocalDate from, int limit) {
        int safeLimit = limit <= 0 || limit > MAX_ZERO_RESULT_ROWS ? MAX_ZERO_RESULT_ROWS : limit;
        return jdbc.query("""
                WITH demand AS (
                    SELECT origin_airport, dest_airport, count(*) AS search_count
                    FROM search_queries
                    WHERE created_at >= ? AND origin_airport IS NOT NULL AND dest_airport IS NOT NULL
                    GROUP BY 1, 2
                ),
                supply AS (
                    SELECT origin_airport, dest_airport, count(*) AS post_count
                    FROM posts
                    WHERE deleted_at IS NULL AND published_at >= ?
                    GROUP BY 1, 2
                )
                SELECT coalesce(d.origin_airport, s.origin_airport) AS origin_airport,
                       coalesce(d.dest_airport, s.dest_airport)     AS dest_airport,
                       coalesce(d.search_count, 0)                  AS search_count,
                       coalesce(s.post_count, 0)                    AS post_count
                FROM demand d
                FULL OUTER JOIN supply s
                  ON s.origin_airport = d.origin_airport AND s.dest_airport = d.dest_airport
                ORDER BY search_count DESC, post_count DESC
                LIMIT ?
                """,
                (rs, i) -> new AnalyticsDto.RouteDemandSupply(
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getLong("search_count"),
                        rs.getLong("post_count")),
                from, from, safeLimit);
    }

    // ------------------------------------------------------------------
    // Xabarnomalar
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AnalyticsDto.NotificationStats notifications(LocalDate from, LocalDate to) {
        List<AnalyticsDto.NotificationDailyPoint> daily = jdbc.query("""
                SELECT metric_date, sent_count, opened_count, failed_count
                FROM v_metrics_notifications
                WHERE metric_date BETWEEN ? AND ?
                ORDER BY metric_date
                """,
                (rs, i) -> new AnalyticsDto.NotificationDailyPoint(
                        rs.getObject("metric_date", LocalDate.class),
                        rs.getLong("sent_count"),
                        rs.getLong("opened_count"),
                        rs.getLong("failed_count")),
                from, to);

        long sent = daily.stream().mapToLong(AnalyticsDto.NotificationDailyPoint::sentCount).sum();
        long opened = daily.stream().mapToLong(AnalyticsDto.NotificationDailyPoint::openedCount).sum();
        long failed = daily.stream().mapToLong(AnalyticsDto.NotificationDailyPoint::failedCount).sum();
        long subscriptions = queryLong("""
                SELECT count(*) FROM notification_subscriptions
                WHERE is_active = TRUE AND deleted_at IS NULL
                """);

        return new AnalyticsDto.NotificationStats(sent, opened, failed, ratio(opened, sent),
                subscriptions, daily);
    }

    // ------------------------------------------------------------------
    // Yordamchilar
    // ------------------------------------------------------------------

    private long queryLong(String sql, Object... args) {
        List<Long> rows = jdbc.query(sql, singleLong(), args);
        return rows.isEmpty() || rows.get(0) == null ? 0L : rows.get(0);
    }

    /**
     * Agregat so'rov qator qaytarib, ichida {@code NULL} bo'lishi mumkin
     * (masalan maxraj nol bo'lganda). Shuning uchun {@code findFirst()}
     * ishlatilmaydi — u {@code null} elementda NPE beradi.
     */
    private BigDecimal queryDecimal(String sql, Object... args) {
        List<BigDecimal> rows = jdbc.query(sql,
                (RowMapper<BigDecimal>) (rs, i) -> rs.getBigDecimal(1), args);
        return rows.isEmpty() || rows.get(0) == null ? BigDecimal.ZERO : rows.get(0);
    }

    private static RowMapper<Long> singleLong() {
        return (ResultSet rs, int i) -> {
            long value = rs.getLong(1);
            return rs.wasNull() ? 0L : value;
        };
    }

    /** Nolga bo'lish yo'q: maxraj nol bo'lsa natija ham nol. */
    static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
