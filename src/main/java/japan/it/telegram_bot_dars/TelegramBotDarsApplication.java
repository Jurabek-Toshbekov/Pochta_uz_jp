package japan.it.telegram_bot_dars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // Xo'jayin qilish uchun
@EnableJpaAuditing
public class TelegramBotDarsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramBotDarsApplication.class, args);

    }


}


