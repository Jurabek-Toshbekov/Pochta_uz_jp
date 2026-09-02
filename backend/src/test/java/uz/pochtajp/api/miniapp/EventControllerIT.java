package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * {@code POST /api/miniapp/events} — §6.2 va §7.1 talablari.
 */
class EventControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/miniapp/events";
    private static final long TELEGRAM_ID = 777_000_555L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    @Test
    @DisplayName("initData'siz so'rov — 401 (§1.4)")
    void rejectsRequestWithoutInitData() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"name\":\"app_open\"}]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Buzilgan imzo — 401")
    void rejectsTamperedInitData() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "tma " + InitDataFactory.tampered(TEST_BOT_TOKEN, TELEGRAM_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"name\":\"app_open\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Yaroqli initData — event yoziladi, foydalanuvchi yaratiladi")
    void storesEventAndCreatesUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String body = """
                {"events":[
                  {"name":"app_open","sessionId":"%s","platform":"ios",
                   "properties":{"start_param":"ch_1","is_first_open":true}},
                  {"name":"post_form_open","sessionId":"%s","properties":{"entry_point":"bot_button"}}
                ]}""".formatted(sessionId, sessionId);

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        Integer users = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE telegram_id = ?", Integer.class, TELEGRAM_ID);
        assertThat(users).isEqualTo(1);

        Integer events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE session_id = ?", Integer.class, sessionId);
        assertThat(events).isEqualTo(2);

        String platform = jdbcTemplate.queryForObject(
                "SELECT platform FROM events WHERE event_name = 'app_open'", String.class);
        assertThat(platform).isEqualTo("ios");

        String source = jdbcTemplate.queryForObject(
                "SELECT DISTINCT source FROM events", String.class);
        assertThat(source).isEqualTo("MINIAPP");

        // user_id body'dan emas, initData'dan olinadi (§7.1)
        Integer linked = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM events e JOIN users u ON u.id = e.user_id
                WHERE u.telegram_id = ?""", Integer.class, TELEGRAM_ID);
        assertThat(linked).isEqualTo(2);
    }

    @Test
    @DisplayName("Ro'yxatda yo'q event nomi tashlab yuboriladi, batch yiqilmaydi")
    void dropsUnknownEventNames() throws Exception {
        String body = """
                {"events":[
                  {"name":"app_open"},
                  {"name":"xakerlik_urinishi"},
                  {"name":"post_publish_success"}
                ]}""";

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        // post_publish_success — faqat server yozadi, klientdan qabul qilinmaydi.
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class);
        assertThat(count).isEqualTo(1);

        String name = jdbcTemplate.queryForObject("SELECT event_name FROM events", String.class);
        assertThat(name).isEqualTo("app_open");
    }

    @Test
    @DisplayName("PII kalitlari saqlanmaydi (§1.7)")
    void stripsPiiFromProperties() throws Exception {
        String body = """
                {"events":[{"name":"app_open","properties":{
                   "phone":"+998901234567","first_name":"Ali","platform_version":"7.2"}}]}""";

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events", String.class);
        assertThat(properties)
                .doesNotContain("phone")
                .doesNotContain("first_name")
                .contains("platform_version");
    }

    @Test
    @DisplayName("Bo'sh ro'yxat — 400, maydon nomi bilan")
    void rejectsEmptyBatch() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.events").exists());
    }

    @Test
    @DisplayName("Buzilgan JSON — 400, tafsilotsiz")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
