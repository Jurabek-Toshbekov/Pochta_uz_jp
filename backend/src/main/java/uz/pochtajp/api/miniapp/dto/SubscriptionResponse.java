package uz.pochtajp.api.miniapp.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;

/** Foydalanuvchining xabarnoma obunasi (§10.3). */
public record SubscriptionResponse(
        UUID id,
        PostType postType,
        Direction direction,
        String originAirport,
        String destAirport,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<Short> categoryIds,
        Instant createdAt
) {

    public static SubscriptionResponse from(NotificationSubscription subscription) {
        Short[] categories = subscription.getCategoryIds();
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getPostType(),
                subscription.getDirection(),
                subscription.getOriginAirport(),
                subscription.getDestAirport(),
                subscription.getDateFrom(),
                subscription.getDateTo(),
                categories == null ? List.of() : Arrays.asList(categories),
                subscription.getCreatedAt());
    }
}
