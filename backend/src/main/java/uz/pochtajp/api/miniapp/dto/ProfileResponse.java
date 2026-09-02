package uz.pochtajp.api.miniapp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.UserRole;
import uz.pochtajp.domain.enums.VerificationLevel;

/**
 * {@code GET /api/miniapp/me} — profil ekrani (§9.1).
 *
 * <p>{@link SessionResponse} dan farqi: bu yerda telefon ham bor. Sessiya
 * javobi har ochilishda keladi, telefon esa faqat foydalanuvchi o'z profilini
 * ochganda kerak — kamroq joyda yurgani xavfsizroq (§7.2).
 *
 * <p>Telefon o'z egasiga to'liq ko'rsatiladi: bu uning o'z ma'lumoti.
 *
 * @param postCount      e'lonlari soni (o'chirilmaganlari)
 * @param activePostCount hozir kanalda turgan e'lonlari
 * @param dealCount      yakunlangan bitimlar — ishonch ballining asosi (§5-bosqich)
 * @param reviewCount    unga qoldirilgan baholar soni
 * @param averageRating  o'rtacha baho; baho bo'lmasa {@code null}
 */
public record ProfileResponse(
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String uiLanguage,
        UserRole role,
        VerificationLevel verificationLevel,
        int trustScore,
        String phone,
        boolean phoneVerified,
        Instant consentTosAt,
        Instant consentPrivacyAt,
        Instant firstSeenAt,
        long postCount,
        long activePostCount,
        long dealCount,
        long reviewCount,
        BigDecimal averageRating
) {

    public static ProfileResponse of(User user, long postCount, long activePostCount,
                                     long dealCount, long reviewCount, BigDecimal averageRating) {
        return new ProfileResponse(
                user.getTelegramId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getUiLanguage(),
                user.getRole(),
                user.getVerificationLevel(),
                user.getTrustScore(),
                user.getPhone(),
                user.getPhoneVerifiedAt() != null,
                user.getConsentTosAt(),
                user.getConsentPrivacyAt(),
                user.getFirstSeenAt(),
                postCount,
                activePostCount,
                dealCount,
                reviewCount,
                averageRating);
    }
}
