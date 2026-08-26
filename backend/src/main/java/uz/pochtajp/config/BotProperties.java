package uz.pochtajp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Telegram sozlamalari. Barcha qiymatlar environment variable'dan keladi —
 * kodda sir bo'lmaydi (CLAUDE.md §1.2).
 *
 * @param token             BOT_TOKEN — initData imzosini tekshirish uchun ham ishlatiladi (§7.1)
 * @param username          BOT_USERNAME
 * @param channelChatId     CHANNEL_CHAT_ID — e'lonlar chiqadigan kanal
 * @param channelUsername   CHANNEL_USERNAME — kanaldagi postga havola yasash uchun (@ belgisiz)
 * @param miniappUrl        MINIAPP_URL — WebApp tugmasi shu manzilni ochadi
 * @param initDataMaxAgeSeconds  initData qancha vaqt amal qiladi (§7.1, 6-qadam)
 * @param mode              BOT_MODE — polling (dev) | webhook (prod) | off
 * @param webhookBaseUrl    WEBHOOK_BASE_URL — webhook rejimida backend'ning tashqi manzili
 * @param webhookSecretToken WEBHOOK_SECRET_TOKEN — {@code X-Telegram-Bot-Api-Secret-Token} (§7.2)
 */
@Validated
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        @NotBlank String token,
        @NotBlank String username,
        String channelChatId,
        String channelUsername,
        String miniappUrl,
        long initDataMaxAgeSeconds,
        BotMode mode,
        String webhookBaseUrl,
        String webhookSecretToken
) {

    public BotProperties {
        mode = mode == null ? BotMode.OFF : mode;
    }

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

    /**
     * WebApp tugmasi ochadigan manzil. Mini App HashRouter ishlatadi, shuning
     * uchun ekranga to'g'ridan-to'g'ri o'tish uchun hash yetarli — {@code startapp}
     * kerak emas (u faqat {@code t.me} havolalarida ishlaydi).
     *
     * @param route masalan {@code "/new"}; bo'sh bo'lsa bosh sahifa
     */
    public String miniappUrlFor(String route) {
        if (miniappUrl == null || miniappUrl.isBlank()) {
            return null;
        }
        String base = miniappUrl.strip();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (route == null || route.isBlank() || "/".equals(route)) {
            return base + "/";
        }
        return base + "/#" + (route.startsWith("/") ? route : "/" + route);
    }

    public boolean hasMiniapp() {
        return miniappUrl != null && !miniappUrl.isBlank();
    }

    /** Webhook manzili — sir yo'lda emas, header'da (§7.2). */
    public String webhookUrl() {
        if (webhookBaseUrl == null || webhookBaseUrl.isBlank()) {
            return null;
        }
        String base = webhookBaseUrl.strip();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/webhook/telegram";
    }
}
