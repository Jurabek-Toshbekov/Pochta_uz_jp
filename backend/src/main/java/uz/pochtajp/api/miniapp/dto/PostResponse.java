package uz.pochtajp.api.miniapp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;

/**
 * E'lon javobi. Bu DTO **egasiga** qaytariladi (yaratgandan keyin, "Mening
 * e'lonlarim"da), shu sabab kontakt maydonlari ham bor.
 *
 * <p>Boshqa foydalanuvchiga ko'rsatiladigan variantda kontakt bo'lmaydi —
 * u faqat "Bog'lanish" bosilganda ochiladi (§6.4, 2-band). O'sha DTO
 * 3-bosqichda (qidiruv) qo'shiladi.
 *
 * @param channelUrl  kanalga chiqqan postga havola (publish bo'lgan bo'lsa)
 * @param deepLink    Mini App'da shu e'lonni ochadigan havola (§8.4)
 */
public record PostResponse(
        UUID id,
        PostType postType,
        Direction direction,
        String originAirport,
        String destAirport,
        String originCityFree,
        String destCityFree,
        String finalDestination,
        LocalDate departDate,
        LocalDate deadlineDate,
        int dateFlexibleDays,
        BigDecimal weightKg,
        BigDecimal weightKgMax,
        BigDecimal priceAmount,
        Currency priceCurrency,
        PriceUnit priceUnit,
        List<Short> categoryIds,
        String comment,
        String contactPhone,
        String contactTelegram,
        String contactOther,
        PostStatus status,
        Long channelMessageId,
        String channelUrl,
        String deepLink,
        Instant publishedAt,
        Instant expiresAt,
        int viewCount,
        int contactRevealCount,
        Instant createdAt
) {

    public static PostResponse of(Post post, List<Short> categoryIds, String channelUrl, String deepLink) {
        return new PostResponse(
                post.getId(),
                post.getPostType(),
                post.getDirection(),
                post.getOriginAirport() == null ? null : post.getOriginAirport().getCode(),
                post.getDestAirport() == null ? null : post.getDestAirport().getCode(),
                post.getOriginCityFree(),
                post.getDestCityFree(),
                post.getFinalDestination(),
                post.getDepartDate(),
                post.getDeadlineDate(),
                post.getDateFlexibleDays(),
                post.getWeightKg(),
                post.getWeightKgMax(),
                post.getPriceAmount(),
                post.getPriceCurrency(),
                post.getPriceUnit(),
                categoryIds,
                post.getComment(),
                post.getContactPhone(),
                post.getContactTelegram(),
                post.getContactOther(),
                post.getStatus(),
                post.getChannelMessageId(),
                channelUrl,
                deepLink,
                post.getPublishedAt(),
                post.getExpiresAt(),
                post.getViewCount(),
                post.getContactRevealCount(),
                post.getCreatedAt());
    }
}
