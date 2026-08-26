package uz.pochtajp.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.support.AbstractIntegrationTest;

/**
 * {@code /health} — initData tekshiruvidan ozod bo'lgan yagona endpoint (§1.4, §12).
 */
class HealthEndpointIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("/health autentifikatsiyasiz ishlaydi")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Boshqa aktuator endpoint'lari ochiq emas")
    void otherActuatorEndpointsAreClosed() throws Exception {
        mockMvc.perform(get("/env"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Ro'yxatdan tashqari marshrut — 4xx, ma'lumot oshkor qilinmaydi")
    void unknownRouteIsDenied() throws Exception {
        mockMvc.perform(get("/api/nimadir"))
                .andExpect(status().is4xxClientError());
    }
}
