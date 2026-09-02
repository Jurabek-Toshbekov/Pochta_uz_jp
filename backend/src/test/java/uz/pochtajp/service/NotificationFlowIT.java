package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.pochtajp.domain.enums.NotificationKind;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.PostFixtures;

/**
 * Xabarnoma oqimi (§10.3).
 *
 * <p>Uchta anti-spam qoidasi tekshiriladi: takrorlanmaslik, birlashtirish
 * va kunlik chegara. Ular buzilsa foydalanuvchi obunani o'chiradi va
 * mahsulotning eng qimmatli kanali yopiladi.
 */
class NotificationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    private PostFixtures fixtures;

    private PostFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PostFixtures(jdbcTemplate);
        }
        return fixtures;
    }

    /** Obuna: JP→UZ yo'nalishidagi CARRY e'lonlar. */
    private UUID subscribe(UUID userId, String direction, String postType, String origin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO notification_subscriptions
                    (id, user_id, post_type, direction, origin_airport, is_active)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """, id, userId, postType, direction, origin);
        return id;
    }

    private UUID publishedPost(UUID ownerId) {
        return fixtures().post(ownerId)
                .type("CARRY")
                .direction("JP_UZ")
                .route("NRT", "TAS")
                .departDate(LocalDate.now().plusDays(7))
                .status("PUBLISHED")
                .insert();
    }

    private long pendingCount() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications_sent WHERE status = 'PENDING'", Long.class);
        return value == null ? 0 : value;
    }

    @Test
    @DisplayName("Mos obuna navbatga tushadi")
    void matchingSubscriptionIsQueued() {
        UUID owner = fixtures().insertUser(830001L, "egasi", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830002L, "obunachi", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", "NRT");

        UUID postId = publishedPost(owner);
        notificationService.enqueueMatches(postId);

        assertThat(pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("E'lon egasining o'ziga xabar ketmaydi")
    void ownerIsNotNotifiedAboutOwnPost() {
        UUID owner = fixtures().insertUser(830003L, "egasi2", "NONE", 0);
        subscribe(owner, "JP_UZ", "CARRY", "NRT");

        notificationService.enqueueMatches(publishedPost(owner));

        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("Mos kelmaydigan obuna chetlab o'tiladi")
    void nonMatchingSubscriptionIsSkipped() {
        UUID owner = fixtures().insertUser(830004L, "egasi3", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830005L, "obunachi2", "NONE", 0);
        subscribe(subscriber, "UZ_JP", "CARRY", null);

        notificationService.enqueueMatches(publishedPost(owner));

        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("Bir e'lon ikki marta navbatga tushmaydi")
    void duplicateEnqueueIsIgnored() {
        UUID owner = fixtures().insertUser(830006L, "egasi4", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830007L, "obunachi3", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        UUID postId = publishedPost(owner);
        notificationService.enqueueMatches(postId);
        notificationService.enqueueMatches(postId);

        assertThat(pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bitta e'lon uchun tafsilotli xabar va e'lonni ochadigan tugma")
    void singleMatchSendsDetailedMessage() {
        UUID owner = fixtures().insertUser(830008L, "egasi5", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830009L, "obunachi4", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        UUID postId = publishedPost(owner);
        notificationService.enqueueMatches(postId);
        notificationService.flushQueue();

        assertThat(botMessenger.messages()).hasSize(1);
        var message = botMessenger.lastMessage();
        assertThat(message.chatId()).isEqualTo(830009L);
        assertThat(message.text()).contains("NRT").contains("TAS");
        // Havola `startapp=nt_` bo'lishi shart — aks holda ochilish
        // (`notification_opened`) yozilmaydi va CTR o'lchanmaydi (§10.3).
        assertThat(message.buttonUrls()).anyMatch(url -> url.contains("startapp=nt_" + postId));
    }

    @Test
    @DisplayName("Bir nechta e'lon BITTA xabarga birlashadi (§10.3)")
    void severalMatchesAreMergedIntoOneMessage() {
        UUID owner = fixtures().insertUser(830010L, "egasi6", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830011L, "obunachi5", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        notificationService.enqueueMatches(publishedPost(owner));
        notificationService.enqueueMatches(publishedPost(owner));
        notificationService.enqueueMatches(publishedPost(owner));
        notificationService.flushQueue();

        assertThat(botMessenger.messages()).hasSize(1);
        assertThat(botMessenger.lastMessage().text()).contains("3");
    }

    @Test
    @DisplayName("Kunlik chegara oshsa xabar BLOCKED bo'ladi, o'chirilmaydi (§1.1)")
    void dailyCapBlocksInsteadOfDeleting() {
        UUID owner = fixtures().insertUser(830012L, "egasi7", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830013L, "obunachi6", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        // Chegara 5 — beshta yuborilgan XABAR yozib qo'yamiz. Har birining
        // o'z `batch_id`si bor, chunki chegara qatorlarni emas, xabarlarni
        // sanaydi (§10.3).
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.update("""
                    INSERT INTO notifications_sent (user_id, status, kind, batch_id)
                    VALUES (?, 'SENT', 'MATCH', ?)
                    """, subscriber, UUID.randomUUID());
        }

        notificationService.enqueueMatches(publishedPost(owner));
        notificationService.flushQueue();

        assertThat(botMessenger.messages()).isEmpty();
        Long blocked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications_sent WHERE status = 'BLOCKED'", Long.class);
        assertThat(blocked).isEqualTo(1);
    }

    @Test
    @DisplayName("Ko'p e'lonli digest kunlik chegarada BITTA xabar sifatida sanaladi")
    void digestCountsAsOneMessageAgainstDailyCap() {
        UUID owner = fixtures().insertUser(830020L, "egasi10", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830021L, "obunachi9", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        // Bitta digestda 6 ta e'lon — chegaradan (5) ko'p, lekin bu BITTA xabar.
        for (int i = 0; i < 6; i++) {
            notificationService.enqueueMatches(publishedPost(owner));
        }
        notificationService.flushQueue();
        assertThat(botMessenger.messages()).hasSize(1);

        // Ilgari bu yerda chegara "oshgan" hisoblanardi (6 qator >= 5) va
        // keyingi xabar bloklanardi — foydalanuvchi bitta xabar olgan bo'lsa ham.
        notificationService.enqueueMatches(publishedPost(owner));
        notificationService.flushQueue();

        assertThat(botMessenger.messages()).hasSize(2);
        Long blocked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications_sent WHERE status = 'BLOCKED'", Long.class);
        assertThat(blocked).isZero();
        Long batches = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT batch_id) FROM notifications_sent
                WHERE user_id = ? AND status = 'SENT'
                """, Long.class, subscriber);
        assertThat(batches).isEqualTo(2);
    }

    @Test
    @DisplayName("Digestda ko'rsatilmagan e'lonlar soni aytiladi")
    void digestMentionsHiddenPostCount() {
        UUID owner = fixtures().insertUser(830022L, "egasi11", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830023L, "obunachi10", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        for (int i = 0; i < 5; i++) {
            notificationService.enqueueMatches(publishedPost(owner));
        }
        notificationService.flushQueue();

        // Sarlavhada 5 ta deyilgan, ro'yxatda 3 ta — qolgan 2 tasi aytilishi
        // kerak, aks holda xabar o'ziga o'zi zid bo'ladi.
        String text = botMessenger.lastMessage().text();
        assertThat(text).contains("5");
        assertThat(text).contains("2");
    }

    @Test
    @DisplayName("Telegram xabarni qabul qilmasa holat FAILED bo'ladi")
    void failedDeliveryIsRecorded() {
        UUID owner = fixtures().insertUser(830014L, "egasi8", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830015L, "obunachi7", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);
        botMessenger.failSends();

        notificationService.enqueueMatches(publishedPost(owner));
        notificationService.flushQueue();

        Long failed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications_sent WHERE status = 'FAILED'", Long.class);
        assertThat(failed).isEqualTo(1);
    }

    @Test
    @DisplayName("Havola ochilishi yoziladi va event chiqadi")
    void openingIsRecorded() {
        UUID owner = fixtures().insertUser(830016L, "egasi9", "NONE", 0);
        UUID subscriber = fixtures().insertUser(830017L, "obunachi8", "NONE", 0);
        subscribe(subscriber, "JP_UZ", "CARRY", null);

        UUID postId = publishedPost(owner);
        notificationService.enqueueMatches(postId);
        notificationService.flushQueue();

        boolean first = notificationService.markOpened(postId, subscriber);
        boolean second = notificationService.markOpened(postId, subscriber);

        assertThat(first).isTrue();
        assertThat(second).isFalse();

        Long events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'notification_opened'", Long.class);
        assertThat(events).isEqualTo(1);
    }

    @Test
    @DisplayName("MATCH turini sendOnce orqali yuborib bo'lmaydi")
    void matchKindCannotBeSentDirectly() {
        UUID owner = fixtures().insertUser(830018L, "egasi10", "NONE", 0);
        UUID postId = publishedPost(owner);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            var post = jdbcTemplate.queryForObject(
                    "SELECT id FROM posts WHERE id = ?", UUID.class, postId);
            assertThat(post).isNotNull();
            notificationService.sendOnce(null, null, NotificationKind.MATCH);
        }).isInstanceOf(IllegalArgumentException.class);
    }
}
