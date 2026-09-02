package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.UserStatus;
import uz.pochtajp.repository.UserRepository;
import uz.pochtajp.security.TelegramInitData;
import uz.pochtajp.security.TelegramInitDataValidator;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * Foydalanuvchi upsert oqimi (§7.1, 7-qadam).
 */
class UserServiceIT extends AbstractIntegrationTest {

    private static final long TELEGRAM_ID = 313_000_777L;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TelegramInitDataValidator validator;

    private TelegramInitData initData(String startParam) {
        String raw = startParam == null
                ? InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID)
                : InitDataFactory.validWithStartParam(TEST_BOT_TOKEN, TELEGRAM_ID, startParam);
        return validator.validate(raw);
    }

    @Test
    @DisplayName("Birinchi kirish — foydalanuvchi yaratiladi, audit maydonlari to'ladi")
    void createsUserOnFirstSession() {
        User user = userService.upsertFromInitData(initData(null)).user();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getTelegramId()).isEqualTo(TELEGRAM_ID);
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getUiLanguage()).isEqualTo("uz");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // @CreatedDate ishlashi — eski koddagi 4-nuqson tuzatilgani (§2)
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getFirstSeenAt()).isNotNull();
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Takroriy kirish — yangi yozuv yaratilmaydi, last_seen yangilanadi")
    void reusesExistingUser() {
        User first = userService.upsertFromInitData(initData(null)).user();
        // Postgres TIMESTAMPTZ mikrosekundda saqlaydi, Instant esa nanosekundda —
        // shuning uchun taqqoslashdan oldin kesib olinadi.
        Instant firstSeen = first.getFirstSeenAt().truncatedTo(ChronoUnit.MILLIS);

        User second = userService.upsertFromInitData(initData(null)).user();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getFirstSeenAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(firstSeen);
        assertThat(second.getLastSeenAt()).isAfterOrEqualTo(firstSeen);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("start_param referral_source ga yoziladi (§6.4, 6-band)")
    void storesReferralSourceOnce() {
        User user = userService.upsertFromInitData(initData("ch_deep_link_1")).user();
        assertThat(user.getReferralSource()).isEqualTo("ch_deep_link_1");

        // Birinchi manba o'zgarmaydi — atributsiya buzilmasligi kerak.
        User again = userService.upsertFromInitData(initData("ch_deep_link_2")).user();
        assertThat(again.getReferralSource()).isEqualTo("ch_deep_link_1");
    }

    @Test
    @DisplayName("BLOCKED foydalanuvchi — 403")
    void rejectsBlockedUser() {
        User user = userService.upsertFromInitData(initData(null)).user();
        user.setStatus(UserStatus.BLOCKED);
        user.setBlockedReason("Test");
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> userService.upsertFromInitData(initData(null)))
                .isInstanceOf(ForbiddenException.class);
    }
}
