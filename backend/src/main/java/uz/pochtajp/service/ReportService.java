package uz.pochtajp.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.Report;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.ReportReason;
import uz.pochtajp.domain.enums.ReportStatus;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.ReportRepository;

/**
 * Shikoyat oqimi (§7.3 — har bir e'londa "Shikoyat qilish" tugmasi).
 *
 * <p>Shikoyat darhol hech narsani o'chirmaydi va e'lonni yopmaydi:
 * qaror moderatorniki (§11.2). Bu ataylab — aks holda raqobatchini
 * bir bosishda yo'q qilish mumkin bo'lardi.
 *
 * <p>Bitta odam bitta e'longa bir marta shikoyat qiladi (V6 unikal indeks).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final int DETAILS_MAX = 1000;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final EventLogger eventLogger;

    public ReportService(ReportRepository reportRepository,
                         PostRepository postRepository,
                         EventLogger eventLogger) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.eventLogger = eventLogger;
    }

    /**
     * Shikoyat qabul qiladi.
     *
     * @param rawReason SPAM | SCAM | PROHIBITED | OFFENSIVE | OTHER
     * @return yaratilgan shikoyat ID'si
     */
    @Transactional
    public UUID submit(UUID postId, UUID reporterId, String rawReason, String details) {
        ReportReason reason = parseReason(rawReason);
        if (details != null && details.length() > DETAILS_MAX) {
            throw new ValidationException("Izoh juda uzun.",
                    Map.of("details", DETAILS_MAX + " belgidan oshmasin."));
        }

        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));

        UUID ownerId = post.getUser().getId();
        if (ownerId.equals(reporterId)) {
            throw new ValidationException("O'z e'loningizga shikoyat qila olmaysiz.",
                    Map.of("post", "Kerak bo'lsa e'lonni yopishingiz mumkin."));
        }
        if (reportRepository.existsByPostIdAndReporterId(postId, reporterId)) {
            throw new ValidationException("Siz bu e'lon haqida allaqachon xabar bergansiz.",
                    Map.of("post", "Shikoyat ko'rib chiqilmoqda."));
        }

        Report report = new Report();
        report.setPostId(postId);
        report.setReportedUserId(ownerId);
        report.setReporterId(reporterId);
        report.setReason(reason);
        report.setDetails(details == null || details.isBlank() ? null : details.strip());
        report.setStatus(ReportStatus.OPEN);
        reportRepository.save(report);

        // §6.1 (Xavfsizlik). Izoh matni event'ga tushmaydi — ichida PII bo'lishi mumkin (§1.7).
        eventLogger.track(TrackedEvent.of(EventName.REPORT_SUBMITTED, EventSource.MINIAPP)
                .user(reporterId)
                .post(postId)
                .property("reason", reason.name())
                .build());

        log.info("Shikoyat qabul qilindi: post_id={} reporter_id={} reason={}",
                postId, reporterId, reason);
        return report.getId();
    }

    private static ReportReason parseReason(String raw) {
        try {
            return ReportReason.valueOf(raw == null ? "" : raw.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Shikoyat sababi tanlanmagan.",
                    Map.of("reason", "SPAM, SCAM, PROHIBITED, OFFENSIVE yoki OTHER."));
        }
    }
}
