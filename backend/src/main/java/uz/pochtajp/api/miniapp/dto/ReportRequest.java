package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * E'lon ustidan shikoyat (§7.3 — har bir e'londa tugma bo'ladi).
 *
 * <p>{@code userId} so'rov tanasida yo'q va bo'lmaydi ham — u faqat
 * tekshirilgan {@code initData}dan olinadi (§7.1).
 */
public record ReportRequest(
        @NotNull(message = "E'lon tanlanmagan.")
        UUID postId,

        @NotBlank(message = "Shikoyat sababini tanlang.")
        String reason,

        @Size(max = 1000, message = "Izoh 1000 belgidan oshmasin.")
        String details
) {
}
