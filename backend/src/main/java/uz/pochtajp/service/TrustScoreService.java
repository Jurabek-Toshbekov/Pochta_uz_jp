package uz.pochtajp.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.ReportStatus;
import uz.pochtajp.domain.enums.VerificationLevel;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.ReportRepository;
import uz.pochtajp.repository.ReviewRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * Ishonch balli (§13, 5-bosqich).
 *
 * <p>Ball 0..100 oralig'ida va to'rtta manbadan yig'iladi. Formulani
 * bir joyda va ochiq saqlash muhim: foydalanuvchi "nega mening ballim
 * past" deb so'rasa, javob bo'lishi kerak.
 *
 * <pre>
 *   baho          0..50  o'rtacha baho x 10 (5 yulduz = 50)
 *   yakunlangan   0..20  tasdiqlangan bitim x 5
 *   tasdiqlanish  0..20  PHONE +10, DOCUMENT +20
 *   shikoyat     -0..40  ASOSLI (RESOLVED) shikoyat x 10
 * </pre>
 *
 * <p>Faqat <b>hal qilingan</b> shikoyat ballni tushiradi. Ochiq shikoyat
 * hali tekshirilmagan — u bilan jazolash raqobatchiga qurol berish bo'lardi.
 *
 * <p>Bahosi yo'q odamning balli 0 emas, {@code NEUTRAL_BASE}: yangi
 * foydalanuvchi "yomon" emas, shunchaki noma'lum.
 */
@Service
public class TrustScoreService {

    private static final Logger log = LoggerFactory.getLogger(TrustScoreService.class);

    static final int NEUTRAL_BASE = 10;
    static final int MAX_SCORE = 100;

    private static final int RATING_WEIGHT = 10;
    private static final int RATING_CAP = 50;
    private static final int DEAL_WEIGHT = 5;
    private static final int DEAL_CAP = 20;
    private static final int REPORT_PENALTY = 10;
    private static final int REPORT_CAP = 40;

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;

    public TrustScoreService(UserRepository userRepository,
                             ReviewRepository reviewRepository,
                             ReportRepository reportRepository,
                             PostRepository postRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
    }

    /** Hisoblash uchun kerakli xom raqamlar. */
    public record Inputs(double averageRating, long reviewCount, long confirmedDeals,
                         long resolvedReports, VerificationLevel verificationLevel) {
    }

    /**
     * Sof funksiya — test shu yerga yoziladi.
     *
     * <p>Natija doim {@code 0..100} oralig'ida.
     */
    public static int calculate(Inputs inputs) {
        int score = NEUTRAL_BASE;

        if (inputs.reviewCount() > 0) {
            score += Math.min(RATING_CAP, (int) Math.round(inputs.averageRating() * RATING_WEIGHT));
        }
        score += (int) Math.min(DEAL_CAP, inputs.confirmedDeals() * DEAL_WEIGHT);
        score += switch (inputs.verificationLevel() == null
                ? VerificationLevel.NONE : inputs.verificationLevel()) {
            case NONE -> 0;
            case PHONE -> 10;
            case DOCUMENT -> 20;
        };
        score -= (int) Math.min(REPORT_CAP, inputs.resolvedReports() * REPORT_PENALTY);

        return Math.max(0, Math.min(MAX_SCORE, score));
    }

    /**
     * Foydalanuvchining ballini qayta hisoblab saqlaydi.
     *
     * <p>Sharh qoldirilganda, bitim tasdiqlanganda va shikoyat hal
     * qilinganda chaqiriladi — ya'ni ball har doim so'nggi holatni
     * aks ettiradi.
     */
    @Transactional
    public int recompute(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElse(null);
        if (user == null) {
            return 0;
        }

        long reviewCount = reviewRepository.countBySubjectIdAndDeletedAtIsNull(userId);
        double averageRating = reviewCount == 0 ? 0 : reviewRepository.averageRating(userId);

        Inputs inputs = new Inputs(
                averageRating,
                reviewCount,
                postRepository.countConfirmedDeals(userId),
                reportRepository.countByReportedUserIdAndStatus(userId, ReportStatus.RESOLVED),
                user.getVerificationLevel());

        int score = calculate(inputs);
        user.setTrustScore(score);
        userRepository.save(user);

        log.debug("Ishonch balli yangilandi: user_id={} score={}", userId, score);
        return score;
    }
}
