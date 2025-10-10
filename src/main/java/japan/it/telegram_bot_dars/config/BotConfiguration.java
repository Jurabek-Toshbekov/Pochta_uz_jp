package japan.it.telegram_bot_dars.config;


import japan.it.telegram_bot_dars.service.TelegramBotMain;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


@Configuration
public class BotConfiguration {

    private final TelegramBotMain telegramBotMain;

    public BotConfiguration(TelegramBotMain telegramBotMain) {
        this.telegramBotMain = telegramBotMain;
    }


    @EventListener(ContextRefreshedEvent.class)
    public void init () {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(telegramBotMain);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
