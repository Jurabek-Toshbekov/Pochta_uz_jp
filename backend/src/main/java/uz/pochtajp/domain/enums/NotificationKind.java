package uz.pochtajp.domain.enums;

/**
 * Xabarnoma turi (§10.3, §6.4).
 *
 * <p>Tur bo'yicha takrorlanmaslik kafolatlanadi: {@code notifications_sent}
 * jadvalida {@code (post_id, user_id, kind)} unikal. Ya'ni job qayta ishga
 * tushsa ham odam ikkinchi marta xabar olmaydi.
 */
public enum NotificationKind {

    /** Obunaga mos yangi e'lon chiqdi. */
    MATCH,

    /** Publish'dan 3 kun keyin: "Odam topdingizmi?" (§6.4, 1-band). */
    DEAL_ASK,

    /** E'lon muddati tugashiga 1 kun qoldi (§8.3). */
    EXPIRY_WARNING,

    /** Bitim tasdiqlangandan keyin baho so'rovi (§6.4, 7-band). */
    REVIEW_ASK
}
