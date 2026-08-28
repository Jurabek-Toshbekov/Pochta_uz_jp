package uz.pochtajp.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uz.pochtajp.service.AdminAuthService;

/**
 * Admin API testlarining poydevori (§11).
 *
 * <p>Har bir testda xodim yaratish va token olish takrorlanmasin uchun
 * shu yerda. Token haqiqiy oqim orqali olinadi (bot kodi -> almashtirish),
 * ya'ni test kirish yo'lini ham har safar tekshiradi.
 */
public abstract class AbstractAdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected AdminAuthService adminAuthService;

    @Autowired
    protected ObjectMapper objectMapper;

    private PostFixtures fixtures;

    protected PostFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PostFixtures(jdbcTemplate);
        }
        return fixtures;
    }

    /** Berilgan rol bilan xodim yaratadi. */
    protected UUID insertStaff(long telegramId, String role) {
        UUID userId = fixtures().insertUser(telegramId, "staff" + telegramId, "NONE", 0);
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, userId);
        return userId;
    }

    /** Xodim yaratib, unga access token oladi. */
    protected String tokenFor(long telegramId, String role) throws Exception {
        return accessToken(insertStaff(telegramId, role));
    }

    protected String accessToken(UUID userId) throws Exception {
        String code = adminAuthService.issueLoginCode(userId);
        String body = mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
