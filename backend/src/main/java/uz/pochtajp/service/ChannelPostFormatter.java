package uz.pochtajp.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import uz.pochtajp.common.TelegramHtml;
import uz.pochtajp.config.BotProperties;
import uz.pochtajp.domain.Airport;
import uz.pochtajp.domain.CargoCategory;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;
import uz.pochtajp.domain.enums.VerificationLevel;

/**
 * Kanal post shabloni (§8.4).
 *
 * <p>MUHIM: kontakt kanalda ko'rsatilmaydi. Bu atributsiyani va reveal
 * metrikasini beradi, spam va scraping'ni kamaytiradi (§8.4).
 *
 * <p>Foydalanuvchi kiritgan har bir matn {@link TelegramHtml#escape} dan o'tadi (§7.2).
 */
@Component
public class ChannelPostFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BotProperties botProperties;

    public ChannelPostFormatter(BotProperties botProperties) {
        this.botProperties = botProperties;
    }

    public String format(Post post, List<CargoCategory> categories) {
        StringBuilder text = new StringBuilder(700);

        text.append("<b>").append(title(post.getPostType())).append("</b>\n\n");
        text.append("<b>Yo'nalish:</b> ").append(route(post)).append('\n');
        text.append("<b>Sana:</b> ").append(dateLine(post)).append('\n');
        text.append("<b>Yuk:</b> ").append(cargoLine(post, categories)).append('\n');
        text.append("<b>Narx:</b> ").append(priceLine(post)).append('\n');

        if (post.getComment() != null && !post.getComment().isBlank()) {
            text.append("<b>Izoh:</b> ").append(TelegramHtml.escape(post.getComment())).append('\n');
        }

        if (post.getUser().getVerificationLevel() != VerificationLevel.NONE) {
            text.append("\n✅ Tasdiqlangan foydalanuvchi\n");
        }

        String hashtags = hashtags(post, categories);
        if (!hashtags.isEmpty()) {
            text.append('\n').append(hashtags).append('\n');
        }

        text.append("\n👉 Bog'lanish va batafsil: ")
                .append(botProperties.deepLinkForPost(post.getId())).append('\n');
        text.append("📝 E'lon berish: @").append(TelegramHtml.escape(botProperties.username()));

        return text.toString();
    }

    private String title(PostType postType) {
        return postType == PostType.CARRY
                ? "✈️ Pochta olib ketaman"
                : "📦 Pochta yuboraman";
    }

    private String route(Post post) {
        String originCode = code(post.getOriginAirport(), post.getOriginCityFree());
        String destCode = code(post.getDestAirport(), post.getDestCityFree());
        StringBuilder line = new StringBuilder();
        line.append(originCode).append(" → ").append(destCode);

        String originCity = city(post.getOriginAirport());
        String destCity = city(post.getDestAirport());
        if (originCity != null && destCity != null) {
            line.append("  (").append(TelegramHtml.escape(originCity))
                    .append(" → ").append(TelegramHtml.escape(destCity)).append(')');
        }
        if (post.getFinalDestination() != null && !post.getFinalDestination().isBlank()) {
            line.append(" → ").append(TelegramHtml.escape(post.getFinalDestination()));
        }
        return line.toString();
    }

    private String code(Airport airport, String freeCity) {
        if (airport != null) {
            return airport.getCode();
        }
        return freeCity == null || freeCity.isBlank() ? "—" : TelegramHtml.escape(freeCity);
    }

    private String city(Airport airport) {
        return airport == null ? null : airport.getCityUz();
    }

    private String dateLine(Post post) {
        LocalDate date = post.getPostType() == PostType.CARRY ? post.getDepartDate() : post.getDeadlineDate();
        if (date == null) {
            return "kelishiladi";
        }
        StringBuilder line = new StringBuilder(DATE.format(date));
        if (post.getPostType() == PostType.SEND) {
            line.append(" gacha");
        }
        if (post.getDateFlexibleDays() > 0) {
            line.append(", ±").append(post.getDateFlexibleDays()).append(" kun");
        }
        return line.toString();
    }

    private String cargoLine(Post post, List<CargoCategory> categories) {
        List<String> parts = new ArrayList<>();
        for (CargoCategory category : categories) {
            String emoji = category.getEmoji();
            parts.add((emoji == null || emoji.isBlank() ? "" : emoji + " ")
                    + TelegramHtml.escape(category.getTitleUz()));
        }
        String weight = weight(post);
        if (weight != null) {
            parts.add(weight);
        }
        return parts.isEmpty() ? "—" : String.join(", ", parts);
    }

    private String weight(Post post) {
        BigDecimal max = post.getWeightKgMax() != null ? post.getWeightKgMax() : post.getWeightKg();
        if (max == null) {
            return null;
        }
        return trim(max) + " kg gacha";
    }

    private String priceLine(Post post) {
        if (post.getPriceUnit() == PriceUnit.NEGOTIABLE || post.getPriceAmount() == null) {
            return "kelishamiz";
        }
        return trim(post.getPriceAmount()) + " " + post.getPriceCurrency().name() + " / " + unit(post.getPriceUnit());
    }

    private String unit(PriceUnit unit) {
        return switch (unit) {
            case PER_KG -> "kg";
            case TOTAL -> "jami";
            case NEGOTIABLE -> "kelishamiz";
        };
    }

    /** Qidiruv va guruhlash uchun hashtag'lar. Foydalanuvchi matni hashtag'ga tushmaydi. */
    private String hashtags(Post post, List<CargoCategory> categories) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("#" + post.getDirection().name());
        tags.add("#" + post.getPostType().name());
        if (post.getOriginAirport() != null && post.getDestAirport() != null) {
            tags.add("#" + post.getOriginAirport().getCode() + "_" + post.getDestAirport().getCode());
        }
        for (CargoCategory category : categories) {
            String token = TelegramHtml.hashtagToken(category.getCode());
            if (!token.isEmpty()) {
                tags.add("#" + token);
            }
        }
        return String.join(" ", tags);
    }

    /** {@code 2000.00} -> {@code 2000}, {@code 20.50} -> {@code 20.5}. */
    private String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
