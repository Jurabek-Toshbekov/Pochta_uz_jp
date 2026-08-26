package uz.pochtajp.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Buyruqni ajratish — guruhda {@code /elon@bot} ko'rinishida keladi. */
class BotCommandTest {

    @Test
    @DisplayName("§8.1 dagi barcha buyruqlar mavjud")
    void allCommandsDefined() {
        assertThat(Arrays.stream(BotCommand.values()).map(BotCommand::command))
                .containsExactlyInAnyOrder(
                        "/start", "/elon", "/qidiruv", "/mening_elonlarim", "/obuna",
                        "/xavfsizlik", "/qoidalar", "/til", "/yordam", "/mening_malumotlarim");
    }

    @Test
    @DisplayName("Oddiy buyruq")
    void parsesPlainCommand() {
        assertThat(BotCommand.parse("/elon")).contains(BotCommand.NEW_POST);
    }

    @Test
    @DisplayName("Bot username bilan: /elon@uzb_jp_elon_bot")
    void parsesCommandWithBotUsername() {
        assertThat(BotCommand.parse("/elon@uzb_jp_elon_bot")).contains(BotCommand.NEW_POST);
    }

    @Test
    @DisplayName("Katta harf bilan yozilgan buyruq ham tanib olinadi")
    void parsesUppercase() {
        assertThat(BotCommand.parse("/START")).contains(BotCommand.START);
    }

    @Test
    @DisplayName("Argument bilan: /start ch_abc")
    void parsesCommandWithPayload() {
        assertThat(BotCommand.parse("/start ch_abc")).contains(BotCommand.START);
        assertThat(BotCommand.payload("/start ch_abc")).contains("ch_abc");
    }

    @Test
    @DisplayName("Argument yo'q bo'lsa payload bo'sh")
    void emptyPayload() {
        assertThat(BotCommand.payload("/start")).isEmpty();
        assertThat(BotCommand.payload("/start   ")).isEmpty();
        assertThat(BotCommand.payload(null)).isEmpty();
    }

    @Test
    @DisplayName("Buyruq bo'lmagan matn — bo'sh natija")
    void ignoresPlainText() {
        assertThat(BotCommand.parse("salom")).isEmpty();
        assertThat(BotCommand.parse(null)).isEmpty();
        assertThat(BotCommand.parse("/mavjudemas")).isEmpty();
    }

    @Test
    @DisplayName("Menyu uchun slug @ belgisiz")
    void slugHasNoSlash() {
        assertThat(BotCommand.MY_DATA.slug()).isEqualTo("mening_malumotlarim");
    }
}
