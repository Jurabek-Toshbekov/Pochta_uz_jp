package uz.pochtajp.bot;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.pochtajp.config.BotProperties;

/**
 * Long polling — lokal ishlab chiqish uchun (§13, 2-bosqich).
 *
 * <p>Prod'da webhook ishlatiladi: {@code BOT_MODE=webhook}. Telegram bir vaqtda
 * ikkalasiga ruxsat bermaydi, shuning uchun polling boshlashdan oldin webhook
 * o'chiriladi.
 *
 * <p>Ishga tushmasa ilova <b>to'xtamaydi</b> — API va Mini App ishlashda davom
 * etadi. Eski kodda bot registratsiyasi yiqilsa butun JVM o'chib qolardi.
 */
@Component
@ConditionalOnProperty(name = "bot.mode", havingValue = "polling")
public class BotLongPollingRunner {

    private static final Logger log = LoggerFactory.getLogger(BotLongPollingRunner.class);

    private final BotProperties botProperties;
    private final BotUpdateHandler handler;
    private final BotMessenger messenger;
    private final TelegramClient telegramClient;

    private TelegramBotsLongPollingApplication application;

    public BotLongPollingRunner(BotProperties botProperties,
                                BotUpdateHandler handler,
                                BotMessenger messenger,
                                TelegramClient telegramClient) {
        this.botProperties = botProperties;
        this.handler = handler;
        this.messenger = messenger;
        this.telegramClient = telegramClient;
    }

    @PostConstruct
    void start() {
        try {
            telegramClient.execute(DeleteWebhook.builder().dropPendingUpdates(false).build());
        } catch (TelegramApiException ex) {
            log.warn("Webhook o'chirilmadi (polling'ga o'tishda): {}", ex.getMessage());
        }

        try {
            application = new TelegramBotsLongPollingApplication();
            application.registerBot(botProperties.token(),
                    (LongPollingSingleThreadUpdateConsumer) handler::handle);
            messenger.publishCommandMenu();
            log.info("Bot long polling rejimida ishga tushdi: @{}", botProperties.username());
        } catch (Exception ex) {
            // Token yaroqsiz bo'lsa ham backend ishlashda davom etadi.
            log.error("Botni long polling rejimida ishga tushirib bo'lmadi: {}", ex.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        if (application == null) {
            return;
        }
        try {
            application.close();
            log.info("Bot long polling to'xtatildi");
        } catch (Exception ex) {
            log.warn("Long polling'ni to'xtatishda xato: {}", ex.getMessage());
        }
    }
}
