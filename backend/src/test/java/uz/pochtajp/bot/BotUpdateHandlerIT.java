package uz.pochtajp.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.BotUpdates;
import uz.pochtajp.support.StubBotMessenger;

/**
 * Botning yangi roli (§8): forma to'ldirmaydi, Mini App'ga yo'naltiradi.
 */
class BotUpdateHandlerIT extends AbstractIntegrationTest {

    @Autowired
    private BotUpdateHandler handler;

    private StubBotMessenger.SentMessage last() {
        return botMessenger.lastMessage();
    }

    @Test
    @DisplayName("/start — foydalanuvchi yaratiladi, salom va WebApp tugmalari yuboriladi (§8.2)")
    void startGreetsAndOffersWebApp() {
        handler.handle(BotUpdates.text("/start"));

        StubBotMessenger.SentMessage message = last();
        assertThat(message).isNotNull();
        assertThat(message.chatId()).isEqualTo(BotUpdates.CHAT_ID);
        assertThat(message.text())
                .contains("Assalomu alaykum")
                .contains("Test")
                .contains("/xavfsizlik");

        // Uchta WebApp tugmasi: e'lon berish, qidirish, mening e'lonlarim
        assertThat(message.webAppUrls()).hasSize(3);
        assertThat(message.webAppUrls()).anyMatch(url -> url.endsWith("/#/new"));
        assertThat(message.webAppUrls()).anyMatch(url -> url.endsWith("/#/my"));

        Integer users = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE telegram_id = ?", Integer.class, BotUpdates.TELEGRAM_ID);
        assertThat(users).isEqualTo(1);
    }

    @Test
    @DisplayName("bot_command eventi yoziladi (§6.1)")
    void writesBotCommandEvent() {
        handler.handle(BotUpdates.text("/elon"));

        List<String> names = jdbcTemplate.queryForList(
                "SELECT event_name FROM events", String.class);
        assertThat(names).contains("bot_command");

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'bot_command'", String.class);
        assertThat(properties).contains("/elon");

        String source = jdbcTemplate.queryForObject("SELECT source FROM events", String.class);
        assertThat(source).isEqualTo("BOT");
    }

    @Test
    @DisplayName("/elon — to'g'ridan-to'g'ri e'lon berish ekranini ochadi")
    void newPostCommandOpensFormScreen() {
        handler.handle(BotUpdates.text("/elon"));

        assertThat(last().webAppUrls()).containsExactly("https://app.example.test/#/new");
    }

    @Test
    @DisplayName("Erkin matn — muloyim javob va tugma, hech qanday jerkish (§8.3)")
    void freeTextGetsPoliteReply() {
        handler.handle(BotUpdates.text("Salom, pochta yubormoqchiman"));

        assertThat(last().text())
                .contains("buyruqlar bilan ishlayman")
                .doesNotContain("😡");
        assertThat(last().webAppUrls()).isNotEmpty();
    }

    @Test
    @DisplayName("Rasm yuborilsa — muloyim tushuntirish (§2, 8-nuqson)")
    void photoGetsPoliteReply() {
        handler.handle(BotUpdates.photo());

        assertThat(last().text())
                .contains("ilova orqali")
                .doesNotContain("😡");
    }

    @Test
    @DisplayName("Noma'lum buyruq — yordamga yo'naltiradi")
    void unknownCommandPointsToHelp() {
        handler.handle(BotUpdates.text("/nimadir"));

        assertThat(last().text()).contains("/yordam");
    }

    @Test
    @DisplayName("Guruh xabariga javob bermaydi")
    void ignoresGroupMessages() {
        handler.handle(BotUpdates.groupText("/start"));

        assertThat(botMessenger.messages()).isEmpty();
    }

    @Test
    @DisplayName("/xavfsizlik — taqiqlangan buyumlar ro'yxati (§7.3)")
    void safetyCommandListsProhibited() {
        handler.handle(BotUpdates.text("/xavfsizlik"));

        assertThat(last().text())
                .contains("giyohvand")
                .contains("Yopiq");
    }

    @Test
    @DisplayName("/til — tanlov, tanlangach matnlar rus tiliga o'tadi")
    void languageCommandSwitchesLanguage() {
        handler.handle(BotUpdates.text("/til"));
        assertThat(last().callbackData()).containsExactly("lang:uz", "lang:uz-cyrl", "lang:ru");

        handler.handle(BotUpdates.callback("lang:ru"));

        String stored = jdbcTemplate.queryForObject(
                "SELECT ui_language FROM users WHERE telegram_id = ?", String.class, BotUpdates.TELEGRAM_ID);
        assertThat(stored).isEqualTo("ru");
        assertThat(botMessenger.answeredCallbacks()).contains("cb-1");

        handler.handle(BotUpdates.text("/start"));
        assertThat(last().text()).contains("Здравствуйте");

        List<String> events = jdbcTemplate.queryForList(
                "SELECT event_name FROM events", String.class);
        assertThat(events).contains("language_changed");
    }

