package uz.pochtajp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.enums.ClosedReason;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.NotificationKind;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.repository.ContactRevealRepository;
import uz.pochtajp.repository.PostRepository;

/**
 * "Odam topdingizmi?" savoliga javob (§6.4, 1-band va 3-band).
 *
 * <p>Bu — loyihaning eng qimmatli ma'lumoti. Kontakt ochilishi faqat
 * <i>niyat</i>ni ko'rsatadi, bu javob esa <i>natija</i>ni: e'lon ish
 * berdimi yoki yo'q.
 *
 * <p>Uchta javob ataylab alohida saqlanadi:
 * <ul>
 *   <li>{@code FOUND} — mahsulot ishladi</li>
 *   <li>{@code NOT_YET} — e'lon ochiq qoladi, lekin javob yozib olinadi:
 *       bu ulush o'ssa taklif yetishmayapti</li>
 *   <li>{@code CANCELLED} — odamning rejasi o'zgardi, mahsulotga aloqasi yo'q</li>
 * </ul>
 * Ularni bitta "yopildi" ga qo'shib yuborish ma'lumotni yo'qotadi.
 */
@Service
public class DealService {

    private static final Logger log = LoggerFactory.getLogger(DealService.class);

    /** Foydalanuvchining javobi. */
    public enum Answer {
        FOUND, NOT_YET, CANCELLED
    }

    private final PostRepository postRepository;
    private final ContactRevealRepository contactRevealRepository;
    private final NotificationService notificationService;
    private final TrustScoreService trustScoreService;
    private final EventLogger eventLogger;

    public DealService(PostRepository postRepository,
                       ContactRevealRepository contactRevealRepository,
                       NotificationService notificationService,
                       TrustScoreService trustScoreService,
                       EventLogger eventLogger) {
        this.postRepository = postRepository;
        this.contactRevealRepository = contactRevealRepository;
        this.notificationService = notificationService;
        this.trustScoreService = trustScoreService;
        this.eventLogger = eventLogger;
    }

    /**
     * Javobni qayd etadi.
     *
     * @param actorId javob bergan odam — e'lon egasi bo'lishi shart
     * @return baho so'ralishi kerak bo'lsa {@code true} (bitim tasdiqlangan)
     */
    @Transactional
    public boolean answer(UUID postId, UUID actorId, Answer answer) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));
        if (!post.getUser().getId().equals(actorId)) {
            throw new ForbiddenException("Bu e'lon sizniki emas.");
        }

        long hoursSincePublish = post.getPublishedAt() == null
                ? 0
                : Duration.between(post.getPublishedAt(), Instant.now()).toHours();

        eventLogger.track(TrackedEvent.of(EventName.DEAL_FOLLOWUP_ANSWER, EventSource.BOT)
                .user(actorId)
                .post(postId)
                .property("answer", answer.name())
                .property("hours_since_publish", hoursSincePublish)
                .build());

        return switch (answer) {
            case FOUND -> confirmDeal(post, actorId, hoursSincePublish);
            case CANCELLED -> {
                closePost(post, ClosedReason.CANCELLED, actorId, hoursSincePublish);
                yield false;
            }
            case NOT_YET -> {
                // Hech narsa o'zgarmaydi: e'lon ochiq qoladi. Javobning o'zi
                // yuqorida event sifatida yozilgan — metrikaga shu yetadi.
                yield false;
            }
        };
    }

    private boolean confirmDeal(Post post, UUID actorId, long hoursSincePublish) {
        post.setDealConfirmedAt(Instant.now());
        post.setDealCounterpartId(guessCounterpart(post.getId()));
        closePost(post, ClosedReason.FOUND, actorId, hoursSincePublish);

        eventLogger.track(TrackedEvent.of(EventName.DEAL_CONFIRMED, EventSource.BOT)
                .user(actorId)
                .post(post.getId())
                .property("counterpart_id", post.getDealCounterpartId() == null
                        ? null : post.getDealCounterpartId().toString())
                .property("hours_since_publish", hoursSincePublish)
                .build());

        // Bitim yakunlandi — ishonch balli o'zgaradi (yakunlangan bitim +5).
        trustScoreService.recompute(actorId);
        if (post.getDealCounterpartId() != null) {
            trustScoreService.recompute(post.getDealCounterpartId());
        }

        log.info("Bitim tasdiqlandi: post_id={} user_id={}", post.getId(), actorId);

        // Baho so'raymiz (§6.4, 7-band). Sherik noma'lum bo'lsa ham
        // e'lon egasidan so'rash mumkin emas — kimni baholaydi?
        return post.getDealCounterpartId() != null;
    }

    private void closePost(Post post, ClosedReason reason, UUID actorId, long hoursSincePublish) {
        post.setStatus(PostStatus.CLOSED);
        post.setClosedReason(reason);
        postRepository.save(post);

        eventLogger.track(TrackedEvent.of(EventName.POST_CLOSE, EventSource.BOT)
                .user(actorId)
                .post(post.getId())
                .property("reason", reason.name())
                .property("hours_since_publish", hoursSincePublish)
                .build());
    }

    /**
     * Bitim kim bilan bo'lganini taxmin qiladi.
     *
     * <p>Faqat bitta odam kontakt ochgan bo'lsa — o'sha. Bir nechta bo'lsa
     * taxmin qilinmaydi: noto'g'ri odamdan baho so'rash undan ham yomon.
     */
    private UUID guessCounterpart(UUID postId) {
        List<UUID> viewers = contactRevealRepository.findViewerIds(postId);
        return viewers.size() == 1 ? viewers.get(0) : null;
    }

    /** Bitim tasdiqlangandan keyin baho so'rovini yuboradi. */
    @Transactional
    public void askForReview(UUID postId) {
        postRepository.findByIdAndDeletedAtIsNull(postId).ifPresent(post ->
                notificationService.sendOnce(post.getUser(), post, NotificationKind.REVIEW_ASK));
    }
}
