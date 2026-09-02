package uz.pochtajp.service;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kunlik agregatlarni yig'adi (§6.2 — retention, §13 4-bosqich).
 *
 * <p>Nima uchun kerak: raw event'lar 24 oydan keyin tozalanishi mumkin,
 * lekin "2026-yil avgustda nechta e'lon bo'lgan" degan savol keyin ham
 * javob talab qiladi. Shuning uchun har kecha o'tgan kunning raqamlari
 * {@code daily_metrics}ga ko'chiriladi va abadiy qoladi.
 *
 * <p>Job idempotent: bir kunni necha marta hisoblasa ham natija bir xil
 * ({@code ON CONFLICT DO UPDATE}). Shu sababli qayta ishga tushirish
 * xavfsiz va orqaga qarab to'ldirish ham shu metod bilan qilinadi.
 */
@Service
public class DailyMetricsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyMetricsJob.class);

    /** Har kecha 03:15 UTC — kun allaqachon yopilgan, yuk esa past. */
    private static final String SCHEDULE = "0 15 3 * * *";

    private static final String UPSERT = """
            INSERT INTO daily_metrics (metric_date, metric_key, dimension, value, computed_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (metric_date, metric_key, dimension)
            DO UPDATE SET value = EXCLUDED.value, computed_at = now()
            """;

    private final JdbcTemplate jdbc;

    public DailyMetricsJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = SCHEDULE, zone = "UTC")
    public void runForYesterday() {
        compute(LocalDate.now().minusDays(1));
    }

    /**
     * Bir kunning barcha metrikalarini hisoblab yozadi.
     *
     * <p>Ochiq metod: orqaga qarab to'ldirish va test uchun ham shu ishlatiladi.
     */
    @Transactional
    public void compute(LocalDate day) {
        int rows = 0;
        rows += postsMetrics(day);
        rows += activeUsers(day);
        rows += searchMetrics(day);
        rows += funnelMetrics(day);
        rows += timeToPublish(day);
        log.info("Kunlik agregatlar yozildi: date={} rows={}", day, rows);
    }

    /** E'lonlar: umumiy va tur kesimida. */
    private int postsMetrics(LocalDate day) {
        int rows = upsertFromQuery(day, "posts_created", """
                SELECT '' AS dimension, coalesce(sum(created_count), 0) AS value
                FROM v_metrics_post_daily WHERE metric_date = ?
                """);
        rows += upsertFromQuery(day, "posts_published", """
                SELECT '' AS dimension, coalesce(sum(published_count), 0) AS value
                FROM v_metrics_post_daily WHERE metric_date = ?
                """);
        rows += upsertFromQuery(day, "posts_created_by_type", """
                SELECT post_type AS dimension, sum(created_count) AS value
                FROM v_metrics_post_daily WHERE metric_date = ? GROUP BY post_type
                """);
        rows += upsertFromQuery(day, "posts_created_by_direction", """
                SELECT direction AS dimension, sum(created_count) AS value
                FROM v_metrics_post_daily WHERE metric_date = ? GROUP BY direction
                """);
        return rows;
    }

    private int activeUsers(LocalDate day) {
        return upsertFromQuery(day, "dau", """
                SELECT '' AS dimension, coalesce(dau, 0) AS value
                FROM v_metrics_active_users WHERE metric_date = ?
                """);
    }

    private int searchMetrics(LocalDate day) {
        int rows = upsertFromQuery(day, "searches", """
                SELECT '' AS dimension, coalesce(search_count, 0) AS value
                FROM v_metrics_search_daily WHERE metric_date = ?
                """);
        rows += upsertFromQuery(day, "searches_zero_result", """
                SELECT '' AS dimension, coalesce(zero_result_count, 0) AS value
                FROM v_metrics_search_daily WHERE metric_date = ?
                """);
        return rows;
    }

    /** Voronka: har bir qadam alohida dimension bo'lib saqlanadi. */
    private int funnelMetrics(LocalDate day) {
        return upsertFromQuery(day, "funnel_users", """
                SELECT step_key AS dimension, sum(users_count) AS value
                FROM v_metrics_funnel_daily WHERE metric_date = ? GROUP BY step_key
                """);
    }

    private int timeToPublish(LocalDate day) {
        return upsertFromQuery(day, "time_to_publish_median_seconds", """
                SELECT '' AS dimension, round(median_seconds::numeric, 1) AS value
                FROM v_metrics_time_to_publish WHERE metric_date = ?
                """);
    }

    /**
     * So'rov natijasini {@code daily_metrics}ga ko'chiradi.
     *
     * <p>So'rov ikkita ustun qaytarishi shart: {@code dimension} va {@code value}.
     * Natija bo'lmasa hech narsa yozilmaydi — nol yozib qo'yish
     * "ma'lumot yo'q" bilan "qiymati nol"ni chalkashtirib yuborardi.
     */
    private int upsertFromQuery(LocalDate day, String metricKey, String sql) {
        record Row(String dimension, java.math.BigDecimal value) {
        }
        var rows = jdbc.query(sql,
                (rs, i) -> new Row(rs.getString("dimension"), rs.getBigDecimal("value")), day);

        int written = 0;
        for (Row row : rows) {
            if (row.value() == null) {
                continue;
            }
            jdbc.update(UPSERT, day, metricKey,
                    row.dimension() == null ? "" : row.dimension(), row.value());
            written++;
        }
        return written;
    }
}
