package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * Saqlangan qidiruv → obuna (§10.3).
 */
class SubscriptionControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/miniapp/subscriptions";
    private static final long TELEGRAM_ID = 606_000_222L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("Obuna yaratiladi, ro'yxatda ko'rinadi, search_saved eventi yoziladi")
    void createsSubscription() throws Exception {
        create("""
                {"postType":"CARRY","direction":"JP_UZ","originAirport":"NRT","destAirport":"TAS",
                 "categoryIds":[1,2],"platform":"ios"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.postType").value("CARRY"))
                .andExpect(jsonPath("$.originAirport").value("NRT"))
                .andExpect(jsonPath("$.categoryIds.length()").value(2));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        List<String> names = jdbcTemplate.queryForList("SELECT event_name FROM events", String.class);
        assertThat(names).contains("search_saved");
    }

    @Test
    @DisplayName("Aeroport kodi kichik harfda kelsa katta harfga o'giriladi")
    void normalizesAirportCode() throws Exception {
        create("{\"originAirport\":\"NRT\",\"destAirport\":\"TAS\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destAirport").value("TAS"));
    }

    @Test
    @DisplayName("Bo'sh obuna rad etiladi — aks holda hamma e'lon spam bo'lib keladi")
    void rejectsEmptySubscription() throws Exception {
        create("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.filters").exists());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_subscriptions", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("Noto'g'ri aeroport kodi — 400")
    void rejectsBadAirportCode() throws Exception {
        create("{\"originAirport\":\"narita\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originAirport").exists());
    }

    @Test
    @DisplayName("10 tadan ko'p obuna bo'lmaydi (§10.3 anti-spam)")
    void enforcesSubscriptionLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            create("{\"direction\":\"JP_UZ\",\"originAirport\":\"NRT\"}")
                    .andExpect(status().isCreated());
        }
        create("{\"direction\":\"JP_UZ\",\"originAirport\":\"NRT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subscriptions").exists());
    }

    @Test
    @DisplayName("O'chirish — soft delete, qator qoladi (§1.1)")
    void deleteIsSoft() throws Exception {
        String response = create("{\"direction\":\"JP_UZ\"}").andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");

        mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(jsonPath("$.length()").value(0));

        var row = jdbcTemplate.queryForMap(
                "SELECT is_active, deleted_at FROM notification_subscriptions WHERE id = ?::uuid", id);
        assertThat(row.get("is_active")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
    }

    @Test
    @DisplayName("Begonaning obunasi — 404, mavjudligi oshkor qilinmaydi")
    void cannotDeleteForeignSubscription() throws Exception {
        String response = create("{\"direction\":\"JP_UZ\"}").andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");

        String otherAuth = "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID + 1);
        mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Obunalar boshqa foydalanuvchiga ko'rinmaydi")
    void subscriptionsArePrivate() throws Exception {
        create("{\"direction\":\"JP_UZ\"}").andExpect(status().isCreated());

        String otherAuth = "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID + 2);
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
