package uz.pochtajp.api.miniapp.dto;

/**
 * "Bog'lanish" bosilgandan keyin ochiladigan kontakt (§6.4, 2-band).
 *
 * <p>Bu javob {@code contact_reveals} jadvaliga yozilgandan keyin qaytadi —
 * ya'ni har bir kontakt ochilishi o'lchanadi. Bu fill rate va match latency
 * metrikalarining yagona manbasi (§6.3).
 */
public record ContactResponse(
        String telegram,
        String phone,
        String other,
        boolean alreadyRevealed
) {
}
