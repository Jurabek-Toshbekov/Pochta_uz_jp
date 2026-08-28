package uz.pochtajp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.config.AppProperties;
import uz.pochtajp.domain.enums.AdminTokenType;
import uz.pochtajp.domain.enums.UserRole;

/**
 * Admin tokenlari (§11.1).
 *
 * <p>{@link uz.pochtajp.security.TelegramInitDataValidator} testi bilan bir
 * xil ruhda: to'g'ri imzo, buzilgan imzo, noto'g'ri tur va sozlanmagan sir.
 */
class AdminJwtServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static AdminJwtService service(String secret) {
        return new AdminJwtService(new AppProperties(secret, List.of(), null, 5, 60));
    }

    private static AdminPrincipal admin() {
        return new AdminPrincipal(USER_ID, 900000042L, UserRole.ADMIN);
    }

    @Test
    @DisplayName("Access token yasaladi va o'qiladi")
    void issuesAndParsesAccessToken() {
        AdminJwtService service = service("secret-for-test");

        String token = service.issueAccessToken(admin());
        AdminPrincipal parsed = service.parse(token, AdminTokenType.ACCESS);

        assertThat(parsed.userId()).isEqualTo(USER_ID);
        assertThat(parsed.telegramId()).isEqualTo(900000042L);
        assertThat(parsed.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Refresh tokenni access sifatida ishlatib bo'lmaydi")
    void refreshTokenIsNotAccepted() {
        AdminJwtService service = service("secret-for-test");
        String refresh = service.issueRefreshToken(admin());

        assertThatThrownBy(() -> service.parse(refresh, AdminTokenType.ACCESS))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Boshqa sir bilan imzolangan token rad etiladi")
    void rejectsTokenSignedWithAnotherSecret() {
        String token = service("secret-one").issueAccessToken(admin());
        AdminJwtService other = service("secret-two");

        assertThatThrownBy(() -> other.parse(token, AdminTokenType.ACCESS))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Buzilgan token rad etiladi")
    void rejectsTamperedToken() {
        AdminJwtService service = service("secret-for-test");
        String token = service.issueAccessToken(admin());
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThatThrownBy(() -> service.parse(tampered, AdminTokenType.ACCESS))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("ADMIN_JWT_SECRET yo'q bo'lsa xizmat o'chiq: token berilmaydi ham, qabul ham qilinmaydi")
    void disabledWithoutSecret() {
        AdminJwtService service = service("  ");

        assertThat(service.isEnabled()).isFalse();
        assertThatThrownBy(() -> service.issueAccessToken(admin()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.parse("anything", AdminTokenType.ACCESS))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Sir uzunligi ixtiyoriy — kalit SHA-256 bilan hosil qilinadi")
    void shortSecretStillWorks() {
        AdminJwtService service = service("qisqa");

        String token = service.issueAccessToken(admin());

        assertThat(service.parse(token, AdminTokenType.ACCESS).userId()).isEqualTo(USER_ID);
    }
}
