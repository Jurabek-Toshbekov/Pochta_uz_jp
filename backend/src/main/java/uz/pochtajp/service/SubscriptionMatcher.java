package uz.pochtajp.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.enums.PostType;

/**
 * Obuna e'longa mos keladimi (§10.3).
 *
 * <p>Sof funksiya — bazaga tegmaydi va test shu yerga yoziladi. Moslik
 * qoidasi yagona joyda bo'lishi shart: aks holda "nega menga bu e'lon
 * keldi" degan savolga javob berib bo'lmaydi.
 *
 * <p>Umumiy printsip: obunada maydon bo'sh bo'lsa — u shart emas
 * ("farqi yo'q"). To'ldirilgan bo'lsa — qat'iy mos kelishi kerak.
 */
public final class SubscriptionMatcher {

    private SubscriptionMatcher() {
    }

    /**
     * @param categoryIds e'lonning kategoriya ID'lari (bo'sh bo'lishi mumkin)
     */
    public static boolean matches(NotificationSubscription subscription,
                                  Post post,
                                  Collection<Short> categoryIds) {
        if (subscription == null || post == null) {
            return false;
        }
        if (subscription.getDirection() != null && subscription.getDirection() != post.getDirection()) {
            return false;
        }
        if (subscription.getPostType() != null && subscription.getPostType() != post.getPostType()) {
            return false;
        }
        if (!airportMatches(subscription.getOriginAirport(), airportCode(post, true))) {
            return false;
        }
        if (!airportMatches(subscription.getDestAirport(), airportCode(post, false))) {
            return false;
        }
        if (!dateMatches(subscription, post)) {
            return false;
        }
        return categoryMatches(subscription.getCategoryIds(), categoryIds);
    }

    private static String airportCode(Post post, boolean origin) {
        var airport = origin ? post.getOriginAirport() : post.getDestAirport();
        return airport == null ? null : airport.getCode();
    }

    private static boolean airportMatches(String wanted, String actual) {
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        return wanted.equalsIgnoreCase(actual);
    }

    /**
     * Sana solishtiruvi.
     *
     * <p>CARRY uchun uchish sanasi, SEND uchun oxirgi muddat — obuna egasi
     * aynan shu sanalarni kutadi. E'londa sana yo'q bo'lsa va obunada
     * oraliq ko'rsatilgan bo'lsa — mos emas: noma'lum sanani "mos" deb
     * hisoblash odamga keraksiz xabar yuborishga olib keladi.
     *
     * <p>{@code date_flexible_days} hisobga olinadi: e'lon egasi ±N kun
     * moslashishga tayyor bo'lsa, oraliq shunga kengaytiriladi.
     */
    private static boolean dateMatches(NotificationSubscription subscription, Post post) {
        LocalDate from = subscription.getDateFrom();
        LocalDate to = subscription.getDateTo();
        if (from == null && to == null) {
            return true;
        }

        LocalDate postDate = post.getPostType() == PostType.CARRY
                ? post.getDepartDate()
                : post.getDeadlineDate();
        if (postDate == null) {
            return false;
        }

        int flexible = Math.max(0, post.getDateFlexibleDays());
        LocalDate earliest = postDate.minusDays(flexible);
        LocalDate latest = postDate.plusDays(flexible);

        if (from != null && latest.isBefore(from)) {
            return false;
        }
        return to == null || !earliest.isAfter(to);
    }

    /** Obunada kategoriya tanlangan bo'lsa — kamida bittasi kesishishi kerak. */
    private static boolean categoryMatches(Short[] wanted, Collection<Short> actual) {
        if (wanted == null || wanted.length == 0) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        Set<Short> actualSet = Set.copyOf(actual);
        for (Short candidate : wanted) {
            if (candidate != null && actualSet.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
