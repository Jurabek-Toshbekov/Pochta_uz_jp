package uz.pochtajp.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.api.admin.dto.AdminDto;

/**
 * Bitta foydalanuvchining to'liq analitikasi (admin → Foydalanuvchilar → profil).
 *
 * <p>"Ko'rish" deganda Mini App'dagi {@code post_detail_view} tushuniladi —
 * u e'lon sahifasi har ochilganda bir marta yoziladi. Kontakt ochish esa
 * {@code contact_reveals} jadvalidan olinadi: ko'rish event'i yo'qolgan
 * bo'lsa ham bitim niyati ko'rinib tursin.
 *
 * <p>Telefon raqami hech qayerda qaytarilmaydi (§1.7). Admin kimning kimni
 * ko'rganini ochgani — maxfiy ma'lumotga kirish, shuning uchun audit'ga yoziladi.
 */
@Service
public class AdminUserAnalyticsService {

    private static final String VIEW_EVENT = EventName.POST_DETAIL_VIEW;
    private static final int ACTIVITY_DAYS = 90;
    private static final int LIST_LIMIT = 100;
    private static final int OWN_POSTS_LIMIT = 50;
    private static final int SEARCH_LIMIT = 50;
    private static final int TIMELINE_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final AdminUserService userService;
    private final AuditService auditService;

