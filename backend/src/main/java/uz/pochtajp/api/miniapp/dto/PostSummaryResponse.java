package uz.pochtajp.api.miniapp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;
import uz.pochtajp.domain.enums.VerificationLevel;

/**
 * Qidiruv natijasidagi e'lon.
 *
 * <p><b>Kontakt maydonlari YO'Q.</b> Kontakt faqat "Bog'lanish" bosilganda
 * {@code POST /posts/{id}/reveal-contact} orqali ochiladi (§6.4, 2-band) —
 * bu niyat signalini beradi va scraping'ni qiyinlashtiradi.
 *
 * @param verified  egasining ishonch darajasi {@code NONE} emas
 */
public record PostSummaryResponse(
        UUID id,
        PostType postType,
        Direction direction,
        String originAirport,
        String destAirport,
        String originCityFree,
        String destCityFree,
        String finalDestination,
        LocalDate departDate,
        LocalDate deadlineDate,
        int dateFlexibleDays,
        BigDecimal weightKg,
        BigDecimal weightKgMax,
        BigDecimal priceAmount,
        Currency priceCurrency,
        PriceUnit priceUnit,
        List<Short> categoryIds,
        String comment,
        boolean verified,
        VerificationLevel verificationLevel,
        int trustScore,
        int viewCount,
        int contactRevealCount,
        Instant publishedAt,
        Instant expiresAt
) {
}
