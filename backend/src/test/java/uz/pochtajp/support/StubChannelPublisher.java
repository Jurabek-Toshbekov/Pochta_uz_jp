package uz.pochtajp.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uz.pochtajp.service.ChannelPublisher;

/**
 * Testlarda haqiqiy Telegram API o'rniga ishlatiladi: yuborilgan matnni eslab
 * qoladi va {@code message_id} qaytaradi.
 *
 * <p>Bu — {@link ChannelPublisher} interfeysi nima uchun kerakligining sababi:
 * publish oqimini tashqi tarmoqqa chiqmasdan to'liq tekshirish mumkin.
 */
public class StubChannelPublisher implements ChannelPublisher {

    private final AtomicLong messageIdSequence = new AtomicLong(9_000);
    private final List<String> sentMessages = new CopyOnWriteArrayList<>();

    private volatile boolean failing = false;
    private volatile boolean configured = true;

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public long publishToChannel(String html) {
        if (!configured) {
            throw new ChannelPublishException("CHANNEL_NOT_CONFIGURED", "Test: kanal sozlanmagan");
        }
        if (failing) {
            throw new ChannelPublishException("TELEGRAM_API_ERROR", "Test: Telegram javob bermadi");
        }
        sentMessages.add(html);
        return messageIdSequence.incrementAndGet();
    }

    public List<String> sentMessages() {
        return List.copyOf(sentMessages);
    }

    public String lastMessage() {
        return sentMessages.isEmpty() ? null : sentMessages.get(sentMessages.size() - 1);
    }

    public void failNextRequests() {
        this.failing = true;
    }

    public void reset() {
        sentMessages.clear();
        failing = false;
        configured = true;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public StubChannelPublisher stubChannelPublisher() {
            return new StubChannelPublisher();
        }
    }
}
