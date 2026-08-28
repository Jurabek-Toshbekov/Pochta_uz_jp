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
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.Review;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.repository.ContactRevealRepository;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.ReviewRepository;

/**
 * Reytting va sharhlar (§13, 5-bosqich; §6.4, 7-band).
 *
 * <p>Kim kimga baho bera oladi — qat'iy qoida:
 * <ul>
 *   <li>e'lon egasi bitim sherigini baholaydi;</li>
 *   <li>kontakt ochgan odam e'lon egasini baholaydi.</li>
 * </ul>
 * Boshqa hech kim baho qoldira olmaydi. Aks holda reytting soxtalashtirish
 * quroliga aylanadi: begona odam raqobatchiga bir yulduz qo'yib ketardi.
 *
 * <p>Bitta e'longa bitta odam faqat bir marta baho beradi (V1 dagi unikal
 * indeks). Sharh <b>o'chirilmaydi</b> (§1.1) — kerak bo'lsa {@code deleted_at}.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private static final int COMMENT_MAX = 1000;

    private final ReviewRepository reviewRepository;
    private final PostRepository postRepository;
    private final ContactRevealRepository contactRevealRepository;
    private final TrustScoreService trustScoreService;
    private final EventLogger eventLogger;

    public ReviewService(ReviewRepository reviewRepository,
                         PostRepository postRepository,
                         ContactRevealRepository contactRevealRepository,
                         TrustScoreService trustScoreService,
                         EventLogger eventLogger) {
        this.reviewRepository = reviewRepository;
        this.postRepository = postRepository;
        this.contactRevealRepository = contactRevealRepository;
        this.trustScoreService = trustScoreService;
        this.eventLogger = eventLogger;
    }

    /**
     * Baho qoldiradi.
     *
     * @param rating 1..5
     * @return baholangan odamning yangi ishonch balli
     */
    @Transactional
    public int leave(UUID postId, UUID authorId, int rating, String comment, EventSource source) {
        if (rating < 1 || rating > 5) {
            throw new ValidationException("Baho 1 dan 5 gacha bo'lishi kerak.",
                    Map.of("rating", "1..5 oralig'ida bo'lsin."));
        }
        if (comment != null && comment.length() > COMMENT_MAX) {
            throw new ValidationException("Izoh juda uzun.",
                    Map.of("comment", COMMENT_MAX + " belgidan oshmasin."));
        }

        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));

        UUID subjectId = resolveSubject(post, authorId);
        if (reviewRepository.existsByPostIdAndAuthorIdAndDeletedAtIsNull(postId, authorId)) {
            throw new ValidationException("Siz bu e'longa allaqachon baho bergansiz.",
                    Map.of("rating", "Baho bir marta beriladi."));
        }

        Review review = new Review();
        review.setPostId(postId);
        review.setAuthorId(authorId);
        review.setSubjectId(subjectId);
        review.setRating((short) rating);
        review.setComment(comment == null || comment.isBlank() ? null : comment.strip());
        reviewRepository.save(review);

        eventLogger.track(TrackedEvent.of(EventName.REVIEW_LEFT, source)
                .user(authorId)
                .post(postId)
                .property("rating", rating)
                .build());

        int score = trustScoreService.recompute(subjectId);
        log.info("Baho qoldirildi: post_id={} author_id={} rating={}", postId, authorId, rating);
        return score;
    }

    /**
     * Kimni baholayapti.
     *
     * <p>E'lon egasi bo'lsa — bitim sherigini. Boshqa odam bo'lsa — e'lon
     * egasini, lekin faqat kontaktni ochgan bo'lsa: kontakt ochmagan odam
     * bitimda qatnashmagan.
     */
    private UUID resolveSubject(Post post, UUID authorId) {
        UUID ownerId = post.getUser().getId();

        if (ownerId.equals(authorId)) {
            UUID counterpart = post.getDealCounterpartId();
            if (counterpart == null) {
                throw new ValidationException("Bu e'lon bo'yicha sherik aniqlanmagan.",
                        Map.of("post", "Avval \"Odam topildi\" deb belgilang."));
            }
            return counterpart;
        }

        if (!contactRevealRepository.existsByPost_IdAndViewer_Id(post.getId(), authorId)) {
            throw new ForbiddenException("Baho qoldirish uchun avval kontaktni oching.");
        }
        return ownerId;
    }
}
