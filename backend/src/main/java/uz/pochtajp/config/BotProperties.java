package uz.pochtajp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Telegram sozlamalari. Barcha qiymatlar environment variable'dan keladi —
 * kodda sir bo'lmaydi (CLAUDE.md §1.2).
 *
 * @param token          BOT_TOKEN — initData imzosini tekshirish uchun ham ishlatiladi (§7.1)
 * @param username       BOT_USERNAME
 * @param channelChatId  CHANNEL_CHAT_ID — e'lonlar chiqadigan kanal
 * @param miniappUrl     MINIAPP_URL — WebApp tugmasi shu manzilni ochadi
 * @param initDataMaxAgeSeconds  initData qancha vaqt amal qiladi (§7.1, 6-qadam)
 */
@Validated
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        @NotBlank String token,
        @NotBlank String username,
        String channelChatId,
        String miniappUrl,
        long initDataMaxAgeSeconds
) {
}
