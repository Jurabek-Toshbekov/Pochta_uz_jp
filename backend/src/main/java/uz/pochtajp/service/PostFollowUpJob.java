package uz.pochtajp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.NotificationKind;
import uz.pochtajp.repository.PostRepository;

/**
 * E'lon egasiga yuboriladigan ikkita eslatma (§6.4, 1-band va §8.3).
 *
 * <ol>
 *   <li><b>"Odam topdingizmi?"</b> — publish'dan 3 kun keyin. Bu savol
 *       loyihadagi eng qimmatli ma'lumotni beradi: e'lon haqiqatan ish
 *       berdimi. Kontakt ochilishi faqat niyat, bu esa natija.</li>
 *   <li><b>Muddat ogohlantirishi</b> — tugashiga 1 kun qolganda. Odam
 *       e'loni jim yo'qolib qolganini keyin bilib qolmasin.</li>
 * </ol>
 *
 * <p>Ikkalasi ham kuniga bir marta ishlaydi va idempotent: takroriy xabar
 * {@code notifications_sent} dagi unikal indeks bilan to'siladi.
 */
@Service
public class PostFollowUpJob {

    private static final Logger log = LoggerFactory.getLogger(PostFollowUpJob.class);

    /** §6.4: publish + 3 kun. */
    static final Duration DEAL_ASK_AFTER = Duration.ofDays(3);

    /** §8.3: muddat tugashiga 1 kun qolganda. */
    static final Duration EXPIRY_WARN_BEFORE = Duration.ofDays(1);

    /** Bir yugurishda ko'pi bilan shuncha e'lon — Telegram chegarasini urmaslik uchun. */
    private static final int BATCH = 200;

    private final PostRepository postRepository;
    private final NotificationService notificationService;

    public PostFollowUpJob(PostRepository postRepository, NotificationService notificationService) {
        this.postRepository = postRepository;
        this.notificationService = notificationService;
    }

    /** Har kuni 09:00 UTC — O'zbekistonda tush payti, Yaponiyada kechqurun. */
    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    public void run() {
        askAboutDeals();
        warnAboutExpiry();
    }

    /** Ochiq metod: test va qo'lda ishga tushirish uchun. */
    @Transactional
    public int askAboutDeals() {
        Instant before = Instant.now().minus(DEAL_ASK_AFTER);
        List<Post> candidates = postRepository.findDealFollowUpCandidates(before, PageRequest.of(0, BATCH));

        int sent = 0;
        for (Post post : candidates) {
            if (send(post, NotificationKind.DEAL_ASK)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("\"Odam topdingizmi?\" so'rovi yuborildi: count={}", sent);
        }
        return sent;
    }

    @Transactional
    public int warnAboutExpiry() {
        Instant now = Instant.now();
        List<Post> candidates = postRepository.findExpiringSoon(
                now, now.plus(EXPIRY_WARN_BEFORE), PageRequest.of(0, BATCH));

        int sent = 0;
        for (Post post : candidates) {
            if (send(post, NotificationKind.EXPIRY_WARNING)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Muddat ogohlantirishi yuborildi: count={}", sent);
        }
        return sent;
    }

    /**
     * Bitta e'lon egasiga xabar. Bloklangan yoki o'chirilgan foydalanuvchi
     * o'tkazib yuboriladi — bitta xato butun yugurishni to'xtatmasligi kerak.
     */
    private boolean send(Post post, NotificationKind kind) {
        try {
            User owner = post.getUser();
            if (owner == null || owner.getDeletedAt() != null || owner.getTelegramId() == null) {
                return false;
            }
            return notificationService.sendOnce(owner, post, kind);
        } catch (RuntimeException ex) {
            log.error("Eslatma yuborilmadi: post_id={} kind={}", post.getId(), kind, ex);
            return false;
        }
    }
}
