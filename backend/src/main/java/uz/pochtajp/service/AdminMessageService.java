package uz.pochtajp.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import uz.pochtajp.bot.BotNotifier;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.repository.UserRepository;

/**
 * Admin ↔ foydalanuvchi yozishmasi bot orqali.
 *
 * <p>Admin panelda yozilgan xabarni bot yetkazadi. Foydalanuvchi botga oddiy
 * matn yozsa, oxirgi {@link #REPLY_WINDOW} ichida unga admin yozgan bo'lsagina
 * bu matn javob sifatida saqlanadi — aks holda bot odatdagidek "buyruqlar
 * bilan ishlayman" deydi (§8.3) va matn hech qayerga yozilmaydi.
 *
 * <p>Xabar matni log'ga yozilmaydi (§1.7) — faqat ID'lar va uzunlik.
 */
@Service
public class AdminMessageService {

    private static final Logger log = LoggerFactory.getLogger(AdminMessageService.class);

    /** Admin yozgandan keyin shuncha vaqt foydalanuvchi matni javob deb qabul qilinadi. */
    static final Duration REPLY_WINDOW = Duration.ofDays(7);

    private static final int MAX_BODY = 3500;
    private static final int THREAD_LIMIT = 300;
    private static final int CONVERSATION_LIMIT = 200;
    private static final int PREVIEW_LENGTH = 120;

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final BotNotifier botNotifier;
    private final AuditService auditService;
    private final EventLogger eventLogger;

