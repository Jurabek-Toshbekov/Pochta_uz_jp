package uz.pochtajp.api.miniapp.dto;

import java.time.Instant;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.UserRole;
import uz.pochtajp.domain.enums.VerificationLevel;

/**
 * {@code POST /api/miniapp/session} javobi — Mini App ochilganda birinchi so'rov.
 *
 * <p>Entity qaytarilmaydi, faqat DTO (§14). Telefon bu javobda YO'Q: u faqat
 * profil ekranida alohida so'rov bilan olinadi.
 *
 * @param username          Telegram username (kontakt sifatida oldindan to'ldirish uchun)
 * @param uiLanguage        uz | uz-cyrl | ru
 * @param needsConsent      ToS/Privacy roziligi hali olinmagan (§7.2)
 * @param startParam        {@code startapp=} qiymati — marshrutga aylantiriladi (§9.3)
 * @param isNewUser         birinchi kirish — {@code is_first_open} eventi uchun
 */
public record SessionResponse(
        String username,
        String firstName,
        String uiLanguage,
        UserRole role,
        VerificationLevel verificationLevel,
        int trustScore,
        boolean needsConsent,
        boolean phoneVerified,
        String startParam,
        boolean isNewUser,
        Instant serverTime
) {

    public static SessionResponse from(User user, String startParam, boolean isNewUser) {
        return new SessionResponse(
                user.getUsername(),
                user.getFirstName(),
                user.getUiLanguage(),
                user.getRole(),
                user.getVerificationLevel(),
                user.getTrustScore(),
                user.getConsentTosAt() == null || user.getConsentPrivacyAt() == null,
                user.getPhoneVerifiedAt() != null,
                startParam,
                isNewUser,
                Instant.now());
    }
}
