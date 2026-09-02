package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;

/**
 * Saqlangan qidiruv → obuna (§10.3).
 *
 * <p>Muhim nuance: obuna <b>qarama-qarshi tomonni</b> kutadi. Yuk yubormoqchi
 * bo'lgan odam {@code CARRY} e'lonlarini kutadi. Shu sabab {@code postType}
 * qidiruv filtridan qanday kelsa shundoq saqlanadi — UI to'g'ri qiymatni
 * yuboradi, backend uni o'zgartirmaydi.
 *
 * @param sessionId {@code search_saved} eventini qidiruv sessiyasiga bog'lash uchun
 */
public record SubscriptionRequest(
        PostType postType,
        Direction direction,
        @Pattern(regexp = "^[A-Z]{3,4}$", message = "Aeroport kodi to'g'ri emas")
        String originAirport,
        @Pattern(regexp = "^[A-Z]{3,4}$", message = "Aeroport kodi to'g'ri emas")
        String destAirport,
        LocalDate dateFrom,
        LocalDate dateTo,
        @Size(max = 10) List<Short> categoryIds,
        UUID sessionId,
        @Size(max = 24) String platform
) {

    /** Bo'sh obuna barcha e'lonni yuboradi — bu spam, ruxsat berilmaydi. */
    public boolean hasAnyCriterion() {
        return postType != null || direction != null || originAirport != null || destAirport != null
                || dateFrom != null || dateTo != null
                || (categoryIds != null && !categoryIds.isEmpty());
    }
}
