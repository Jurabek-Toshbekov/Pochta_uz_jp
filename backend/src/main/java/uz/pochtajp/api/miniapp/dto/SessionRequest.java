package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/miniapp/session} tanasi. Hammasi ixtiyoriy.
 *
 * @param acceptTos     foydalanish shartlariga rozilik (§7.2) — {@code consent_tos_at}
 * @param acceptPrivacy maxfiylik siyosatiga rozilik — {@code consent_privacy_at}
 * @param uiLanguage    foydalanuvchi tanlagan til: uz | uz-cyrl | ru
 */
public record SessionRequest(
        Boolean acceptTos,
        Boolean acceptPrivacy,
        @Pattern(regexp = "^(uz|uz-cyrl|ru)$", message = "Til qiymati to'g'ri emas")
        String uiLanguage,
        @Size(max = 24) String platform
) {

    public static SessionRequest empty() {
        return new SessionRequest(null, null, null, null);
    }
}
