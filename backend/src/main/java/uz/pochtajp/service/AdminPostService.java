package uz.pochtajp.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.api.admin.dto.AdminRequests;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.ModerationActionLog;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.enums.ClosedReason;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.ModerationAction;
import uz.pochtajp.domain.enums.ModerationTarget;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.repository.ModerationActionLogRepository;
import uz.pochtajp.repository.PostRepository;

/**
 * E'lonlar moderatsiyasi (§11.2 — /posts).
 *
 * <p>Har bir harakat ikki joyga yoziladi: {@code moderation_actions}
 * (oldingi va keyingi holat bilan) va {@code audit_log}. Hech narsa
 * o'chirilmaydi (§1.1) — rad etilgan e'lon ham bazada qoladi.
 *
 * <p>Ro'yxat so'rovi {@link JdbcTemplate} bilan: filtr, sanoq va bir nechta
 * jadvalga qo'shilish JPA Criteria'da o'qib bo'lmas holga keladi.
 */
@Service
public class AdminPostService {

    private static final Logger log = LoggerFactory.getLogger(AdminPostService.class);

    private static final int MAX_PAGE_SIZE = 100;

    /** Qidiruv matnidagi e'lon ID'si: yalang'och, `startapp=ch_<id>` yoki butun kanal posti ichida. */
    private static final Pattern POST_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Kanal postining havolasi: t.me/jpuzbpochta/123 yoki t.me/c/1424117981/123. */
    private static final Pattern CHANNEL_LINK = Pattern.compile("t\\.me/(?:c/\\d+|[A-Za-z0-9_]+)/(\\d+)");

    private final JdbcTemplate jdbc;
    private final PostRepository postRepository;
    private final ModerationActionLogRepository moderationLogRepository;
    private final PublishService publishService;
    private final AuditService auditService;
    private final EventLogger eventLogger;

    public AdminPostService(JdbcTemplate jdbc,
                            PostRepository postRepository,
                            ModerationActionLogRepository moderationLogRepository,
                            PublishService publishService,
                            AuditService auditService,
                            EventLogger eventLogger) {
        this.jdbc = jdbc;
        this.postRepository = postRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.publishService = publishService;
        this.auditService = auditService;
        this.eventLogger = eventLogger;
    }

    /** Ro'yxatni saralash. Ruxsat etilgan qiymatlar shu yerda — SQL'ga faqat shular tushadi. */
    public enum Sort {
        /** PENDING va HIGH risk tepada (§11.2), keyin yangilari. */
        PRIORITY(" ORDER BY (p.status = 'PENDING') DESC, (coalesce(cat.max_risk, 'LOW') = 'HIGH') DESC,"
                + " p.created_at DESC"),
        NEWEST(" ORDER BY p.created_at DESC"),
        OLDEST(" ORDER BY p.created_at ASC"),
        VIEWS(" ORDER BY p.view_count DESC, p.created_at DESC"),
        REVEALS(" ORDER BY p.contact_reveal_count DESC, p.created_at DESC"),
        REPORTS(" ORDER BY coalesce(rep.report_count, 0) DESC, p.created_at DESC"),
        TRAVEL_DATE(" ORDER BY coalesce(p.depart_date, p.deadline_date) ASC NULLS LAST, p.created_at DESC");

        private final String sql;

        Sort(String sql) {
            this.sql = sql;
        }

