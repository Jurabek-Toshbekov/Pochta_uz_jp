package uz.pochtajp.support;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integratsiya testlarining poydevori (§14 — har bir endpoint'ga MockMvc testi,
 * Testcontainers postgres bilan).
 *
 * <p>Konteyner singleton: bir marta ko'tariladi va barcha test klasslari uchun
 * qayta ishlatiladi (JVM tugaganda Testcontainers Ryuk uni o'zi tozalaydi).
 *
 * <p>Sxema Flyway orqali quriladi va {@code ddl-auto=validate} entity'larni
 * shu sxemaga solishtiradi — ya'ni kontekst ko'tarilishi allaqachon migratsiya
 * va mapping to'g'riligini tekshiradi (§2, 3-nuqson).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AbstractIntegrationTest.SyncAsyncConfig.class, StubChannelPublisher.Config.class,
        StubBotMessenger.Config.class})
public abstract class AbstractIntegrationTest {

    /** Testdagi soxta bot tokeni. Real token hech qachon testda ishlatilmaydi (§1.2). */
    public static final String TEST_BOT_TOKEN = "1234567890:TEST-ONLY-DO-NOT-USE-abcdefghijklmno";

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("pochta_test")
                    .withUsername("pochta")
                    .withPassword("test-only");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StubChannelPublisher channelPublisher;

    @Autowired
    protected StubBotMessenger botMessenger;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("bot.token", () -> TEST_BOT_TOKEN);
        registry.add("bot.username", () -> "test_bot");
        registry.add("bot.init-data-max-age-seconds", () -> 86_400);
        registry.add("app.rate-limit-requests-per-minute", () -> 60);
        registry.add("app.admin-telegram-ids", () -> "");
        registry.add("bot.channel-chat-id", () -> "-1001111111111");
        registry.add("bot.channel-username", () -> "jpuzbpochta_test");
        registry.add("bot.miniapp-url", () -> "https://app.example.test");
        // Testlarda bot Telegram'ga ulanmaydi.
        registry.add("bot.mode", () -> "off");
        registry.add("bot.webhook-secret-token", () -> "test-webhook-secret");
    }

    /**
     * Test'lar ichida foydalanuvchi ma'lumoti oqib ketmasligi uchun har bir
     * testdan oldin tranzaksion jadvallar tozalanadi.
     *
     * <p>Bu — TEST bazasi. Prod'da {@code DELETE} taqiqlangan (§1.1), shu sabab
     * bu metod faqat {@code src/test} ichida turadi.
     */
    @BeforeEach
    void resetState() {
        channelPublisher.reset();
        botMessenger.reset();
        jdbcTemplate.execute("""
                TRUNCATE TABLE events, search_queries, contact_reveals, notifications_sent,
                    notification_subscriptions, moderation_actions, reports, reviews,
                    post_categories, post_drafts, posts, audit_log, users RESTART IDENTITY CASCADE
                """);
    }

    /** {@code @Async} ni sinxronga aylantiradi — event yozilishini kutish kerak bo'lmaydi. */
    @TestConfiguration
    public static class SyncAsyncConfig implements AsyncConfigurer {

        @Override
        public Executor getAsyncExecutor() {
            return new SyncTaskExecutor();
        }
    }
}
