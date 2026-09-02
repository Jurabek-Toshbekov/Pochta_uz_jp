package uz.pochtajp.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin API so'rov tanalari (§12).
 *
 * <p>Har birida Bean Validation bor (§7.2). Sabab matni majburiy:
 * sababi yozilmagan moderatsiya harakati keyin tushunarsiz bo'lib qoladi
 * va foydalanuvchiga nima deyishni ham bilib bo'lmaydi.
 */
public final class AdminRequests {

    private AdminRequests() {
    }

    /** Bot bergan bir martalik kod (§11.1). */
    public record LoginRequest(
            @NotBlank(message = "Kodni kiriting.")
            @Size(max = 32, message = "Kod juda uzun.")
            String code
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh token yo'q.")
            String refreshToken
    ) {
    }

    public record RejectRequest(
            @NotBlank(message = "Rad etish sababini yozing.")
            @Size(max = 500, message = "Sabab 500 belgidan oshmasin.")
            String reason
    ) {
    }

    public record BlockRequest(
            @NotBlank(message = "Bloklash sababini yozing.")
            @Size(max = 500, message = "Sabab 500 belgidan oshmasin.")
            String reason
    ) {
    }

    /** {@code NONE}, {@code PHONE} yoki {@code DOCUMENT} (§5.2). */
    public record VerifyRequest(
            @NotBlank(message = "Tasdiqlash darajasini tanlang.")
            String level
    ) {
    }

    /** {@code RESOLVED} yoki {@code DISMISSED}. */
    public record ResolveReportRequest(
            @NotBlank(message = "Qarorni tanlang.")
            String resolution,
            @Size(max = 500, message = "Izoh 500 belgidan oshmasin.")
            String note
    ) {
    }

    /** Sozlama qiymati: boolean, son yoki matn — turi serverda tekshiriladi. */
    public record UpdateSettingRequest(
            @NotNull(message = "Qiymat ko'rsatilmagan.")
            Object value
    ) {
    }

    /** Moderator e'lonni tahrirlashi (§11.2). Faqat matn maydonlari. */
    public record UpdatePostRequest(
            @Size(max = 1000, message = "Izoh 1000 belgidan oshmasin.")
            String comment,
            @Size(max = 120, message = "Manzil 120 belgidan oshmasin.")
            String finalDestination
    ) {
    }
}
