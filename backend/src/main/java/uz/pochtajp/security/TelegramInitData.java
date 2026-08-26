package uz.pochtajp.security;

import java.time.Instant;

/**
 * Tekshirilgan va tahlil qilingan {@code initData}.
 *
 * @param user       Telegram foydalanuvchisi (imzo tekshirilgani uchun ishonchli)
 * @param authDate   Telegram imzo qo'ygan vaqt
 * @param startParam {@code startapp=} parametri — deep link atributsiyasi (§6.4, 6-band)
 * @param chatType   private | group | supergroup | channel
 * @param queryId    inline javob uchun (hozircha ishlatilmaydi)
 */
public record TelegramInitData(
        TelegramWebAppUser user,
        Instant authDate,
        String startParam,
        String chatType,
        String queryId
) {
}
