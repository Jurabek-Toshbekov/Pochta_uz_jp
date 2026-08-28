package uz.pochtajp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.domain.AuditLog;
import uz.pochtajp.repository.AuditLogRepository;

/**
 * Audit jurnali (§11.2 — /audit sahifasi).
 *
 * <p>Har bir admin harakati yoziladi: kim, nima qildi, qaysi obyektga.
 * Jurnal <b>faqat qo'shiladi</b> — o'zgartirilmaydi va o'chirilmaydi (§1.1).
 *
 * <p>Yozuv asosiy oqimni to'xtatmaydi: {@code @Async} + o'z tranzaksiyasi.
 * Audit yozilmagani uchun moderatsiya harakati bekor bo'lib qolmasligi kerak.
 * PII yozilmaydi — payload'ga telefon va ism qo'yilmaydi (§1.7).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        JdbcTemplate jdbc,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String entity, String entityId,
                       Map<String, Object> payload) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorId(actorId);
            entry.setAction(action);
            entry.setEntity(entity);
            entry.setEntityId(entityId);
            entry.setPayload(payload == null ? new HashMap<>() : new HashMap<>(payload));
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.error("Audit yozuvi saqlanmadi: action={} entity={}", action, entity, ex);
        }
    }

    public void record(UUID actorId, String action, String entity, String entityId) {
        record(actorId, action, entity, entityId, Map.of());
    }

    /**
     * Jurnalni o'qish (§11.2 — /audit). Faqat o'qish: yozuv o'zgartirilmaydi.
     */
    public AdminDto.Page<AdminDto.AuditRow> list(String action, UUID actorId, int page, int size) {
        int safeSize = size <= 0 || size > MAX_PAGE_SIZE ? 50 : size;
        int safePage = Math.max(0, page);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            where.append(" AND a.action = ?");
            args.add(action.strip());
        }
        if (actorId != null) {
            where.append(" AND a.actor_id = ?");
            args.add(actorId);
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_log a" + where,
                Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);

        List<AdminDto.AuditRow> items = jdbc.query(
                "SELECT a.id, a.actor_id, a.action, a.entity, a.entity_id, a.payload, a.created_at,"
                        + " u.username AS actor_username"
                        + " FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id"
                        + where
                        + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new AdminDto.AuditRow(
                        rs.getLong("id"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_username"),
                        rs.getString("action"),
                        rs.getString("entity"),
                        rs.getString("entity_id"),
                        readPayload(rs.getString("payload")),
                        rs.getTimestamp("created_at") == null
                                ? null : rs.getTimestamp("created_at").toInstant()),
                pageArgs.toArray());

        return AdminDto.Page.of(items, total == null ? 0 : total, safePage, safeSize);
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("Audit payload o'qilmadi");
            return Map.of();
        }
    }
}
