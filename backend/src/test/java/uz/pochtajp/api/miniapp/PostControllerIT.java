package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * {@code POST /api/miniapp/posts} — 4 qadamli formaning natijasi (§9.2)
 * va kanalga publish (§8.4).
 */
class PostControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/miniapp/posts";
    private static final long TELEGRAM_ID = 424_000_111L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    private String tomorrow() {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC).plusDays(1));
    }

    /** CARRY / JP→UZ / NRT→TAS, kg uchun narx — eng tipik e'lon. */
    private String validCarryBody() {
        return """
                {
                  "postType": "CARRY",
                  "direction": "JP_UZ",
                  "originAirport": "NRT",
                  "destAirport": "TAS",
                  "finalDestination": "Samarqand",
                  "departDate": "%s",
                  "dateFlexibleDays": 3,
                  "weightKg": 5,
                  "weightKgMax": 20,
                  "priceAmount": 2000,
                  "priceCurrency": "JPY",
                  "priceUnit": "PER_KG",
                  "categoryIds": [1, 2],
                  "comment": "Hujjat va kiyim olib ketaman",
                  "contactTelegram": "@testuser",
                  "contactPhone": "+998901234567",
                  "safetyChecklistOk": true,
                  "platform": "ios"
                }""".formatted(tomorrow());
    }

    private ResultActions submit(String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("initData'siz — 401 (§1.4)")
    void rejectsWithoutInitData() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCarryBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Yaroqli e'lon — 201, kanalga chiqadi, PUBLISHED bo'ladi")
    void createsAndPublishesPost() throws Exception {
        submit(validCarryBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.channelMessageId").isNumber())
                .andExpect(jsonPath("$.channelUrl").value(org.hamcrest.Matchers.containsString("jpuzbpochta_test")))
                .andExpect(jsonPath("$.deepLink").value(org.hamcrest.Matchers.containsString("startapp=ch_")))
                .andExpect(jsonPath("$.categoryIds.length()").value(2))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        Integer posts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE status = 'PUBLISHED'", Integer.class);
        assertThat(posts).isEqualTo(1);

        Integer links = jdbcTemplate.queryForObject("SELECT count(*) FROM post_categories", Integer.class);
        assertThat(links).isEqualTo(2);

        // Kanal matni: kontakt YO'Q (§8.4), yo'nalish va hashtag bor
        String sent = channelPublisher.lastMessage();
        assertThat(sent)
                .contains("NRT → TAS")
                .contains("Tokio → Toshkent")
                .contains("Samarqand")
                .contains("±3 kun")
                .contains("2000 JPY / kg")
                .contains("#JP_UZ")
                .contains("#NRT_TAS")
                .contains("startapp=ch_")
                .doesNotContain("testuser");
    }

    @Test
    @DisplayName("post_submit va post_publish_success event'lari yoziladi (§1.6)")
    void writesFunnelEvents() throws Exception {
        submit(validCarryBody()).andExpect(status().isCreated());

        java.util.List<String> names = jdbcTemplate.queryForList(
                "SELECT event_name FROM events ORDER BY id", String.class);
        assertThat(names).contains("post_submit", "post_publish_success");

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'post_submit'", String.class);
        // JSONB kalit tartibi va bo'shliqlari Postgres ixtiyorida — qiymatga qaraymiz.
        assertThat(properties)
                .contains("NRT_TAS")
                .contains("days_until_departure")
                .contains("DOCUMENTS")
                .contains("PER_KG");
    }

    @Test
    @DisplayName("Xavfsizlik checklist'i belgilanmagan — 400, e'lon yaratilmaydi (§7.3)")
    void rejectsWithoutSafetyChecklist() throws Exception {
        submit(validCarryBody().replace("\"safetyChecklistOk\": true", "\"safetyChecklistOk\": false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.safetyChecklistOk").exists());

        Integer posts = jdbcTemplate.queryForObject("SELECT count(*) FROM posts", Integer.class);
        assertThat(posts).isZero();
    }

    @Test
    @DisplayName("O'tib ketgan sana — 400, matn nima qilishni aytadi (§9.4)")
    void rejectsPastDate() throws Exception {
        String yesterday = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC).minusDays(1));

        submit(validCarryBody().replace(tomorrow(), yesterday))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.departDate")
                        .value(org.hamcrest.Matchers.containsString("Bugundan keyingi kunni tanlang")));
    }

    @Test
    @DisplayName("Yo'nalishga mos kelmaydigan aeroport — 400 (§6.3 ma'lumot sifati)")
    void rejectsAirportDirectionMismatch() throws Exception {
        submit(validCarryBody().replace("\"originAirport\": \"NRT\"", "\"originAirport\": \"TAS\"")
                        .replace("\"destAirport\": \"TAS\"", "\"destAirport\": \"NRT\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originAirport").exists());
    }

    @Test
    @DisplayName("Kelishamiz — summa saqlanmaydi, narx indeksi buzilmaydi (§6.3)")
    void negotiablePriceDropsAmount() throws Exception {
        submit(validCarryBody()
                .replace("\"priceUnit\": \"PER_KG\"", "\"priceUnit\": \"NEGOTIABLE\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceAmount").doesNotExist())
                .andExpect(jsonPath("$.priceCurrency").doesNotExist());

        assertThat(channelPublisher.lastMessage()).contains("kelishamiz");
    }

    @Test
    @DisplayName("Narx birligi TOTAL, lekin summa yo'q — 400 (§6.4, 8-band)")
    void requiresAmountWhenNotNegotiable() throws Exception {
        submit(validCarryBody()
                .replace("\"priceUnit\": \"PER_KG\"", "\"priceUnit\": \"TOTAL\"")
                .replace("\"priceAmount\": 2000,", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceAmount").exists());
    }

    @Test
    @DisplayName("Telegram username yo'q — 400 (ikkalasi majburiy)")
    void requiresTelegramUsername() throws Exception {
        submit(validCarryBody().replace("\"contactTelegram\": \"@testuser\",", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactTelegram").exists());
    }

    /**
     * Telefon ham majburiy: bitta kanal ishlamay qolsa odam ikkinchisidan
     * yozadi. Bitta kontakt qolsa e'lon ko'rinadi-yu bitim boshlanmaydi.
     */
    @Test
    @DisplayName("Telefon yo'q — 400 (ikkalasi majburiy)")
    void requiresPhone() throws Exception {
        submit(validCarryBody().replace("\"contactPhone\": \"+998901234567\",", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists());
    }

    @Test
    @DisplayName("Ikkalasi ham yo'q — ikkala maydon xatosi qaytadi")
    void reportsBothMissingContacts() throws Exception {
        submit(validCarryBody()
                .replace("\"contactTelegram\": \"@testuser\",", "")
                .replace("\"contactPhone\": \"+998901234567\",", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactTelegram").exists())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists());
    }

    @Test
    @DisplayName("Ro'yxatda yo'q kategoriya — 400")
    void rejectsUnknownCategory() throws Exception {
        submit(validCarryBody().replace("\"categoryIds\": [1, 2]", "\"categoryIds\": [999]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryIds").exists());
    }

    @Test
    @DisplayName("Yakuniy manzil kelish shahri bilan bir xil bo'lsa takrorlanmaydi")
    void doesNotRepeatFinalDestination() throws Exception {
        submit(validCarryBody().replace("\"finalDestination\": \"Samarqand\"", "\"finalDestination\": \"toshkent\""))
                .andExpect(status().isCreated());

        String sent = channelPublisher.lastMessage();
        assertThat(sent).contains("Tokio → Toshkent");
        // "Tokio → Toshkent → toshkent" bo'lmasligi kerak
        assertThat(sent).doesNotContain("Toshkent → toshkent");
    }

    @Test
    @DisplayName("Yakuniy manzil boshqa tilda yozilsa ham takrorlanmaydi")
    void doesNotRepeatFinalDestinationInAnotherLanguage() throws Exception {
        // Foydalanuvchi rus tilida yozadi, kanalda o'zbekcha nom chiqadi.
        submit(validCarryBody().replace("\"finalDestination\": \"Samarqand\"", "\"finalDestination\": \"Ташкент\""))
                .andExpect(status().isCreated());

        assertThat(channelPublisher.lastMessage()).doesNotContain("Ташкент");
    }

    @Test
    @DisplayName("Boshqa yakuniy manzil ko'rsatiladi")
    void showsDifferentFinalDestination() throws Exception {
        submit(validCarryBody()).andExpect(status().isCreated());

        assertThat(channelPublisher.lastMessage()).contains("→ Samarqand");
    }

    @Test
    @DisplayName("Izohdagi HTML kanalga xom ketmaydi (§7.2)")
    void escapesHtmlInComment() throws Exception {
        submit(validCarryBody().replace("Hujjat va kiyim olib ketaman",
                        "<b>arzon</b> & <a href='http://x'>bosing</a>"))
                .andExpect(status().isCreated());

        String sent = channelPublisher.lastMessage();
        assertThat(sent)
                .contains("&lt;b&gt;arzon&lt;/b&gt; &amp;")
                .doesNotContain("<a href");
    }

    @Test
    @DisplayName("Kanal xatosi — e'lon o'chirilmaydi, PENDING qoladi (§1.1)")
    void keepsPostWhenChannelFails() throws Exception {
        channelPublisher.failNextRequests();

        submit(validCarryBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.channelMessageId").doesNotExist());

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE status = 'PENDING'", Integer.class);
        assertThat(pending).isEqualTo(1);

        java.util.List<String> names = jdbcTemplate.queryForList(
                "SELECT event_name FROM events", String.class);
        assertThat(names).contains("post_publish_fail");
    }

    @Test
    @DisplayName("Kunlik chegara oshsa — 429 (§7.2)")
    void enforcesDailyPostLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            submit(validCarryBody()).andExpect(status().isCreated());
        }
        submit(validCarryBody())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("5")));

        java.util.List<String> names = jdbcTemplate.queryForList(
                "SELECT event_name FROM events", String.class);
        assertThat(names).contains("rate_limit_hit");
    }

    @Test
    @DisplayName("SEND e'lon uchun deadline talab qilinadi, depart_date yozilmaydi")
    void sendPostUsesDeadlineDate() throws Exception {
        String body = validCarryBody()
                .replace("\"postType\": \"CARRY\"", "\"postType\": \"SEND\"")
                .replace("\"departDate\"", "\"deadlineDate\"");

        submit(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deadlineDate").value(tomorrow()))
                .andExpect(jsonPath("$.departDate").doesNotExist());

        assertThat(channelPublisher.lastMessage()).contains("gacha");
    }

    @Test
    @DisplayName("Mening e'lonlarim — faqat o'zining e'lonlari")
    void listsOwnPosts() throws Exception {
        submit(validCarryBody()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/miniapp/my/posts").header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contactTelegram").value("testuser"));

        // Boshqa foydalanuvchi hech narsa ko'rmaydi
        String otherAuth = "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID + 1);
        mockMvc.perform(get("/api/miniapp/my/posts").header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Begonaning e'loni — 404, mavjudligi oshkor qilinmaydi")
    void hidesOtherUsersPost() throws Exception {
        String response = submit(validCarryBody()).andReturn().getResponse().getContentAsString();
        String postId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        String otherAuth = "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID + 2);
        mockMvc.perform(get("/api/miniapp/my/posts/" + postId).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Publish bo'lgach draft tozalanadi (§6.4, 5-band)")
    void clearsDraftAfterPublish() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/miniapp/drafts")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step3_cargo\",\"payload\":{\"weightKg\":5}}"))
                .andExpect(status().isOk());

        submit(validCarryBody()).andExpect(status().isCreated());

        Integer drafts = jdbcTemplate.queryForObject("SELECT count(*) FROM post_drafts", Integer.class);
        assertThat(drafts).isZero();
    }
}
