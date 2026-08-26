package uz.pochtajp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram API klienti — yagona bean.
 *
 * <p>Ilgari kanalga yuborish va botga javob berish ikkita alohida klient
 * yaratardi; bitta bean bo'lsa ulanish puli ham bitta bo'ladi va token
 * faqat shu yerda ishlatiladi (§1.2).
 */
@Configuration
public class TelegramClientConfig {

    @Bean
    public TelegramClient telegramClient(BotProperties botProperties) {
        return new OkHttpTelegramClient(botProperties.token());
    }
}
