package uz.pochtajp.domain.enums;

/**
 * E'lon yopilish sababi (§6.4, 3-band).
 *
 * <p>Uchta foydalanuvchi javobi ataylab alohida: {@code FOUND} — mahsulot
 * ishladi, {@code CANCELLED} — odamning rejasi o'zgardi (mahsulotga aloqasi
 * yo'q), {@code NO_ANSWER} — e'lon chiqdi-yu hech kim yozmadi. Oxirgisi
 * aynan mahsulot muammosi haqida gapiradi, shuning uchun uni "bekor qilindi"
 * bilan qo'shib yuborish ma'lumotni yo'qotadi.
 *
 * <p>{@code EXPIRED} — muddat tugagani uchun tizim yopgan.
 */
public enum ClosedReason {
    FOUND, CANCELLED, NO_ANSWER, EXPIRED
}
