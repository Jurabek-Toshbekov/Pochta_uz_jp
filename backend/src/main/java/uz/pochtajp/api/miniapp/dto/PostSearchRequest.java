package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;

/**
 * {@code GET /api/miniapp/posts} filtrlari (§10.1, §10.2).
 *
 * <p>Barcha maydonlar ixtiyoriy — filtrsiz so'rov "eng yangi e'lonlar"ni beradi.
 *
 * @param origin  chiqish aeroportlari (ko'p tanlov)
 * @param dest    kelish aeroportlari (ko'p tanlov)
 * @param q       matn qidiruvi — izoh va erkin kiritilgan joy nomlari bo'yicha
 * @param cursor  keyset kursori; birinchi sahifada {@code null} (§10.2)
 */
public record PostSearchRequest(
        PostType type,
        Direction direction,
        @Size(max = 10) List<String> origin,
        @Size(max = 10) List<String> dest,
        LocalDate dateFrom,
        LocalDate dateTo,
        @Size(max = 10) List<Short> categories,
        BigDecimal priceMax,
        Currency currency,
        Boolean verifiedOnly,
        @Size(max = 200) String q,
        PostSort sort,
        String cursor,
        @Min(1) @Max(50) Integer size
) {

    private static final int DEFAULT_SIZE = 20;

    public PostSort sortOrDefault() {
        return sort == null ? PostSort.NEWEST : sort;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public boolean firstPage() {
        return cursor == null || cursor.isBlank();
    }

    public Set<String> originCodes() {
        return normalizeCodes(origin);
    }

    public Set<String> destCodes() {
        return normalizeCodes(dest);
    }

    public Set<Short> categoryIds() {
        return categories == null ? Set.of() : Set.copyOf(categories);
    }

    public String textQuery() {
        return q == null || q.isBlank() ? null : q.strip();
    }

    public boolean verified() {
        return Boolean.TRUE.equals(verifiedOnly);
    }

    /** {@code search_queries} jadvaliga yozish uchun bitta aeroport kodi (§10.2). */
    public String primaryOrigin() {
        return originCodes().stream().findFirst().orElse(null);
    }

    public String primaryDest() {
        return destCodes().stream().findFirst().orElse(null);
    }

    /** Obunaga aylantirilganda ishlatiladi (§10.3). */
    public boolean hasAnyFilter() {
        return type != null || direction != null || !originCodes().isEmpty() || !destCodes().isEmpty()
                || dateFrom != null || dateTo != null || !categoryIds().isEmpty()
                || priceMax != null || textQuery() != null || verified();
    }

    private static Set<String> normalizeCodes(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toUpperCase())
                .filter(value -> value.length() >= 3 && value.length() <= 4)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Sessiya konteksti event va {@code search_queries} uchun. */
    public record Context(UUID sessionId, String platform) {
    }
}
