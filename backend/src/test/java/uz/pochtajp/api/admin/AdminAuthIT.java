package uz.pochtajp.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uz.pochtajp.service.AdminAuthService;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.PostFixtures;

/**
 * Admin panelga kirish oqimi (§11.1, §12).
 *
 * <p>Tekshiriladigan qoidalar: tokensiz kirish yo'q, kod bir marta ishlaydi,
 * huquqsiz odam kod ololmaydi, refresh access o'rnida ishlamaydi.
 */
class AdminAuthIT extends AbstractIntegrationTest {

    @Autowired
    private AdminAuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    private PostFixtures fixtures;

    private PostFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PostFixtures(jdbcTemplate);
        }
        return fixtures;
    }

    private UUID insertStaff(long telegramId, String role) {
        UUID userId = fixtures().insertUser(telegramId, "staff" + telegramId, "NONE", 0);
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, userId);
        return userId;
    }

    private String login(UUID userId) throws Exception {
        String code = authService.issueLoginCode(userId);
        String body = mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    @DisplayName("Tokensiz admin endpoint 401 qaytaradi")
    void rejectsRequestWithoutToken() throws Exception {
        // 401, 403 emas: kim ekanligi umuman aniqlanmagan. Panel shu farqqa
        // qarab token yangilaydi yoki kirish ekraniga qaytaradi.
        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Buzilgan token 401 qaytaradi")
    void rejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer soxta.token.qiymat"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bot bergan kod token juftligiga almashadi")
    void exchangesCodeForTokens() throws Exception {
        UUID adminId = insertStaff(900001L, "ADMIN");
        String code = authService.issueLoginCode(adminId);

        mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.userId").value(adminId.toString()));
    }

    @Test
    @DisplayName("Kod faqat bir marta ishlaydi")
    void codeWorksOnlyOnce() throws Exception {
        UUID adminId = insertStaff(900002L, "ADMIN");
        String code = authService.issueLoginCode(adminId);

        mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Noto'g'ri kod qabul qilinmaydi")
    void rejectsWrongCode() throws Exception {
        mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZZZZZZZ\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Oddiy foydalanuvchi kod ololmaydi")
    void plainUserCannotGetCode() {
        UUID userId = fixtures().insertUser(900003L, "oddiy", "NONE", 0);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> authService.issueLoginCode(userId))
                .isInstanceOf(uz.pochtajp.common.exception.ForbiddenException.class);
    }

    @Test
    @DisplayName("Kod bazada ochiq matnda saqlanmaydi")
    void codeIsStoredHashedOnly() {
        UUID adminId = insertStaff(900004L, "ADMIN");
        String code = authService.issueLoginCode(adminId);

        String hash = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM admin_login_codes WHERE user_id = ?", String.class, adminId);

        assertThat(hash).isNotNull().hasSize(64).isNotEqualTo(code);
    }

    @Test
    @DisplayName("Token bilan overview ochiladi")
    void opensOverviewWithToken() throws Exception {
        UUID adminId = insertStaff(900005L, "ADMIN");
        String token = login(adminId);

        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postsToday").exists())
                .andExpect(jsonPath("$.openReports").exists());
    }

    @Test
    @DisplayName("Refresh token bilan yangi juftlik olinadi")
    void refreshesTokens() throws Exception {
        UUID adminId = insertStaff(900006L, "ADMIN");
        String code = authService.issueLoginCode(adminId);
        String body = mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + json.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Refresh tokenni Authorization sifatida ishlatib bo'lmaydi")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        UUID adminId = insertStaff(900007L, "ADMIN");
        String code = authService.issueLoginCode(adminId);
        String body = mockMvc.perform(post("/api/admin/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(body).get("refreshToken").asText();

        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Huquq bekor qilinsa token ham ishlamay qoladi")
    void tokenStopsWorkingWhenRoleRevoked() throws Exception {
        UUID adminId = insertStaff(900008L, "ADMIN");
        String token = login(adminId);

        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", adminId);

        // Bu yerda aynan 403: token haqiqiy, kim ekanligi ma'lum — huquq yo'q.
        // Token yangilash yordam bermaydi, shuning uchun panel darhol chiqaradi.
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