    public AdminUserAnalyticsService(JdbcTemplate jdbc,
                                     AdminUserService userService,
                                     AuditService auditService) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.auditService = auditService;
    }

    /**
     * Tranzaksiyasiz: faqat o'qiladi, audit esa o'z tranzaksiyasida yoziladi
     * (read-only tranzaksiya ichida INSERT rad etiladi).
     */
    public AdminDto.UserAnalytics analytics(UUID userId, UUID actorId) {
        AdminDto.UserRow row = userService.row(userId);
        auditService.record(actorId, "USER_ANALYTICS_VIEW", "USER", userId.toString());

        String blockedReason = jdbc.queryForObject(
                "SELECT blocked_reason FROM users WHERE id = ?", String.class, userId);

        return new AdminDto.UserAnalytics(
                row,
                blockedReason,
                origin(userId),
                totals(userId),
                activity(userId),
                eventBreakdown(userId),
                ownPosts(userId),
                viewedPosts(userId),
                viewers(userId),
                searches(userId),
                userService.recentEvents(userId, TIMELINE_LIMIT));
    }

    private AdminDto.UserOrigin origin(UUID userId) {
        List<String> platforms = jdbc.queryForList("""
                SELECT platform FROM events
                WHERE user_id = ? AND platform IS NOT NULL
                GROUP BY platform ORDER BY count(*) DESC
                """, String.class, userId);

        return jdbc.queryForObject("""
                SELECT first_seen_at, last_seen_at, referral_source, language_code, ui_language,
                       is_telegram_premium, phone_verified_at, consent_tos_at, consent_privacy_at
                FROM users WHERE id = ?
                """,
                (rs, i) -> new AdminDto.UserOrigin(
                        instant(rs.getTimestamp("first_seen_at")),
                        instant(rs.getTimestamp("last_seen_at")),
                        rs.getString("referral_source"),
                        rs.getString("language_code"),
                        rs.getString("ui_language"),
                        rs.getBoolean("is_telegram_premium"),
                        rs.getTimestamp("phone_verified_at") != null,
                        instant(rs.getTimestamp("consent_tos_at")),
                        instant(rs.getTimestamp("consent_privacy_at")),
                        platforms,
                        userService.firstEvent(userId).orElse(null)),
                userId);
    }

    private AdminDto.UserTotals totals(UUID userId) {
        Map<String, Object> ev = jdbc.queryForMap("""
                SELECT count(*)                                         AS events,
                       count(DISTINCT session_id)                       AS sessions,
                       count(DISTINCT date_trunc('day', occurred_at))   AS active_days
                FROM events WHERE user_id = ?
                """, userId);
        Map<String, Object> posts = jdbc.queryForMap("""
                SELECT count(*)                                              AS created,
                       count(*) FILTER (WHERE published_at IS NOT NULL)      AS published
                FROM posts WHERE user_id = ? AND deleted_at IS NULL
                """, userId);
        Map<String, Object> search = jdbc.queryForMap("""
                SELECT count(*)                                  AS total,
                       count(*) FILTER (WHERE result_count = 0)  AS zero
                FROM search_queries WHERE user_id = ?
                """, userId);
        Map<String, Object> received = jdbc.queryForMap("""
                SELECT count(*)                  AS views,
                       count(DISTINCT e.user_id) AS viewers
                FROM events e JOIN posts p ON p.id = e.post_id
                WHERE p.user_id = ? AND e.event_name = ?
                  AND e.user_id IS NOT NULL AND e.user_id <> p.user_id
                """, userId, VIEW_EVENT);
        Map<String, Object> reviews = jdbc.queryForMap("""
                SELECT count(*) AS total, round(avg(rating), 2) AS avg_rating
                FROM reviews WHERE subject_id = ? AND deleted_at IS NULL
                """, userId);

        return new AdminDto.UserTotals(
                number(ev.get("events")),
                number(ev.get("sessions")),
                number(ev.get("active_days")),
                number(posts.get("created")),
                number(posts.get("published")),
                number(search.get("total")),
                number(search.get("zero")),
                count("""
                        SELECT count(DISTINCT e.post_id) FROM events e JOIN posts p ON p.id = e.post_id
                        WHERE e.user_id = ? AND e.event_name = ? AND p.user_id <> e.user_id
                        """, userId, VIEW_EVENT),
                number(received.get("views")),
                number(received.get("viewers")),
                count("SELECT count(*) FROM contact_reveals WHERE viewer_id = ?", userId),
                count("SELECT count(*) FROM contact_reveals WHERE owner_id = ?", userId),
                count("SELECT count(*) FROM reports WHERE reporter_id = ?", userId),
                count("""
                        SELECT count(*) FROM reports
                        WHERE reported_user_id = ?
                           OR post_id IN (SELECT id FROM posts WHERE user_id = ?)
                        """, userId, userId),
                number(reviews.get("total")),
                (BigDecimal) reviews.get("avg_rating"),
                count("""
                        SELECT count(*) FROM notification_subscriptions
                        WHERE user_id = ? AND is_active AND deleted_at IS NULL
                        """, userId));
    }

    private List<AdminDto.DailyCount> activity(UUID userId) {
        return jdbc.query("""
                SELECT date_trunc('day', occurred_at)::date AS day, count(*) AS total
                FROM events
                WHERE user_id = ? AND occurred_at >= now() - make_interval(days => ?)
                GROUP BY 1 ORDER BY 1
                """,
                (rs, i) -> new AdminDto.DailyCount(rs.getObject("day", LocalDate.class), rs.getLong("total")),
                userId, ACTIVITY_DAYS);
    }

    private List<AdminDto.EventCount> eventBreakdown(UUID userId) {
        return jdbc.query("""
                SELECT event_name, count(*) AS total, max(occurred_at) AS last_at
                FROM events WHERE user_id = ?
                GROUP BY event_name ORDER BY total DESC
                """,
                (rs, i) -> new AdminDto.EventCount(
                        rs.getString("event_name"), rs.getLong("total"), instant(rs.getTimestamp("last_at"))),
                userId);
    }

    private List<AdminDto.OwnPost> ownPosts(UUID userId) {
        return jdbc.query("""
                SELECT p.id, p.created_at, p.status, p.post_type, p.origin_airport, p.dest_airport,
                       coalesce(p.depart_date, p.deadline_date) AS travel_date,
                       p.view_count, p.contact_reveal_count,
                       (SELECT count(DISTINCT e.user_id) FROM events e
                        WHERE e.post_id = p.id AND e.event_name = ?
                          AND e.user_id IS NOT NULL AND e.user_id <> p.user_id) AS unique_viewers
                FROM posts p
                WHERE p.user_id = ?
                ORDER BY p.created_at DESC LIMIT ?
                """,
                (rs, i) -> new AdminDto.OwnPost(
                        rs.getObject("id", UUID.class),
                        instant(rs.getTimestamp("created_at")),
                        rs.getString("status"),
                        rs.getString("post_type"),
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getObject("travel_date", LocalDate.class),
                        rs.getInt("view_count"),
                        rs.getLong("unique_viewers"),
                        rs.getInt("contact_reveal_count")),
                VIEW_EVENT, userId, OWN_POSTS_LIMIT);
    }

    private List<AdminDto.ViewedPost> viewedPosts(UUID userId) {
        return jdbc.query("""
                WITH touch AS (
                    SELECT e.post_id, e.occurred_at, 0 AS reveal
                    FROM events e
                    WHERE e.user_id = ? AND e.event_name = ? AND e.post_id IS NOT NULL
                    UNION ALL
                    SELECT cr.post_id, cr.created_at, 1
                    FROM contact_reveals cr WHERE cr.viewer_id = ?
                )
                SELECT p.id, p.post_type, p.origin_airport, p.dest_airport, p.status,
                       o.id AS owner_id, o.username, o.first_name, o.last_name,
                       count(*) FILTER (WHERE t.reveal = 0) AS views,
                       bool_or(t.reveal = 1)                AS revealed,
                       max(t.occurred_at)                   AS last_at
                FROM touch t
                JOIN posts p ON p.id = t.post_id
                JOIN users o ON o.id = p.user_id
                WHERE p.user_id <> ?
                GROUP BY p.id, o.id
                ORDER BY last_at DESC LIMIT ?
                """,
                (rs, i) -> new AdminDto.ViewedPost(
                        rs.getObject("id", UUID.class),
                        rs.getString("post_type"),
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getString("status"),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("username"),
                        AdminPostService.displayName(rs.getString("first_name"), rs.getString("last_name")),
                        rs.getLong("views"),
                        rs.getBoolean("revealed"),
                        instant(rs.getTimestamp("last_at"))),
                userId, VIEW_EVENT, userId, userId, LIST_LIMIT);
    }

    private List<AdminDto.Viewer> viewers(UUID userId) {
        return jdbc.query("""
                WITH touch AS (
                    SELECT e.user_id AS viewer_id, e.post_id, e.occurred_at, 0 AS reveal
                    FROM events e JOIN posts p ON p.id = e.post_id
                    WHERE p.user_id = ? AND e.event_name = ? AND e.user_id IS NOT NULL
                    UNION ALL
                    SELECT cr.viewer_id, cr.post_id, cr.created_at, 1
                    FROM contact_reveals cr WHERE cr.owner_id = ?
                )
                SELECT v.id, v.username, v.first_name, v.last_name,
                       count(DISTINCT t.post_id)            AS posts_viewed,
                       count(*) FILTER (WHERE t.reveal = 0) AS views,
                       count(*) FILTER (WHERE t.reveal = 1) AS reveals,
                       max(t.occurred_at)                   AS last_at
                FROM touch t
                JOIN users v ON v.id = t.viewer_id
                WHERE v.id <> ?
                GROUP BY v.id
                ORDER BY last_at DESC LIMIT ?
                """,
                (rs, i) -> new AdminDto.Viewer(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        AdminPostService.displayName(rs.getString("first_name"), rs.getString("last_name")),
                        rs.getLong("posts_viewed"),
                        rs.getLong("views"),
                        rs.getLong("reveals"),
                        instant(rs.getTimestamp("last_at"))),
                userId, VIEW_EVENT, userId, userId, LIST_LIMIT);
    }

    private List<AdminDto.SearchRow> searches(UUID userId) {
        return jdbc.query("""
                SELECT created_at, post_type, direction, origin_airport, dest_airport,
                       date_from, date_to, text_query, result_count
                FROM search_queries WHERE user_id = ?
                ORDER BY created_at DESC LIMIT ?
                """,
                (rs, i) -> new AdminDto.SearchRow(
                        instant(rs.getTimestamp("created_at")),
                        rs.getString("post_type"),
                        rs.getString("direction"),
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getObject("date_from", LocalDate.class),
                        rs.getObject("date_to", LocalDate.class),
                        rs.getString("text_query"),
                        rs.getInt("result_count")),
                userId, SEARCH_LIMIT);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
