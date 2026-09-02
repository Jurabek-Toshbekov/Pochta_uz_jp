package uz.pochtajp.bot;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.pochtajp.config.BotProperties;

/**
 * Ishga tushishda Telegram'ga webhook manzilini aytadi (§13, 2-bosqich).
 *
 * <p>Sir URL'da ham, {@code secret_token}da ham bir xil — ikkisi
 * {@link BotWebhookController}da tekshiriladi (§7.2).
 */
@Component
@ConditionalOnProperty(name = "bot.mode", havingValue = "webhook")
public class BotWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(BotWebhookRegistrar.class);

    /** Bizga faqat shu turdagi update'lar kerak — qolganini Telegram yubormaydi. */
    private static final List<String> ALLOWED_UPDATES = List.of("message", "callback_query");

    private final BotProperties botProperties;
    private final TelegramClient telegramClient;
    private final BotMessenger messenger;

    public BotWebhookRegistrar(BotProperties botProperties,
                               TelegramClient telegramClient,
                               BotMessenger messenger) {
        this.botProperties = botProperties;
        this.telegramClient = telegramClient;
        this.messenger = messenger;
    }

    @PostConstruct
    void register() {
        String base = botProperties.webhookUrl();
        String secret = botProperties.webhookSecretToken();

        if (base == null || secret == null || secret.isBlank()) {
            log.error("Webhook sozlanmagan: WEBHOOK_BASE_URL va WEBHOOK_SECRET_TOKEN kerak");
            return;
        }

        try {
            telegramClient.execute(SetWebhook.builder()
                    .url(base + "/" + secret)
                    .secretToken(secret)
                    .allowedUpdates(ALLOWED_UPDATES)
                    .dropPendingUpdates(false)
                    .build());
            // Manzil log'ga yozilmaydi — ichida sir bor (§1.2).
            log.info("Webhook o'rnatildi: @{}", botProperties.username());
            messenger.publishCommandMenu();
        } catch (TelegramApiException ex) {
            log.error("Webhook o'rnatilmadi: {}", ex.getMessage());
        }
    }
}
