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
 * @param miniappShortName  BOT_MINIAPP_SHORT_NAME — BotFather'da yaratilgan Mini App'ning
 *                          qisqa nomi. {@code t.me/<bot>/<qisqa nom>?startapp=...} havolasi
 *                          shundan yasaladi (§8.4). Telegram bu qismni qisqa nom deb o'qiydi:
 *                          BotFather'da bunday app bo'lmasa havola "application not found"
 *                          beradi. Standart {@code app}.
 * @param initDataMaxAgeSeconds  initData qancha vaqt amal qiladi (§7.1, 6-qadam)
 * @param mode              BOT_MODE — polling (dev) | webhook (prod) | off
 * @param webhookBaseUrl    WEBHOOK_BASE_URL — webhook rejimida backend'ning tashqi manzili
 * @param webhookSecretToken WEBHOOK_SECRET_TOKEN — {@code X-Telegram-Bot-Api-Secret-Token} (§7.2)
 * @param apiUrl            BOT_API_URL — Telegram API manzili. Bo'sh bo'lsa rasmiy
 *                          {@code https://api.telegram.org}. Faqat lokal sinov uchun
 *                          o'zgartiriladi (maket server); prod'da hech qachon to'ldirilmaydi.
 */
@Validated
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        @NotBlank String token,
        @NotBlank String username,
        String channelChatId,
        String channelUsername,
        String miniappUrl,
        String miniappShortName,
        long initDataMaxAgeSeconds,
        BotMode mode,
        String webhookBaseUrl,
        String webhookSecretToken,
        String apiUrl
) {

    public BotProperties {
        mode = mode == null ? BotMode.OFF : mode;
        miniappShortName = miniappShortName == null || miniappShortName.isBlank()
                ? "app"
                : miniappShortName.strip();
    }

    /** Mini App'da e'lonni ochadigan deep link (§8.4). Manba: kanal. */
    public String deepLinkForPost(Object postId) {
        return miniappLink("ch_" + postId);
    }

    /**
     * Xabarnomadagi havola (§10.3).
     *
     * <p>WebApp tugmasi emas, {@code t.me} havolasi: faqat shu ko'rinishda
     * {@code startapp} parametri Mini App'ga yetib boradi va ochilish
     * {@code notification_opened} sifatida yozila oladi. Atributsiyasiz
     * havola CTR'ni o'lchab bo'lmas holga keltiradi.
     */
    public String notificationLinkForPost(Object postId) {
        return miniappLink("nt_" + postId);
    }

    /** {@code t.me/<bot>/<qisqa nom>?startapp=<param>} — Mini App'ni ochadigan yagona format. */
    private String miniappLink(String startParam) {
        return "https://t.me/" + username + "/" + miniappShortName + "?startapp=" + startParam;
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
