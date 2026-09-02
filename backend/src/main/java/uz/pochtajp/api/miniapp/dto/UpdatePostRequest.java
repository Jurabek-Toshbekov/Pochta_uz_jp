package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.PriceUnit;

/**
 * {@code PATCH /api/miniapp/posts/{id}} — o'z e'lonini tahrirlash (§9.1, §12).
 *
 * <p>Nima o'zgartirilmaydi va nega: <b>tur, yo'nalish va aeroportlar</b>.
 * Ular e'lonning o'zligi — ularni almashtirish aslida boshqa e'lon yasash
 * bo'ladi, lekin ko'rishlar, kontakt ochilishlari va kanaldagi post eskisiga
 * tegishli bo'lib qolaveradi. Natijada metrikalar (narx indeksi, talab/taklif)
 * buziladi. Yo'nalish o'zgarsa foydalanuvchi eskisini yopib, yangisini beradi.
 *
 * <p>Har bir maydon ixtiyoriy: {@code null} — "tegmang" degani. Bo'sh satr
 * esa "tozalang" degani (izoh, yakuniy manzil, aloqa uchun).
 */
public record UpdatePostRequest(
        @Size(max = 120) String finalDestination,

        LocalDate departDate,
        LocalDate deadlineDate,
        @Min(value = 0, message = "Moslashuv kuni 0 dan kichik bo'lmaydi")
        @Max(value = 7, message = "Moslashuv kuni 7 kundan oshmaydi")
        Integer dateFlexibleDays,

        @DecimalMin(value = "0.1", message = "Og'irlik 0.1 kg dan kam bo'lmaydi")
        @DecimalMax(value = "100.0", message = "Og'irlik 100 kg dan oshmaydi")
        BigDecimal weightKg,
        @DecimalMin(value = "0.1", message = "Og'irlik 0.1 kg dan kam bo'lmaydi")
        @DecimalMax(value = "100.0", message = "Og'irlik 100 kg dan oshmaydi")
        BigDecimal weightKgMax,

        @DecimalMin(value = "0.0", inclusive = false, message = "Narx 0 dan katta bo'lishi kerak")
        @DecimalMax(value = "9999999999.0", message = "Narx juda katta")
        BigDecimal priceAmount,
        Currency priceCurrency,
        PriceUnit priceUnit,

        @Size(max = 5, message = "Ko'pi bilan 5 ta yuk turi tanlanadi")
        List<Short> categoryIds,

        @Size(max = 1000, message = "Izoh 1000 belgidan oshmaydi") String comment,

        @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,32}$", message = "Telefon raqami to'g'ri emas")
        String contactPhone,
        @Size(max = 64) String contactTelegram,
        @Size(max = 160) String contactOther,

        java.util.UUID sessionId,
        @Size(max = 24) String platform
) {

    /** {@code null} bo'lmasa — kategoriyalar almashtiriladi. */
    public Set<Short> distinctCategoryIds() {
        return categoryIds == null ? null : Set.copyOf(categoryIds);
    }
}