    @Test
    @DisplayName("/mening_malumotlarim — eksport JSON fayl bo'lib keladi (§7.2)")
    void exportsUserData() {
        handler.handle(BotUpdates.text("/mening_malumotlarim"));
        assertThat(last().callbackData()).contains("data:export", "data:delete");

        handler.handle(BotUpdates.callback("data:export"));

        assertThat(botMessenger.documents()).hasSize(1);
        StubBotMessenger.SentDocument document = botMessenger.documents().get(0);
        assertThat(document.fileName()).endsWith(".json");

        String json = new String(document.content(), StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"telegramId\"")
                .contains("\"posts\"")
                .contains("\"subscriptions\"");

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'USER_DATA_EXPORT'", Integer.class);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    @DisplayName("O'chirish tasdiq so'raydi; bekor qilinsa hech narsa o'chmaydi")
    void deleteAsksForConfirmation() {
        handler.handle(BotUpdates.text("/start"));
        handler.handle(BotUpdates.callback("data:delete"));

        assertThat(last().text()).contains("tasdiqlaysizmi");
        assertThat(last().callbackData()).contains("data:delete:yes", "data:cancel");

        handler.handle(BotUpdates.callback("data:cancel"));
        assertThat(last().text()).contains("Bekor qilindi");

        String deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at::text FROM users WHERE telegram_id = ?",
                String.class, BotUpdates.TELEGRAM_ID);
        assertThat(deletedAt).isNull();
    }

    @Test
    @DisplayName("Tasdiqlangan o'chirish — PII tozalanadi, qator qoladi (§1.1, §7.2)")
    void deleteRemovesPersonalDataButKeepsRow() {
        handler.handle(BotUpdates.text("/start"));
        handler.handle(BotUpdates.callback("data:delete:yes"));

        assertThat(last().text()).contains("o'chirildi");

        var row = jdbcTemplate.queryForMap(
                "SELECT username, first_name, last_name, phone, deleted_at FROM users WHERE telegram_id = ?",
                BotUpdates.TELEGRAM_ID);
        assertThat(row.get("username")).isNull();
        assertThat(row.get("first_name")).isNull();
        assertThat(row.get("last_name")).isNull();
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'USER_DATA_DELETE'", Integer.class);
        assertThat(audits).isEqualTo(1);

        // Qator o'chirilmagan — analitika buzilmaydi (§1.1)
        Integer users = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE telegram_id = ?", Integer.class, BotUpdates.TELEGRAM_ID);
        assertThat(users).isEqualTo(1);
    }

    @Test
    @DisplayName("O'chirilgandan keyin ism qaytmaydi — /start yana yozib qo'ymaydi")
    void deletedProfileIsNotRestored() {
        handler.handle(BotUpdates.text("/start"));
        handler.handle(BotUpdates.callback("data:delete:yes"));
        handler.handle(BotUpdates.text("/start"));

        String firstName = jdbcTemplate.queryForObject(
                "SELECT first_name FROM users WHERE telegram_id = ?",
                String.class, BotUpdates.TELEGRAM_ID);
        assertThat(firstName).isNull();
    }

    @Test
    @DisplayName("/obuna — hozircha ishlamasligi ochiq aytiladi")
    void subscriptionsCommandIsHonest() {
        handler.handle(BotUpdates.text("/obuna"));

        assertThat(last().text()).contains("tayyorlanmoqda");
    }

    @Test
    @DisplayName("/start argumenti deep_link_open eventiga yoziladi (§6.4, 6-band)")
    void startPayloadIsTracked() {
        handler.handle(BotUpdates.text("/start ch_abc123"));

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'deep_link_open'", String.class);
        assertThat(properties).contains("ch_abc123");
    }

    @Test
    @DisplayName("Bloklangan foydalanuvchiga sabab aytiladi")
    void blockedUserGetsExplanation() {
        handler.handle(BotUpdates.text("/start"));
        jdbcTemplate.update("UPDATE users SET status = 'BLOCKED' WHERE telegram_id = ?",
                BotUpdates.TELEGRAM_ID);
        botMessenger.reset();

        handler.handle(BotUpdates.text("/elon"));

        assertThat(last().text()).contains("bloklangan");
    }
}