    public AdminMessageService(JdbcTemplate jdbc,
                               UserRepository userRepository,
                               BotNotifier botNotifier,
                               AuditService auditService,
                               EventLogger eventLogger) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.botNotifier = botNotifier;
        this.auditService = auditService;
        this.eventLogger = eventLogger;
    }

    // ------------------------------------------------------------------
    // Admin tomoni
    // ------------------------------------------------------------------

    /**
     * Xabarni yuboradi va natijasidan qat'i nazar saqlaydi: yetkazilmagan xabar
     * ({@code FAILED}, odatda bot bloklangan) ham suhbatda ko'rinib tursin.
     */
    public AdminDto.MessageRow send(UUID userId, String rawText, UUID adminId) {
        String body = rawText == null ? "" : rawText.strip();
        if (body.isEmpty() || body.length() > MAX_BODY) {
            throw new ValidationException("Xabar matni noto'g'ri.",
                    Map.of("text", "1 dan " + MAX_BODY + " belgigacha bo'lishi kerak."));
        }
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));

        // Telegram so'rovi tranzaksiyadan tashqarida — sekin tarmoq bazani ushlab turmasin.
        boolean delivered = botNotifier.sendAdminMessage(user, body);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO admin_messages (id, user_id, direction, admin_id, body, delivery_status)
                VALUES (?, ?, 'OUTBOUND', ?, ?, ?)
                """, id, userId, adminId, body, delivered ? "SENT" : "FAILED");

        auditService.record(adminId, "ADMIN_MESSAGE_SEND", "USER", userId.toString(),
                Map.of("message_id", id.toString(), "delivered", delivered));
        eventLogger.track(TrackedEvent.of(EventName.ADMIN_MESSAGE_SENT, EventSource.ADMIN)
                .user(adminId)
                .property("target_user_id", userId.toString())
                .property("delivered", delivered)
                .property("length", body.length())
                .build());
        log.info("Admin xabari: message_id={} user_id={} actor_id={} delivered={}",
                id, userId, adminId, delivered);

        return jdbc.query(SELECT_MESSAGE + " WHERE m.id = ?", messageRowMapper(), id).get(0);
    }

    /** Suhbat, eskisidan yangisiga (chat ko'rinishida pastda eng yangisi). */
    public List<AdminDto.MessageRow> thread(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Foydalanuvchi topilmadi.");
        }
        List<AdminDto.MessageRow> thread = new ArrayList<>(jdbc.query(SELECT_MESSAGE + """
                 WHERE m.user_id = ? AND m.deleted_at IS NULL
                ORDER BY m.created_at DESC LIMIT ?
                """, messageRowMapper(), userId, THREAD_LIMIT));
        Collections.reverse(thread);
        return thread;
    }

    /** Foydalanuvchi javoblarini o'qilgan deb belgilash (admin suhbatni ochganda). */
    @Transactional
    public int markRead(UUID userId) {
        return jdbc.update("""
                UPDATE admin_messages SET read_at = now(), updated_at = now()
                WHERE user_id = ? AND direction = 'INBOUND' AND read_at IS NULL AND deleted_at IS NULL
                """, userId);
    }

    /** Barcha suhbatlar: o'qilmagani borlari tepada, keyin oxirgi xabar vaqti bo'yicha. */
    public List<AdminDto.Conversation> conversations() {
        return jdbc.query("""
                SELECT DISTINCT ON (m.user_id)
                       m.user_id, u.username, u.first_name, u.last_name,
                       m.direction, m.body, m.created_at,
                       (SELECT count(*) FROM admin_messages x
                        WHERE x.user_id = m.user_id AND x.direction = 'INBOUND'
                          AND x.read_at IS NULL AND x.deleted_at IS NULL) AS unread
                FROM admin_messages m
                JOIN users u ON u.id = m.user_id
                WHERE m.deleted_at IS NULL
                ORDER BY m.user_id, m.created_at DESC
                """,
                (rs, i) -> new AdminDto.Conversation(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("username"),
                        AdminPostService.displayName(rs.getString("first_name"), rs.getString("last_name")),
                        rs.getString("direction"),
                        preview(rs.getString("body")),
                        instant(rs.getTimestamp("created_at")),
                        rs.getLong("unread")))
                .stream()
                .sorted((a, b) -> {
                    int byUnread = Boolean.compare(b.unreadCount() > 0, a.unreadCount() > 0);
                    return byUnread != 0 ? byUnread : b.lastAt().compareTo(a.lastAt());
                })
                .limit(CONVERSATION_LIMIT)
                .toList();
    }

    public long unreadCount() {
        Long value = jdbc.queryForObject("""
                SELECT count(*) FROM admin_messages
                WHERE direction = 'INBOUND' AND read_at IS NULL AND deleted_at IS NULL
                """, Long.class);
        return value == null ? 0L : value;
    }

    // ------------------------------------------------------------------
    // Bot tomoni
    // ------------------------------------------------------------------

    /** Foydalanuvchiga oxirgi {@link #REPLY_WINDOW} ichida admin yozganmi. */
    public boolean hasOpenConversation(UUID userId) {
        Boolean open = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM admin_messages
                    WHERE user_id = ? AND direction = 'OUTBOUND' AND delivery_status = 'SENT'
                      AND deleted_at IS NULL AND created_at > ?
                )
                """, Boolean.class, userId, Timestamp.from(Instant.now().minus(REPLY_WINDOW)));
        return Boolean.TRUE.equals(open);
    }

    /**
     * Botga kelgan matnni javob sifatida saqlaydi — faqat suhbat ochiq bo'lsa.
     *
     * @return saqlangan bo'lsa {@code true}; aks holda bot odatdagi javobini beradi
     */
    public boolean acceptReply(UUID userId, String rawText) {
        if (rawText == null || rawText.isBlank() || !hasOpenConversation(userId)) {
            return false;
        }
        String body = rawText.strip();
        if (body.length() > 4000) {
            body = body.substring(0, 3999) + "…";
        }
        jdbc.update("""
                INSERT INTO admin_messages (user_id, direction, body, delivery_status)
                VALUES (?, 'INBOUND', ?, 'RECEIVED')
                """, userId, body);
        eventLogger.track(TrackedEvent.of(EventName.ADMIN_MESSAGE_REPLY, EventSource.BOT)
                .user(userId)
                .property("length", body.length())
                .build());
        log.info("Foydalanuvchi adminga javob yozdi: user_id={}", userId);
        return true;
    }

    // ------------------------------------------------------------------

    private static final String SELECT_MESSAGE = """
            SELECT m.id, m.direction, m.body, m.delivery_status, m.admin_id, a.username AS admin_username,
                   m.read_at, m.created_at
            FROM admin_messages m
            LEFT JOIN users a ON a.id = m.admin_id
            """;

    private RowMapper<AdminDto.MessageRow> messageRowMapper() {
        return (rs, i) -> new AdminDto.MessageRow(
                rs.getObject("id", UUID.class),
                rs.getString("direction"),
                rs.getString("body"),
                rs.getString("delivery_status"),
                rs.getObject("admin_id", UUID.class),
                rs.getString("admin_username"),
                instant(rs.getTimestamp("read_at")),
                instant(rs.getTimestamp("created_at")));
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() > PREVIEW_LENGTH ? flat.substring(0, PREVIEW_LENGTH) + "…" : flat;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
