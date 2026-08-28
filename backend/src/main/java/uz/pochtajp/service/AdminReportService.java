package uz.pochtajp.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.Report;
import uz.pochtajp.domain.enums.ReportStatus;
import uz.pochtajp.repository.ReportRepository;

/**
 * Shikoyatlar navbati (§11.2 — /reports).
 *
 * <p>Shikoyat o'chirilmaydi (§1.1): hal qilingani ham, rad etilgani ham
 * jadvalda qoladi — report rate metrikasi shundan hisoblanadi (§6.3).
 */
@Service
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final ReportRepository reportRepository;
    private final AuditService auditService;
    private final TrustScoreService trustScoreService;

    public AdminReportService(JdbcTemplate jdbc,
                              ReportRepository reportRepository,
                              AuditService auditService,
                              TrustScoreService trustScoreService) {
        this.jdbc = jdbc;
        this.reportRepository = reportRepository;
        this.auditService = auditService;
        this.trustScoreService = trustScoreService;
    }

    public record Filter(String status, String reason, int page, int size) {

        public Filter {
            page = Math.max(0, page);
            size = size <= 0 || size > MAX_PAGE_SIZE ? 25 : size;
        }
    }

    @Transactional(readOnly = true)
    public AdminDto.Page<AdminDto.ReportRow> list(Filter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();

        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND r.status = ?");
            args.add(filter.status());
        }
        if (filter.reason() != null && !filter.reason().isBlank()) {
            where.append(" AND r.reason = ?");
            args.add(filter.reason());
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM reports r" + where,
                Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(filter.size());
        pageArgs.add(filter.page() * filter.size());

        // Ochiq shikoyatlar tepada — navbat shu tartibda ishlanadi.
        List<AdminDto.ReportRow> items = jdbc.query("""
                SELECT r.id, r.reason, r.status, r.details, r.post_id, r.reported_user_id,
                       r.reporter_id, r.created_at, r.resolved_at, u.username AS reported_username
                FROM reports r
                LEFT JOIN users u ON u.id = r.reported_user_id
                """ + where + """
                 ORDER BY (r.status = 'OPEN') DESC, r.created_at DESC
                 LIMIT ? OFFSET ?
                """,
                (rs, i) -> new AdminDto.ReportRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("details"),
                        rs.getObject("post_id", UUID.class),
                        rs.getObject("reported_user_id", UUID.class),
                        rs.getString("reported_username"),
                        rs.getObject("reporter_id", UUID.class),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("resolved_at"))),
                pageArgs.toArray());

        return AdminDto.Page.of(items, total == null ? 0 : total, filter.page(), filter.size());
    }

    /**
     * Shikoyatni yakunlaydi.
     *
     * @param resolution {@code RESOLVED} — chora ko'rildi, {@code DISMISSED} — asossiz
     */
    @Transactional
    public AdminDto.ReportRow resolve(UUID reportId, String resolution, String note, UUID actorId) {
        ReportStatus status;
        try {
            status = ReportStatus.valueOf(resolution.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Qaror noto'g'ri.",
                    Map.of("resolution", "RESOLVED yoki DISMISSED bo'lishi kerak."));
        }
        if (status != ReportStatus.RESOLVED && status != ReportStatus.DISMISSED) {
            throw new ValidationException("Qaror noto'g'ri.",
                    Map.of("resolution", "RESOLVED yoki DISMISSED bo'lishi kerak."));
        }

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Shikoyat topilmadi."));

        report.setStatus(status);
        report.setResolvedBy(actorId);
        report.setResolvedAt(Instant.now());
        if (note != null && !note.isBlank()) {
            String existing = report.getDetails() == null ? "" : report.getDetails() + "\n";
            report.setDetails(existing + "[admin] " + note.strip());
        }
        reportRepository.saveAndFlush(report);

        auditService.record(actorId, "REPORT_RESOLVE", "REPORT", reportId.toString(),
                Map.of("resolution", status.name()));

        // Faqat ASOSLI (RESOLVED) shikoyat ishonch ballini tushiradi —
        // shuning uchun ball aynan shu yerda qayta hisoblanadi.
        if (report.getReportedUserId() != null) {
            trustScoreService.recompute(report.getReportedUserId());
        }
        log.info("Shikoyat yakunlandi: report_id={} status={} actor_id={}", reportId, status, actorId);

        return single(reportId);
    }

    private AdminDto.ReportRow single(UUID reportId) {
        List<AdminDto.ReportRow> rows = jdbc.query("""
                SELECT r.id, r.reason, r.status, r.details, r.post_id, r.reported_user_id,
                       r.reporter_id, r.created_at, r.resolved_at, u.username AS reported_username
                FROM reports r
                LEFT JOIN users u ON u.id = r.reported_user_id
                WHERE r.id = ?
                """,
                (rs, i) -> new AdminDto.ReportRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("details"),
                        rs.getObject("post_id", UUID.class),
                        rs.getObject("reported_user_id", UUID.class),
                        rs.getString("reported_username"),
                        rs.getObject("reporter_id", UUID.class),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("resolved_at"))),
                reportId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Shikoyat topilmadi.");
        }
        return rows.get(0);
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
