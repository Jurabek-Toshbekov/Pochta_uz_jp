package uz.pochtajp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.ModerationActionLog;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.ModerationAction;
import uz.pochtajp.domain.enums.ModerationTarget;
import uz.pochtajp.domain.enums.UserStatus;
import uz.pochtajp.domain.enums.VerificationLevel;
import uz.pochtajp.repository.ModerationActionLogRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * Foydalanuvchilar bo'limi (§11.2 — /users).
 *
 * <p>Bloklash va tasdiqlash — moderatsiya harakati, shuning uchun
 * {@code moderation_actions} va {@code audit_log}ga yoziladi.
 *
 * <p>Ro'yxatda telefon raqami umuman ko'rsatilmaydi (§1.7): moderatsiya
 * uchun username va ID yetarli.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int RECENT_EVENTS = 30;
    private static final int RECENT_POSTS = 10;

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final ModerationActionLogRepository moderationLogRepository;
    private final AuditService auditService;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;

    public AdminUserService(JdbcTemplate jdbc,
                            UserRepository userRepository,
                            ModerationActionLogRepository moderationLogRepository,
                            AuditService auditService,
                            EventLogger eventLogger,
                            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.auditService = auditService;
        this.eventLogger = eventLogger;
        this.objectMapper = objectMapper;
    }

    public record Filter(String search, String role, String status, int page, int size) {

        public Filter {
            page = Math.max(0, page);
            size = size <= 0 || size > MAX_PAGE_SIZE ? 25 : size;
        }
    }

    private static final String LIST_SELECT = """
            SELECT u.id, u.telegram_id, u.username, u.first_name, u.last_name, u.role, u.status,
                   u.verification_level, u.trust_score, u.last_seen_at, u.created_at,
                   coalesce(p.post_count, 0)   AS post_count,
                   coalesce(r.report_count, 0) AS report_count
            FROM users u
            LEFT JOIN (
                SELECT user_id, count(*) AS post_count FROM posts
                WHERE deleted_at IS NULL GROUP BY user_id
            ) p ON p.user_id = u.id
            LEFT JOIN (
                SELECT reported_user_id, count(*) AS report_count FROM reports
                WHERE reported_user_id IS NOT NULL GROUP BY reported_user_id
            ) r ON r.reported_user_id = u.id
            """;

    @Transactional(readOnly = true)
    public AdminDto.Page<AdminDto.UserRow> list(Filter filter) {
        StringBuilder where = new StringBuilder(" WHERE u.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();

        if (filter.search() != null && !filter.search().isBlank()) {
            where.append(" AND (u.username ILIKE ? OR u.telegram_id::text = ?)");
            args.add("%" + filter.search().strip() + "%");
            args.add(filter.search().strip());
        }
        if (filter.role() != null && !filter.role().isBlank()) {
            where.append(" AND u.role = ?");
            args.add(filter.role());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND u.status = ?");
            args.add(filter.status());
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM users u" + where,
                Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(filter.size());
        pageArgs.add(filter.page() * filter.size());

        List<AdminDto.UserRow> items = jdbc.query(
                LIST_SELECT + where + " ORDER BY u.last_seen_at DESC LIMIT ? OFFSET ?",
                userRowMapper(), pageArgs.toArray());

        return AdminDto.Page.of(items, total == null ? 0 : total, filter.page(), filter.size());
    }

    private RowMapper<AdminDto.UserRow> userRowMapper() {
        return (rs, i) -> new AdminDto.UserRow(
                rs.getObject("id", UUID.class),
                rs.getLong("telegram_id"),
                rs.getString("username"),
                AdminPostService.displayName(rs.getString("first_name"), rs.getString("last_name")),
                rs.getString("role"),
                rs.getString("status"),
                rs.getString("verification_level"),
                rs.getInt("trust_score"),
                rs.getLong("post_count"),
                rs.getLong("report_count"),
                instant(rs.getTimestamp("last_seen_at")),
                instant(rs.getTimestamp("created_at")));
    }

    @Transactional(readOnly = true)
    public AdminDto.UserDetail detail(UUID userId) {
        List<AdminDto.UserRow> rows = jdbc.query(LIST_SELECT + " WHERE u.id = ?", userRowMapper(), userId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Foydalanuvchi topilmadi.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));

        long publishedCount = count("""
                SELECT count(*) FROM posts
                WHERE user_id = ? AND deleted_at IS NULL AND published_at IS NOT NULL
                """, userId);
        long revealsMade = count("SELECT count(*) FROM contact_reveals WHERE viewer_id = ?", userId);
        long revealsReceived = count("SELECT count(*) FROM contact_reveals WHERE owner_id = ?", userId);

        List<AdminDto.PostRow> recentPosts = postsOfUser(userId);

        List<AdminDto.EventRow> events = jdbc.query("""
                SELECT event_name, source, properties, occurred_at
                FROM events WHERE user_id = ?
                ORDER BY occurred_at DESC LIMIT ?
                """,
                (rs, i) -> new AdminDto.EventRow(
                        rs.getString("event_name"),
                        rs.getString("source"),
                        readProperties(rs.getString("properties")),
                        instant(rs.getTimestamp("occurred_at"))),
                userId, RECENT_EVENTS);

        return new AdminDto.UserDetail(
                rows.get(0),
                user.getUiLanguage(),
                user.getBlockedReason(),
                user.getPhoneVerifiedAt() != null,
                publishedCount, revealsMade, revealsReceived,
                recentPosts, events);
    }

    private List<AdminDto.PostRow> postsOfUser(UUID userId) {
        return jdbc.query("""
                SELECT p.id, p.created_at, p.published_at, p.status, p.post_type, p.direction,
                       p.origin_airport, p.dest_airport, p.depart_date, p.deadline_date,
                       p.price_amount, p.price_currency, p.price_unit, p.weight_kg,
                       p.view_count, p.contact_reveal_count, p.channel_message_id, p.reject_reason,
                       u.id AS user_id, u.username, u.first_name, u.last_name
                FROM posts p JOIN users u ON u.id = p.user_id
                WHERE p.user_id = ? AND p.deleted_at IS NULL
                ORDER BY p.created_at DESC LIMIT ?
                """,
                (rs, i) -> new AdminDto.PostRow(
                        rs.getObject("id", UUID.class),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("published_at")),
                        rs.getString("status"),
                        rs.getString("post_type"),
                        rs.getString("direction"),
                        rs.getString("origin_airport"),
                        rs.getString("dest_airport"),
                        rs.getObject("depart_date", java.time.LocalDate.class),
                        rs.getObject("deadline_date", java.time.LocalDate.class),
                        rs.getBigDecimal("price_amount"),
                        rs.getString("price_currency"),
                        rs.getString("price_unit"),
                        rs.getBigDecimal("weight_kg"),
                        "LOW",
                        List.of(),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("username"),
                        AdminPostService.displayName(rs.getString("first_name"), rs.getString("last_name")),
                        rs.getInt("view_count"),
                        rs.getInt("contact_reveal_count"),
                        0L,
                        (Long) rs.getObject("channel_message_id"),
                        rs.getString("reject_reason")),
                userId, RECENT_POSTS);
    }

    // ------------------------------------------------------------------
    // Harakatlar
    // ------------------------------------------------------------------

    @Transactional
    public AdminDto.UserDetail block(UUID userId, String reason, UUID actorId) {
        User user = require(userId);
        user.setStatus(UserStatus.BLOCKED);
        user.setBlockedReason(reason.strip());
        userRepository.saveAndFlush(user);

        recordModeration(actorId, ModerationAction.BLOCK, userId, reason,
                Map.of("status", UserStatus.ACTIVE.name()), Map.of("status", UserStatus.BLOCKED.name()));
        auditService.record(actorId, "USER_BLOCK", "USER", userId.toString(), Map.of());
        // §6.1 (Xavfsizlik). PII yozilmaydi — faqat ID va sabab uzunligi (§1.7).
        eventLogger.track(TrackedEvent.of(EventName.USER_BLOCKED, EventSource.ADMIN)
                .user(actorId)
                .property("target_user_id", userId.toString())
                .build());
        log.info("Foydalanuvchi bloklandi: user_id={} actor_id={}", userId, actorId);
        return detail(userId);
    }

    @Transactional
    public AdminDto.UserDetail unblock(UUID userId, UUID actorId) {
        User user = require(userId);
        user.setStatus(UserStatus.ACTIVE);
        user.setBlockedReason(null);
        userRepository.saveAndFlush(user);

        recordModeration(actorId, ModerationAction.UNBLOCK, userId, null,
                Map.of("status", UserStatus.BLOCKED.name()), Map.of("status", UserStatus.ACTIVE.name()));
        auditService.record(actorId, "USER_UNBLOCK", "USER", userId.toString(), Map.of());
        return detail(userId);
    }

    @Transactional
    public AdminDto.UserDetail verify(UUID userId, String rawLevel, UUID actorId) {
        VerificationLevel level;
        try {
            level = VerificationLevel.valueOf(rawLevel.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Tasdiqlash darajasi noto'g'ri.",
                    Map.of("level", "NONE, PHONE yoki DOCUMENT bo'lishi kerak."));
        }

        User user = require(userId);
        VerificationLevel before = user.getVerificationLevel();
        user.setVerificationLevel(level);
        user.setVerifiedAt(level == VerificationLevel.NONE ? null : Instant.now());
        userRepository.saveAndFlush(user);

        recordModeration(actorId, ModerationAction.EDIT, userId, "verification",
                Map.of("verification_level", before == null ? "NONE" : before.name()),
                Map.of("verification_level", level.name()));
        auditService.record(actorId, "USER_VERIFY", "USER", userId.toString(),
                Map.of("level", level.name()));
        return detail(userId);
    }

    private User require(UUID userId) {
        return userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));
    }

    private void recordModeration(UUID actorId, ModerationAction action, UUID userId, String reason,
                                  Map<String, Object> before, Map<String, Object> after) {
        ModerationActionLog entry = new ModerationActionLog();
        entry.setActorId(actorId);
        entry.setTargetType(ModerationTarget.USER);
        entry.setTargetId(userId);
        entry.setAction(action);
        entry.setReason(reason);
        entry.setBeforeJson(before);
        entry.setAfterJson(after);
        moderationLogRepository.save(entry);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private Map<String, Object> readProperties(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            // Buzilgan JSON butun sahifani yiqitmasin.
            log.warn("Event properties o'qilmadi");
            return Map.of();
        }
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
