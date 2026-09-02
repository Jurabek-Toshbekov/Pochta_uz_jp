package uz.pochtajp.api.miniapp.dto;

/**
 * E'lon tafsiloti (§12 {@code GET /posts/{id}}) — kontaktsiz.
 *
 * @param post              e'lonning ochiq ma'lumoti
 * @param own               shu e'lon so'rov yuborgan foydalanuvchiga tegishli
 * @param contactRevealed   foydalanuvchi kontaktni allaqachon ochgan
 * @param deepLink          ulashish uchun havola (§8.4)
 * @param channelUrl        kanaldagi post (bo'lsa)
 */
public record PostDetailResponse(
        PostSummaryResponse post,
        boolean own,
        boolean contactRevealed,
        String deepLink,
        String channelUrl
) {
}
