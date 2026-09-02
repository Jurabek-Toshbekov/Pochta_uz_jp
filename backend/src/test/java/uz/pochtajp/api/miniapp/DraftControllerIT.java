package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * Draft autosave (§6.4, 5-band): ilova yopilib ochilsa forma o'sha joydan davom etadi.
 */
class DraftControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/miniapp/drafts";
    private static final long TELEGRAM_ID = 828_000_333L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    @Test
    @DisplayName("Draft yo'q — bo'sh javob, xato emas")
    void returnsEmptyDraft() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").doesNotExist())
                .andExpect(jsonPath("$.payload").isMap());
    }

    @Test
    @DisplayName("Saqlanadi va o'qiladi, ikkinchi saqlash yangi yozuv yaratmaydi")
    void savesAndOverwritesSingleDraft() throws Exception {
        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step1_type\",\"payload\":{\"postType\":\"CARRY\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("step1_type"));

        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step2_route\",\"payload\":{\"postType\":\"CARRY\",\"originAirport\":\"NRT\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("step2_route"));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM post_drafts", Integer.class);
        assertThat(count).isEqualTo(1);

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("step2_route"))
                .andExpect(jsonPath("$.payload.originAirport").value("NRT"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("post_draft_saved eventi yoziladi (§1.6)")
    void writesDraftEvent() throws Exception {
        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step3_cargo\",\"payload\":{\"weightKg\":10}}"))
                .andExpect(status().isOk());

        Integer events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'post_draft_saved'", Integer.class);
        assertThat(events).isEqualTo(1);
    }

    @Test
    @DisplayName("Bekor qilinsa o'chadi")
    void discardsDraft() throws Exception {
        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step1_type\",\"payload\":{\"postType\":\"SEND\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM post_drafts", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("payload majburiy — 400")
    void requiresPayload() throws Exception {
        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step1_type\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.payload").exists());
    }

    @Test
    @DisplayName("Draft boshqa foydalanuvchiga ko'rinmaydi")
    void draftIsPrivate() throws Exception {
        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":\"step1_type\",\"payload\":{\"postType\":\"SEND\"}}"))
                .andExpect(status().isOk());

        String otherAuth = "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID + 1);
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").doesNotExist());
    }
}
