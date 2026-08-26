package uz.pochtajp.security;

import java.util.UUID;
import uz.pochtajp.domain.enums.UserRole;

/**
 * Autentifikatsiya qilingan Mini App foydalanuvchisi.
 *
 * <p>{@code userId} — bizning bazadagi UUID, {@code telegramId} — Telegram ID.
 * Ikkisi ham faqat tekshirilgan {@code initData}dan keladi (§7.1).
 *
 * @param startParam deep link atributsiyasi uchun ({@code startapp=})
 * @param newUser    shu so'rovda birinchi marta ro'yxatga olindi
 */
public record MiniAppPrincipal(
        UUID userId,
        long telegramId,
        UserRole role,
        String startParam,
        boolean newUser
) {
}
