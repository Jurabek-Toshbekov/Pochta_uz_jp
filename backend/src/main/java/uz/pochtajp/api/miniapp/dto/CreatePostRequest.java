package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;

/**
 * {@code POST /api/miniapp/posts} — 4 qadamli formaning natijasi (§9.2).
 *
 * <p>Muhim: {@code userId} bu yerda YO'Q. Egasi faqat {@code initData}dan
 * aniqlanadi (§7.1). Sana {@link LocalDate}, narx {@link BigDecimal} + valyuta
 * enum, aeroport IATA kodi — erkin matn saqlash taqiqlangan (§1.5).
 *
 * @param postType          SEND (pochta yuboraman) | CARRY (olib ketaman)
 * @param direction         JP_UZ | UZ_JP
 * @param originAirport     IATA kodi; ro'yxatda bo'lmasa {@code originCityFree}
 * @param departDate        CARRY uchun uchish sanasi
 * @param deadlineDate      SEND uchun oxirgi muddat
 * @param dateFlexibleDays  ±N kun (0..7)
 * @param priceUnit         PER_KG | TOTAL | NEGOTIABLE — birlik majburiy (§6.4, 8-band)
 * @param categoryIds       kamida bitta yuk kategoriyasi
 * @param safetyChecklistOk 3 ta katakcha belgilanganini tasdiqlaydi (§7.3)
 * @param sessionId         voronka event'larini bir sessiyaga bog'lash uchun
 */
public record CreatePostRequest(
        @NotNull(message = "E'lon turini tanlang") PostType postType,
        @NotNull(message = "Yo'nalishni tanlang") Direction direction,

        @Pattern(regexp = "^[A-Z]{3,4}$", message = "Aeroport kodi to'g'ri emas")
        String originAirport,
        @Pattern(regexp = "^[A-Z]{3,4}$", message = "Aeroport kodi to'g'ri emas")
        String destAirport,
        @Size(max = 120) String originCityFree,
        @Size(max = 120) String destCityFree,
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
        @NotNull(message = "Narx birligini tanlang") PriceUnit priceUnit,

        @NotEmpty(message = "Kamida bitta yuk turini tanlang")
        @Size(max = 5, message = "Ko'pi bilan 5 ta yuk turi tanlanadi")
        List<Short> categoryIds,

        @Size(max = 1000, message = "Izoh 1000 belgidan oshmaydi") String comment,

        @Size(max = 32) @Pattern(regexp = "^\\+?[0-9 ()-]{7,32}$", message = "Telefon raqami to'g'ri emas")
        String contactPhone,
        @Size(max = 64) String contactTelegram,
        @Size(max = 160) String contactOther,

        @NotNull(message = "Xavfsizlik shartlarini tasdiqlang") Boolean safetyChecklistOk,

        java.util.UUID sessionId,
        @Size(max = 24) String platform,
        /* Forma ochilgan vaqt (epoch ms) — time-to-publish metrikasi uchun (§6.3). */
        Long formStartedAtMs
) {

    /** Sana maydonlaridan e'lon turiga mos keladigani. */
    public LocalDate relevantDate() {
        return postType == PostType.CARRY ? departDate : deadlineDate;
    }

    public Set<Short> distinctCategoryIds() {
        return categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
    }

    public int flexibleDaysOrZero() {
        return dateFlexibleDays == null ? 0 : dateFlexibleDays;
    }
}
