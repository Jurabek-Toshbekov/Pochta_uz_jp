package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * {@code PUT /api/miniapp/drafts} — forma autosave (§6.4, 5-band).
 *
 * <p>Tashlab ketilgan draft — voronkadagi teshikning fotosurati, shuning uchun
 * har o'zgarishda saqlanadi va o'chirilmaydi (publish bo'lgandagina tozalanadi).
 *
 * @param step    joriy qadam kodi ({@code step1_type}, {@code step2_route}, ...)
 * @param payload formaning to'liq holati; sxemasi frontend'da, backend uni shunchaki saqlaydi
 */
public record DraftRequest(
        @Size(max = 40) String step,
        @NotNull(message = "Draft ma'lumoti bo'sh") Map<String, Object> payload
) {
}