        static Sort parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return PRIORITY;
            }
            try {
                return valueOf(raw.strip().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Saralash turi noto'g'ri.", Map.of("sort", raw));
            }
        }
    }

    /** Ro'yxat filtri. Bo'sh maydon — cheklov yo'q. */
    public record Filter(String status, String postType, String direction, String search,
                         String originAirport, String destAirport, Short categoryId, String riskLevel,
                         boolean reportedOnly, UUID userId, LocalDate createdFrom, LocalDate createdTo,
                         Sort sort, int page, int size) {

        public Filter {
            page = Math.max(0, page);
            size = size <= 0 || size > MAX_PAGE_SIZE ? 25 : size;
            sort = sort == null ? Sort.PRIORITY : sort;
        }

        public static Sort sortOf(String raw, boolean priorityFirst) {
            return raw == null || raw.isBlank()
                    ? (priorityFirst ? Sort.PRIORITY : Sort.NEWEST)
                    : Sort.parse(raw);
        }
    }

    // ------------------------------------------------------------------
    // O'qish
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminDto.Page<AdminDto.PostRow> list(Filter filter) {
        // Aniq e'lon ko'rsatilgan bo'lsa (ID, deep link, kanal havolasi) — holat va
        // tur filtrlari e'tiborga olinmaydi: admin aynan shu e'lonni qidiryapti.
        String search = filter.search() == null ? "" : filter.search().strip();
        Matcher idMatch = POST_ID.matcher(search);
        Matcher linkMatch = CHANNEL_LINK.matcher(search);
        if (idMatch.find()) {
            return page(new StringBuilder(" WHERE p.id = ?"),
                    List.of(UUID.fromString(idMatch.group())), filter);
        }
        if (linkMatch.find()) {
            return page(new StringBuilder(" WHERE p.channel_message_id = ?"),
                    List.of(Long.parseLong(linkMatch.group(1))), filter);
        }

        StringBuilder where = new StringBuilder(" WHERE p.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        equal(where, args, "p.status", filter.status());
        equal(where, args, "p.post_type", filter.postType());
        equal(where, args, "p.direction", filter.direction());
        equal(where, args, "p.origin_airport", upper(filter.originAirport()));
        equal(where, args, "p.dest_airport", upper(filter.destAirport()));
        equal(where, args, "coalesce(cat.max_risk, 'LOW')", upper(filter.riskLevel()));
        if (filter.categoryId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM post_categories pc"
                    + " WHERE pc.post_id = p.id AND pc.category_id = ?)");
            args.add(filter.categoryId());
        }
        if (filter.reportedOnly()) {
            where.append(" AND coalesce(rep.report_count, 0) > 0");
        }
        if (filter.userId() != null) {
            where.append(" AND p.user_id = ?");
            args.add(filter.userId());
        }
        if (filter.createdFrom() != null) {
            where.append(" AND p.created_at >= ?::date");
            args.add(filter.createdFrom());
        }
        if (filter.createdTo() != null) {
            // "gacha" kun ham kiradi.
            where.append(" AND p.created_at < ?::date + 1");
            args.add(filter.createdTo());
        }
        if (!search.isEmpty()) {
            // Foydalanuvchi nomi (username yoki ism) yoki yo'nalish kodi bo'yicha.
            where.append(" AND (u.username ILIKE ? OR concat_ws(' ', u.first_name, u.last_name) ILIKE ?"
                    + " OR p.origin_airport = upper(?) OR p.dest_airport = upper(?))");
            String like = "%" + search.replace("@", "") + "%";
            args.add(like);
            args.add(like);
            args.add(search);
            args.add(search);
        }
        return page(where, args, filter);
    }

    private static void equal(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            args.add(value.strip());
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.strip().toUpperCase();
    }

    private AdminDto.Page<AdminDto.PostRow> page(StringBuilder where, List<Object> args, Filter filter) {
        Long total = jdbc.queryForObject("SELECT count(*)" + LIST_FROM + where, Long.class, args.toArray());

        String order = filter.sort().sql;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(filter.size());
        pageArgs.add(filter.page() * filter.size());

        List<AdminDto.PostRow> items = jdbc.query(
                LIST_SELECT + where + order + " LIMIT ? OFFSET ?", postRowMapper(), pageArgs.toArray());

        return AdminDto.Page.of(items, total == null ? 0 : total, filter.page(), filter.size());
    }

    private static final String LIST_COLUMNS = """
            SELECT p.id, p.created_at, p.published_at, p.status, p.post_type, p.direction,
                   p.origin_airport, p.dest_airport, p.depart_date, p.deadline_date,
                   p.price_amount, p.price_currency, p.price_unit, p.weight_kg,
                   p.view_count, p.contact_reveal_count, p.channel_message_id, p.reject_reason,
                   u.id AS user_id, u.username, u.first_name, u.last_name,
                   coalesce(cat.titles, ARRAY[]::text[])   AS categories,
                   coalesce(cat.max_risk, 'LOW')           AS max_risk,
                   coalesce(rep.report_count, 0)           AS report_count
            """;

    private static final String LIST_FROM = """
            FROM posts p
            JOIN users u ON u.id = p.user_id
            LEFT JOIN (
                SELECT pc.post_id,
                       array_agg(c.title_uz ORDER BY c.sort_order) AS titles,
                       CASE max(CASE c.risk_level WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END)
                            WHEN 3 THEN 'HIGH' WHEN 2 THEN 'MEDIUM' ELSE 'LOW' END AS max_risk
                FROM post_categories pc
                JOIN cargo_categories c ON c.id = pc.category_id
                GROUP BY pc.post_id
            ) cat ON cat.post_id = p.id
            LEFT JOIN (
                SELECT post_id, count(*) AS report_count FROM reports
                WHERE post_id IS NOT NULL GROUP BY post_id
            ) rep ON rep.post_id = p.id
            """;

    private static final String LIST_SELECT = LIST_COLUMNS + LIST_FROM;

    private RowMapper<AdminDto.PostRow> postRowMapper() {
        return (rs, i) -> {
            String[] categories = (String[]) rs.getArray("categories").getArray();
            return new AdminDto.PostRow(
                    rs.getObject("id", UUID.class),
                    instant(rs.getTimestamp("created_at")),
                    instant(rs.getTimestamp("published_at")),
                    rs.getString("status"),
                    rs.getString("post_type"),
                    rs.getString("direction"),
                    rs.getString("origin_airport"),
                    rs.getString("dest_airport"),
                    rs.getObject("depart_date", LocalDate.class),
                    rs.getObject("deadline_date", LocalDate.class),
                    rs.getBigDecimal("price_amount"),
                    rs.getString("price_currency"),
                    rs.getString("price_unit"),
                    rs.getBigDecimal("weight_kg"),
                    rs.getString("max_risk"),
                    List.of(categories),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("username"),
                    displayName(rs.getString("first_name"), rs.getString("last_name")),
                    rs.getInt("view_count"),
                    rs.getInt("contact_reveal_count"),
                    rs.getLong("report_count"),
                    (Long) rs.getObject("channel_message_id"),
                    rs.getString("reject_reason"));
        };
    }

    @Transactional(readOnly = true)
    public AdminDto.PostDetail detail(UUID postId) {
        List<AdminDto.PostRow> rows = jdbc.query(
                LIST_SELECT + " WHERE p.id = ?", postRowMapper(), postId);
        if (rows.isEmpty()) {
            throw new NotFoundException("E'lon topilmadi.");
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));

        return new AdminDto.PostDetail(
                rows.get(0),
                post.getComment(),
                post.getFinalDestination(),
                post.getOriginCityFree(),
                post.getDestCityFree(),
                post.getSafetyChecklistOk(),
                post.getContactTelegram(),
                maskPhone(post.getContactPhone()),
                post.getSource() == null ? null : post.getSource().name());
    }

    // ------------------------------------------------------------------
    // Harakatlar
    // ------------------------------------------------------------------

    /** Tasdiqlash: e'lon kanalga chiqadi (§11.2). */
    @Transactional
    public AdminDto.PostDetail approve(UUID postId, UUID actorId) {
        Post post = require(postId);
        if (post.getStatus() != PostStatus.PENDING) {
            throw new ValidationException("Faqat kutayotgan e'lonni tasdiqlash mumkin.",
                    Map.of("status", "Hozirgi holat: " + post.getStatus()));
        }
        Map<String, Object> before = snapshot(post);

        boolean sent = publishService.publish(postId, null, "ADMIN", 0L);
        // Quyidagi o'qish JdbcTemplate orqali ketadi — Hibernate o'zgarishni
        // commit'gacha yozmaydi, shuning uchun aniq flush qilamiz.
        postRepository.flush();
        recordModeration(actorId, ModerationAction.APPROVE, postId, null, before,
                Map.of("status", sent ? PostStatus.PUBLISHED.name() : PostStatus.PENDING.name()));
        auditService.record(actorId, "POST_APPROVE", "POST", postId.toString(),
                Map.of("published", sent));
        log.info("E'lon tasdiqlandi: post_id={} actor_id={} published={}", postId, actorId, sent);

        return detail(postId);
    }

    /** Rad etish: sabab majburiy, e'lon o'chirilmaydi (§1.1). */
    @Transactional
    public AdminDto.PostDetail reject(UUID postId, String reason, UUID actorId) {
        Post post = require(postId);
        Map<String, Object> before = snapshot(post);

        post.setStatus(PostStatus.REJECTED);
        post.setRejectReason(reason.strip());
        postRepository.saveAndFlush(post);

        recordModeration(actorId, ModerationAction.REJECT, postId, reason, before, snapshot(post));
        auditService.record(actorId, "POST_REJECT", "POST", postId.toString(), Map.of());
        // §6.1 (Xavfsizlik) — moderatsiya qarori ham o'lchanadi.
        eventLogger.track(TrackedEvent.of(EventName.POST_REJECTED, EventSource.ADMIN)
                .user(actorId)
                .post(postId)
                .property("post_type", post.getPostType() == null ? null : post.getPostType().name())
                .build());
        log.info("E'lon rad etildi: post_id={} actor_id={}", postId, actorId);
        return detail(postId);
    }

    /** Matn maydonlarini tahrirlash (§11.2). */
    @Transactional
    public AdminDto.PostDetail update(UUID postId, AdminRequests.UpdatePostRequest request, UUID actorId) {
        Post post = require(postId);
        Map<String, Object> before = snapshot(post);

        if (request.comment() != null) {
            post.setComment(request.comment().strip());
        }
        if (request.finalDestination() != null) {
            post.setFinalDestination(request.finalDestination().strip());
        }
        postRepository.saveAndFlush(post);

        recordModeration(actorId, ModerationAction.EDIT, postId, null, before, snapshot(post));
        auditService.record(actorId, "POST_EDIT", "POST", postId.toString(), Map.of());
        return detail(postId);
    }

    /** Yopish: e'lon qidiruvdan chiqadi, ma'lumot qoladi. */
    @Transactional
    public AdminDto.PostDetail close(UUID postId, UUID actorId) {
        Post post = require(postId);
        Map<String, Object> before = snapshot(post);

        post.setStatus(PostStatus.CLOSED);
        post.setClosedReason(ClosedReason.CANCELLED);
        postRepository.saveAndFlush(post);

        recordModeration(actorId, ModerationAction.EDIT, postId, "Admin yopdi", before, snapshot(post));
        auditService.record(actorId, "POST_CLOSE", "POST", postId.toString(), Map.of());
        return detail(postId);
    }

    private Post require(UUID postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));
    }

    private void recordModeration(UUID actorId, ModerationAction action, UUID postId, String reason,
                                  Map<String, Object> before, Map<String, Object> after) {
        ModerationActionLog entry = new ModerationActionLog();
        entry.setActorId(actorId);
        entry.setTargetType(ModerationTarget.POST);
        entry.setTargetId(postId);
        entry.setAction(action);
        entry.setReason(reason);
        entry.setBeforeJson(before);
        entry.setAfterJson(after);
        moderationLogRepository.save(entry);
    }

    /** Snapshot'da PII bo'lmaydi — faqat holat maydonlari (§1.7). */
    private static Map<String, Object> snapshot(Post post) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", post.getStatus() == null ? null : post.getStatus().name());
        map.put("reject_reason", post.getRejectReason());
        map.put("comment_length", post.getComment() == null ? 0 : post.getComment().length());
        map.put("final_destination", post.getFinalDestination());
        map.put("channel_message_id", post.getChannelMessageId());
        return map;
    }

    static String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.strip();
        String last = lastName == null ? "" : lastName.strip();
        String joined = (first + " " + last).strip();
        return joined.isEmpty() ? null : joined;
    }

    /**
     * Telefon to'liq ko'rsatilmaydi — moderatorga oxirgi to'rt raqam yetadi,
     * shikoyatni tekshirish uchun shuning o'zi kifoya (§1.7).
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
