package uz.pochtajp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.ProfileResponse;
import uz.pochtajp.api.miniapp.dto.UpdateProfileRequest;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.ReviewRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * Profil ekrani (§9.1 — {@code /profile}).
 *
 * <p>Foydalanuvchi o'zi haqidagi ma'lumotni ko'radi va ikkitasini
 * o'zgartira oladi: til va telefon. Qolgani (rol, ishonch balli,
 * tasdiqlanish darajasi) — tizim hisoblaydigan qiymatlar.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final EventLogger eventLogger;

    public ProfileService(UserRepository userRepository,
                          PostRepository postRepository,
                          ReviewRepository reviewRepository,
                          EventLogger eventLogger) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.reviewRepository = reviewRepository;
        this.eventLogger = eventLogger;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return toResponse(require(userId));
    }

    /**
     * Til va telefonni yangilaydi.
     *
     * <p>Telefon o'zgarsa {@code phone_verified_at} tozalanadi: eski
     * tasdiqlanish yangi raqamga tegishli emas. Bo'sh satr — o'chirish.
     */
    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        User user = require(userId);

        if (request.uiLanguage() != null && !request.uiLanguage().equals(user.getUiLanguage())) {
            String from = user.getUiLanguage();
            user.setUiLanguage(request.uiLanguage());
            eventLogger.track(TrackedEvent.of(EventName.LANGUAGE_CHANGED, EventSource.MINIAPP)
                    .user(userId)
                    .property("from", from)
                    .property("to", request.uiLanguage())
                    .build());
        }

        if (request.phone() != null) {
            String phone = request.phone().strip();
            String next = phone.isEmpty() ? null : phone;
            if (!java.util.Objects.equals(user.getPhone(), next)) {
                user.setPhone(next);
                user.setPhoneVerifiedAt(null);
                // Raqamning o'zi log'ga yozilmaydi (§1.7).
                log.info("Profil telefoni o'zgardi: user_id={} bor={}", userId, next != null);
            }
        }

        userRepository.save(user);
        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        UUID userId = user.getId();
        long reviewCount = reviewRepository.countBySubjectIdAndDeletedAtIsNull(userId);
        BigDecimal average = reviewCount == 0
                ? null
                : BigDecimal.valueOf(reviewRepository.averageRating(userId))
                        .setScale(2, RoundingMode.HALF_UP);

        return ProfileResponse.of(user,
                postRepository.countByUser_IdAndDeletedAtIsNull(userId),
                postRepository.countByUser_IdAndStatusAndDeletedAtIsNull(userId, PostStatus.PUBLISHED),
                postRepository.countConfirmedDeals(userId),
                reviewCount,
                average);
    }

    private User require(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("Foydalanuvchi topilmadi.");
        }
        return user;
    }
}
