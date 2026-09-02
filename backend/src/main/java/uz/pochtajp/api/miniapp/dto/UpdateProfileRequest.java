package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/miniapp/me} — til va telefon (§12).
 *
 * <p>Faqat shu ikkisi: ism va username Telegram'dan keladi, rol va ishonch
 * balli esa foydalanuvchi qo'lida bo'lmasligi kerak.
 *
 * <p>{@code null} — "tegmang". Telefon uchun bo'sh satr — "o'chiring":
 * foydalanuvchi raqamini olib tashlay olishi kerak (§7.2 maxfiylik).
 *
 * @param uiLanguage uz | uz-cyrl | ru
 */
public record UpdateProfileRequest(
        @Pattern(regexp = "^(uz|uz-cyrl|ru)$", message = "Bunday til qo'llab-quvvatlanmaydi.")
        String uiLanguage,

        @Size(max = 32)
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,32}$", message = "Telefon raqami to'g'ri emas.")
        String phone
) {
}
