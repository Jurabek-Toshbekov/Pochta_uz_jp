package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.domain.Airport;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;

/**
 * Obuna moslik qoidalari (§10.3).
 *
 * <p>Bu qoida noto'g'ri bo'lsa foydalanuvchi keraksiz xabar oladi va
 * obunani o'chiradi — shuning uchun har bir shart alohida tekshiriladi.
 */
class SubscriptionMatcherTest {

    private static Airport airport(String code) {
        Airport airport = new Airport();
        airport.setCode(code);
        return airport;
    }

    private static Post carryPost() {
        Post post = new Post();
        post.setPostType(PostType.CARRY);
        post.setDirection(Direction.JP_UZ);
        post.setOriginAirport(airport("NRT"));
        post.setDestAirport(airport("TAS"));
        post.setDepartDate(LocalDate.of(2026, 9, 10));
        return post;
    }

    private static NotificationSubscription emptySubscription() {
        return new NotificationSubscription();
    }

    @Test
    @DisplayName("Bo'sh obuna hamma narsaga mos keladi")
    void emptySubscriptionMatchesEverything() {
        assertThat(SubscriptionMatcher.matches(emptySubscription(), carryPost(), List.of())).isTrue();
    }

    @Test
    @DisplayName("Yo'nalish mos kelmasa — yo'q")
    void directionMustMatch() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setDirection(Direction.UZ_JP);

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isFalse();
    }

    @Test
    @DisplayName("E'lon turi mos kelmasa — yo'q")
    void postTypeMustMatch() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setPostType(PostType.SEND);

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isFalse();
    }

    @Test
    @DisplayName("Aeroport kodi katta-kichik harfdan qat'i nazar solishtiriladi")
    void airportComparisonIsCaseInsensitive() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setOriginAirport("nrt");

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isTrue();
    }

    @Test
    @DisplayName("Boshqa aeroport — mos emas")
    void otherAirportDoesNotMatch() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setOriginAirport("KIX");

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isFalse();
    }

    @Test
    @DisplayName("Sana oralig'i ichida — mos")
    void dateInsideRangeMatches() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setDateFrom(LocalDate.of(2026, 9, 1));
        subscription.setDateTo(LocalDate.of(2026, 9, 30));

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isTrue();
    }

    @Test
    @DisplayName("Sana oralig'idan tashqarida — mos emas")
    void dateOutsideRangeDoesNotMatch() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setDateFrom(LocalDate.of(2026, 10, 1));
        subscription.setDateTo(LocalDate.of(2026, 10, 30));

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isFalse();
    }

    @Test
    @DisplayName("±N kun moslashuv oraliqni kengaytiradi")
    void flexibleDaysWidenTheRange() {
        Post post = carryPost();
        post.setDateFlexibleDays((short) 5);

        NotificationSubscription subscription = emptySubscription();
        subscription.setDateFrom(LocalDate.of(2026, 9, 12));
        subscription.setDateTo(LocalDate.of(2026, 9, 20));

        assertThat(SubscriptionMatcher.matches(subscription, post, List.of())).isTrue();
    }

    @Test
    @DisplayName("SEND uchun oxirgi muddat solishtiriladi, uchish sanasi emas")
    void sendUsesDeadlineDate() {
        Post post = new Post();
        post.setPostType(PostType.SEND);
        post.setDirection(Direction.JP_UZ);
        post.setDeadlineDate(LocalDate.of(2026, 9, 10));

        NotificationSubscription subscription = emptySubscription();
        subscription.setDateFrom(LocalDate.of(2026, 9, 1));
        subscription.setDateTo(LocalDate.of(2026, 9, 30));

        assertThat(SubscriptionMatcher.matches(subscription, post, List.of())).isTrue();
    }

    @Test
    @DisplayName("E'londa sana yo'q, obunada oraliq bor — mos emas")
    void unknownDateDoesNotMatchDateFilter() {
        Post post = carryPost();
        post.setDepartDate(null);

        NotificationSubscription subscription = emptySubscription();
        subscription.setDateFrom(LocalDate.of(2026, 9, 1));

        assertThat(SubscriptionMatcher.matches(subscription, post, List.of())).isFalse();
    }

    @Test
    @DisplayName("Kategoriya kesishsa — mos")
    void categoryIntersectionMatches() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setCategoryIds(new Short[] {2, 5});

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of((short) 5, (short) 7)))
                .isTrue();
    }

    @Test
    @DisplayName("Kategoriya kesishmasa — mos emas")
    void categoryWithoutIntersectionDoesNotMatch() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setCategoryIds(new Short[] {2, 5});

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of((short) 7)))
                .isFalse();
    }

    @Test
    @DisplayName("Obunada kategoriya bor, e'londa yo'q — mos emas")
    void categoryFilterNeedsPostCategories() {
        NotificationSubscription subscription = emptySubscription();
        subscription.setCategoryIds(new Short[] {2});

        assertThat(SubscriptionMatcher.matches(subscription, carryPost(), List.of())).isFalse();
    }

    @Test
    @DisplayName("null qiymatlar xato bermaydi")
    void nullsAreSafe() {
        assertThat(SubscriptionMatcher.matches(null, carryPost(), List.of())).isFalse();
        assertThat(SubscriptionMatcher.matches(emptySubscription(), null, List.of())).isFalse();
        assertThat(SubscriptionMatcher.matches(emptySubscription(), carryPost(), null)).isTrue();
    }
}
