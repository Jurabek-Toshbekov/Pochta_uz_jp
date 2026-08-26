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
 * @param channelUsername CHANNEL_USERNAME — kanaldagi postga havola yasash uchun (@ belgisiz)
 * @param miniappUrl     MINIAPP_URL — WebApp tugmasi shu manzilni ochadi
 * @param initDataMaxAgeSeconds  initData qancha vaqt amal qiladi (§7.1, 6-qadam)
 */
@Validated
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        @NotBlank String token,
        @NotBlank String username,
        String channelChatId,
        String channelUsername,
        String miniappUrl,
        long initDataMaxAgeSeconds
) {

    /** Mini App'da e'lonni ochadigan deep link (§8.4). */
    public String deepLinkForPost(Object postId) {
        return "https://t.me/" + username + "/app?startapp=ch_" + postId;
    }

    /**
     * Kanaldagi postga havola. {@code CHANNEL_USERNAME} sozlanmagan bo'lsa
     * {@code null} — UI havolasiz muvaffaqiyat ekranini ko'rsatadi.
     */
    public String channelUrlForMessage(Long messageId) {
        if (messageId == null || channelUsername == null || channelUsername.isBlank()) {
            return null;
        }
        return "https://t.me/" + channelUsername.strip().replace("@", "") + "/" + messageId;
    }
}
