package uz.pochtajp.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram API klienti — yagona bean.
 *
 * <p>Ilgari kanalga yuborish va botga javob berish ikkita alohida klient
 * yaratardi; bitta bean bo'lsa ulanish puli ham bitta bo'ladi va token
 * faqat shu yerda ishlatiladi (§1.2).
 *
 * <p>{@code BOT_API_URL} berilgan bo'lsa so'rovlar o'sha manzilga ketadi.
 * Bu faqat lokal uchidan-uchiga sinov uchun kerak: maket server kanalga
 * yuborishni ham, botning javoblarini ham haqiqiy Telegramsiz ko'rsatadi.
 * Sozlanmasa xatti-harakat o'zgarmaydi — rasmiy {@code api.telegram.org}.
 */
@Configuration
public class TelegramClientConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramClientConfig.class);

    @Bean
    public TelegramClient telegramClient(BotProperties botProperties) {
        TelegramUrl url = parseApiUrl(botProperties.apiUrl());
        if (url == null) {
            return new OkHttpTelegramClient(botProperties.token());
        }
        // Manzil sir emas, token esa hech qachon log'ga chiqmaydi (§1.2).
        log.warn("Telegram API manzili almashtirildi: {}://{}:{} — bu faqat lokal sinov rejimi",
                url.getSchema(), url.getHost(), url.getPort());
        return new OkHttpTelegramClient(botProperties.token(), url);
    }

    /** @return {@code null} — sozlanmagan, rasmiy manzil ishlatiladi */
    static TelegramUrl parseApiUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri = URI.create(raw.strip());
        String schema = uri.getScheme() == null ? "https" : uri.getScheme();
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("BOT_API_URL noto'g'ri: xost ko'rsatilmagan");
        }
        int port = uri.getPort() != -1 ? uri.getPort() : ("http".equals(schema) ? 80 : 443);
        return TelegramUrl.builder()
                .schema(schema)
                .host(uri.getHost())
                .port(port)
                .testServer(false)
                .build();
    }
}
