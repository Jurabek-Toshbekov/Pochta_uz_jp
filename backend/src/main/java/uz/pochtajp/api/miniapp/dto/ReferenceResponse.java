package uz.pochtajp.api.miniapp.dto;

import java.math.BigDecimal;
import java.util.List;
import uz.pochtajp.domain.Airport;
import uz.pochtajp.domain.CargoCategory;
import uz.pochtajp.domain.Corridor;
import uz.pochtajp.domain.enums.RiskLevel;

/**
 * {@code GET /api/miniapp/reference} — aeroportlar, kategoriyalar, koridorlar (§12).
 *
 * <p>Erkin matn emas, ID/kod orqali bog'lanish uchun (§5.1). Mini App bu javobni
 * bir marta oladi va formada tanlov ro'yxatlarini undan quradi.
 */
public record ReferenceResponse(
        List<AirportDto> airports,
        List<CategoryDto> categories,
        List<CorridorDto> corridors
) {

    /**
     * @param code      IATA kodi — formada mono shriftda ko'rsatiladi (§9.4)
     * @param popular   mashhurlari ro'yxat tepasida turadi (§9.2, 2-qadam)
     */
    public record AirportDto(
            String code,
            String countryCode,
            String cityUz,
            String cityRu,
            String cityEn,
            String nameEn,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean popular,
            int sortOrder
    ) {

        public static AirportDto from(Airport airport) {
            return new AirportDto(
                    airport.getCode(), airport.getCountryCode(),
                    airport.getCityUz(), airport.getCityRu(), airport.getCityEn(),
                    airport.getNameEn(), airport.getLatitude(), airport.getLongitude(),
                    airport.getPopular(), airport.getSortOrder());
        }
    }

    /**
     * @param riskLevel  HIGH bo'lsa Mini App {@code warningUz}ni darhol ko'rsatadi (§7.3)
     */
    public record CategoryDto(
            Short id,
            String code,
            String titleUz,
            String titleRu,
            String emoji,
            RiskLevel riskLevel,
            String warningUz,
            int sortOrder
    ) {

        public static CategoryDto from(CargoCategory category) {
            return new CategoryDto(
                    category.getId(), category.getCode(),
                    category.getTitleUz(), category.getTitleRu(), category.getEmoji(),
                    category.getRiskLevel(), category.getWarningUz(), category.getSortOrder());
        }
    }

    public record CorridorDto(
            Short id,
            String code,
            String originCountry,
            String destCountry,
            String titleUz
    ) {

        public static CorridorDto from(Corridor corridor) {
            return new CorridorDto(
                    corridor.getId(), corridor.getCode(),
                    corridor.getOriginCountry(), corridor.getDestCountry(), corridor.getTitleUz());
        }
    }
}
