package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * {@code /api/miniapp/me} — profil ekrani (§9.1, §12).
 *
 * <p>Asosiy tekshiruv: foydalanuvchi faqat o'ziga tegishli ikki maydonni
 * (til, telefon) o'zgartira oladi. Rol va ishonch balli so'rov orqali
 * ko'tarilmasligi kerak — bu huquq oshirish yo'li bo'lardi.
 */
class ProfileControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/miniapp/me";
    private static final long TELEGRAM_ID = 424_000_333L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    private ResultActions patchMe(String body) throws Exception {
        return mockMvc.perform(patch(PATH)
                .header(HttpHeaders.AUTHORIZATION, auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Profil statistikasi bo'sh bo'lmasligi uchun bitta e'lon. */
    private void createPost() throws Exception {
        String departDate = DateTimeFormatter.ISO_LOCAL_DATE
                .format(LocalDate.now(ZoneOffset.UTC).plusDays(7));
        String body = """
                {
                  "postType": "CARRY",
                  "direction": "JP_UZ",
                  "originAirport": "NRT",
                  "destAirport": "TAS",
                  "departDate": "%s",
                  "priceAmount": 2000,
                  "priceCurrency": "JPY",
                  "priceUnit": "PER_KG",
                  "categoryIds": [1],
                  "contactTelegram": "@testuser",
                  "safetyChecklistOk": true
                }""".formatted(departDate);

        mockMvc.perform(post("/api/miniapp/posts")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("initData'siz — 401 (§1.4)")
    void rejectsWithoutInitData() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me — Telegram ma'lumoti va standart qiymatlar")
    void returnsProfile() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramId").value(TELEGRAM_ID))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.uiLanguage").value("uz"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.verificationLevel").value("NONE"))
                .andExpect(jsonPath("$.trustScore").value(0))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.phoneVerified").value(false))
                .andExpect(jsonPath("$.postCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    @Test
    @DisplayName("GET /me — e'lon statistikasi hisoblanadi")
    void countsPosts() throws Exception {
        createPost();

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.activePostCount").value(1))
                .andExpect(jsonPath("$.dealCount").value(0))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    @DisplayName("Til o'zgarsa language_changed eventi yoziladi (§6.1)")
    void changesLanguageAndTracksEvent() throws Exception {
        patchMe("{\"uiLanguage\": \"ru\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiLanguage").value("ru"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT ui_language FROM users", String.class)).isEqualTo("ru");

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'language_changed'",
                String.class);
        assertThat(properties).contains("\"from\"").contains("\"to\"").contains("ru");
    }

    @Test
    @DisplayName("Bir xil til qayta yuborilsa — event yozilmaydi")
    void sameLanguageWritesNoEvent() throws Exception {
        patchMe("{\"uiLanguage\": \"uz\"}").andExpect(status().isOk());

        Integer events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'language_changed'", Integer.class);
        assertThat(events).isZero();
    }

    @Test
    @DisplayName("Qo'llab-quvvatlanmaydigan til — 400")
    void rejectsUnknownLanguage() throws Exception {
        patchMe("{\"uiLanguage\": \"en\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.uiLanguage").exists());
    }

    @Test
    @DisplayName("Telefon o'zgarsa tasdiqlanish bekor bo'ladi")
    void phoneChangeResetsVerification() throws Exception {
        jdbcTemplate.update("UPDATE users SET phone = ?, phone_verified_at = now()", "+998901112233");

        patchMe("{\"phone\": \"+998907776655\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+998907776655"))
                .andExpect(jsonPath("$.phoneVerified").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone_verified_at FROM users", java.sql.Timestamp.class)).isNull();
    }

    @Test
    @DisplayName("Bo'sh satr — telefon o'chiriladi (§7.2 maxfiylik)")
    void emptyPhoneClearsIt() throws Exception {
        patchMe("{\"phone\": \"+998901112233\"}").andExpect(status().isOk());

        patchMe("{\"phone\": \"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone FROM users", String.class)).isNull();
    }

    @Test
    @DisplayName("Noto'g'ri telefon — 400")
    void rejectsInvalidPhone() throws Exception {
        patchMe("{\"phone\": \"telefon emas\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }

    @Test
    @DisplayName("Rol va ishonch balli so'rov orqali o'zgarmaydi")
    void ignoresPrivilegeFields() throws Exception {
        patchMe("{\"uiLanguage\": \"ru\", \"role\": \"ADMIN\", \"trustScore\": 999}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.trustScore").value(0));

        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users", String.class))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("GET /me/export — JSON fayl qaytadi (§7.2)")
    void exportsUserData() throws Exception {
        createPost();

        mockMvc.perform(get(PATH + "/export").header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("pochta-malumotlarim.json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.profile").exists())
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.exportedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM audit_log", String.class)).contains("USER_DATA_EXPORT");
    }
}
