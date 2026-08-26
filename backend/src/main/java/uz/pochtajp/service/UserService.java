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
import uz.pochtajp.api.miniapp.dto.SessionRequest;
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
 * Foydalanuvchi yozuvini Telegram profilidan yaratadi va yangilaydi.
 *
 * <p>Ikki kirish nuqtasi bir xil mantiqdan foydalanadi:
 * Mini App ({@code initData}, §7.1) va bot (update ichidagi {@code from}).
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
     * API so'rovi {@code users} jadvaliga UPDATE beradi. Hibernate o'zgarmagan
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

    /** Telegram bergan profil — manbadan qat'i nazar bir xil shakl. */
    public record TelegramProfile(
            long telegramId,
            String username,
            String firstName,
            String lastName,
            String languageCode,
            boolean premium
    ) {
    }

    /** Upsert natijasi. */
    public record Session(User user, boolean created) {
    }

    /**
     * Telegram foydalanuvchisini topadi yoki yaratadi va profil ma'lumotini yangilaydi.
     * BLOCKED foydalanuvchi ishlashga qo'yilmaydi.
     *
     * @return foydalanuvchi va u shu so'rovda yaratilgani ({@code is_first_open} eventi uchun)
     */
    @Transactional
    public Session upsert(TelegramProfile profile, String referralSource) {
        boolean[] created = {false};
        User user = userRepository.findByTelegramId(profile.telegramId())
                .orElseGet(() -> {
                    created[0] = true;
                    return createUser(profile, referralSource);
                });

        if (user.getStatus() == UserStatus.BLOCKED) {
            log.warn("Bloklangan foydalanuvchi so'rov yubordi: user_id={}", user.getId());
            throw new ForbiddenException("Hisobingiz bloklangan. Sabab bo'yicha /yordam ga murojaat qiling.");
        }

        applyTelegramProfile(user, profile);
        Instant now = Instant.now();
        if (user.getLastSeenAt() == null || user.getLastSeenAt().plus(LAST_SEEN_THROTTLE).isBefore(now)) {
            user.setLastSeenAt(now);
        }
        if (user.getReferralSource() == null && referralSource != null) {
            user.setReferralSource(trim(referralSource, REFERRAL_MAX));
        }
        // ADMIN_TELEGRAM_IDS ro'yxatidagilar avtomatik ADMIN bo'ladi (§11.1).
        if (user.getRole() == UserRole.USER && appProperties.isAdmin(profile.telegramId())) {
            user.setRole(UserRole.ADMIN);
            log.info("Foydalanuvchiga ADMIN roli berildi: user_id={}", user.getId());
        }
        return new Session(userRepository.save(user), created[0]);
    }

    /** Mini App yo'li (§7.1, 7-qadam). */
    @Transactional
    public Session upsertFromInitData(TelegramInitData initData) {
        TelegramWebAppUser tgUser = initData.user();
        TelegramProfile profile = new TelegramProfile(
                tgUser.id(),
                tgUser.username(),
                tgUser.firstName(),
                tgUser.lastName(),
                tgUser.languageCode(),
                Boolean.TRUE.equals(tgUser.isPremium()));
        return upsert(profile, initData.startParam());
    }

    /**
     * Sessiya boshida keladigan tanlovlar: til va ToS/Privacy roziligi (§7.2).
     *
     * <p>Rozilik vaqti bir marta yoziladi va keyin o'zgartirilmaydi — bu huquqiy
     * dalil, shuning uchun qayta yozish mumkin emas.
     */
    @Transactional
    public User applySessionPreferences(UUID userId, SessionRequest request) {
        User user = getActiveForUpdate(userId);
        Instant now = Instant.now();

        if (request.uiLanguage() != null && !request.uiLanguage().equals(user.getUiLanguage())) {
            user.setUiLanguage(request.uiLanguage());
        }
        if (Boolean.TRUE.equals(request.acceptTos()) && user.getConsentTosAt() == null) {
            user.setConsentTosAt(now);
        }
        if (Boolean.TRUE.equals(request.acceptPrivacy()) && user.getConsentPrivacyAt() == null) {
            user.setConsentPrivacyAt(now);
        }
        return userRepository.save(user);
    }

    /** {@code /til} buyrug'i (§8.1). */
    @Transactional
    public User setUiLanguage(UUID userId, String uiLanguage) {
        User user = getActiveForUpdate(userId);
        user.setUiLanguage(uiLanguage);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getActive(UUID userId) {
        return getActiveForUpdate(userId);
    }

    private User getActiveForUpdate(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("Foydalanuvchi topilmadi.");
        }
        return user;
    }

    private User createUser(TelegramProfile profile, String referralSource) {
        User user = new User();
        user.setTelegramId(profile.telegramId());
        Instant now = Instant.now();
        user.setFirstSeenAt(now);
        user.setLastSeenAt(now);
        user.setUiLanguage(resolveUiLanguage(profile.languageCode()));
        if (referralSource != null) {
            user.setReferralSource(trim(referralSource, REFERRAL_MAX));
        }
        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Yangi foydalanuvchi yaratildi: user_id={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Bir vaqtda ikkita so'rov kelsa — telegram_id UNIQUE tutib qoladi.
            Optional<User> existing = userRepository.findByTelegramId(profile.telegramId());
            return existing.orElseThrow(() -> ex);
        }
    }

    private void applyTelegramProfile(User user, TelegramProfile profile) {
        // Ma'lumotini o'chirishni so'ragan foydalanuvchiga ismi qaytarilmaydi (§7.2).
        if (user.getDeletedAt() != null) {
            return;
        }
        user.setUsername(trim(profile.username(), USERNAME_MAX));
        user.setFirstName(trim(profile.firstName(), NAME_MAX));
        user.setLastName(trim(profile.lastName(), NAME_MAX));
        user.setLanguageCode(trim(profile.languageCode(), LANGUAGE_MAX));
        user.setTelegramPremium(profile.premium());
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
