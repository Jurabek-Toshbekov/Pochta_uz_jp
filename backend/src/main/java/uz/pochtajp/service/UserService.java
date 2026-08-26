package uz.pochtajp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.config.AppProperties;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.UserRole;
import uz.pochtajp.domain.enums.UserStatus;
import uz.pochtajp.repository.UserRepository;
import uz.pochtajp.security.TelegramInitData;
import uz.pochtajp.security.TelegramWebAppUser;

/**
 * Foydalanuvchi yozuvini {@code initData}dan yaratadi va yangilaydi (§7.1, 7-qadam).
 *
 * <p>Log'ga ism va telefon yozilmaydi — faqat {@code user_id} (§1.7).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int USERNAME_MAX = 64;
    private static final int NAME_MAX = 120;
    private static final int LANGUAGE_MAX = 8;
    private static final int REFERRAL_MAX = 120;

    /**
     * {@code last_seen_at} shu oynadan tez-tez yangilanmaydi: aks holda har bir
     * API so'rovi `users` jadvaliga UPDATE beradi. Hibernate o'zgarmagan
     * entity uchun UPDATE yubormaydi, ya'ni oyna ichida yozish umuman bo'lmaydi.
     * Admin panelidagi "oxirgi faollik" uchun 5 daqiqa aniqligi yetarli (§11.2).
     */
    private static final Duration LAST_SEEN_THROTTLE = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public UserService(UserRepository userRepository, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    /**
     * Telegram foydalanuvchisini topadi yoki yaratadi va profil ma'lumotini yangilaydi.
     * BLOCKED foydalanuvchi ishlashga qo'yilmaydi.
     */
    @Transactional
    public User upsertFromInitData(TelegramInitData initData) {
        TelegramWebAppUser tgUser = initData.user();
        long telegramId = tgUser.id();

        User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createUser(telegramId, initData));

        if (user.getStatus() == UserStatus.BLOCKED) {
            log.warn("Bloklangan foydalanuvchi so'rov yubordi: user_id={}", user.getId());
            throw new ForbiddenException("Hisobingiz bloklangan. Sabab bo'yicha /yordam ga murojaat qiling.");
        }

        applyTelegramProfile(user, tgUser);
        Instant now = Instant.now();
        if (user.getLastSeenAt() == null || user.getLastSeenAt().plus(LAST_SEEN_THROTTLE).isBefore(now)) {
            user.setLastSeenAt(now);
        }
        if (user.getReferralSource() == null && initData.startParam() != null) {
            user.setReferralSource(trim(initData.startParam(), REFERRAL_MAX));
        }
        // ADMIN_TELEGRAM_IDS ro'yxatidagilar avtomatik ADMIN bo'ladi (§11.1).
        if (user.getRole() == UserRole.USER && appProperties.isAdmin(telegramId)) {
            user.setRole(UserRole.ADMIN);
            log.info("Foydalanuvchiga ADMIN roli berildi: user_id={}", user.getId());
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getActive(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("Foydalanuvchi topilmadi.");
        }
        return user;
    }

    private User createUser(long telegramId, TelegramInitData initData) {
        User user = new User();
        user.setTelegramId(telegramId);
        Instant now = Instant.now();
        user.setFirstSeenAt(now);
        user.setLastSeenAt(now);
        user.setUiLanguage(resolveUiLanguage(initData.user().languageCode()));
        if (initData.startParam() != null) {
            user.setReferralSource(trim(initData.startParam(), REFERRAL_MAX));
        }
        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Yangi foydalanuvchi yaratildi: user_id={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Bir vaqtda ikkita so'rov kelsa — telegram_id UNIQUE tutib qoladi.
            Optional<User> existing = userRepository.findByTelegramId(telegramId);
            return existing.orElseThrow(() -> ex);
        }
    }

    private void applyTelegramProfile(User user, TelegramWebAppUser tgUser) {
        user.setUsername(trim(tgUser.username(), USERNAME_MAX));
        user.setFirstName(trim(tgUser.firstName(), NAME_MAX));
        user.setLastName(trim(tgUser.lastName(), NAME_MAX));
        user.setLanguageCode(trim(tgUser.languageCode(), LANGUAGE_MAX));
        user.setTelegramPremium(Boolean.TRUE.equals(tgUser.isPremium()));
    }

    /** Telegram tilidan boshlang'ich UI tili: ru -> ru, qolgani -> uz (§8.1 /til). */
    private String resolveUiLanguage(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase().startsWith("ru")) {
            return "ru";
        }
        return "uz";
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
