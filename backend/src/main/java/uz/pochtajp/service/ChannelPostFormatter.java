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

        // Belgilar bitta blok bo'lib chiqadi — orasida bo'sh qator qolmasin.
        List<String> badges = new ArrayList<>(2);
        if (post.getUser().getVerificationLevel() != VerificationLevel.NONE) {
            badges.add("✅ Tasdiqlangan foydalanuvchi");
        }
        if (post.getEditedAt() != null) {
            // O'quvchi eski ma'lumot bilan bog'lanmasligi uchun: e'lon kanalga
            // chiqqanidan keyin o'zgargan bo'lsa, buni ochiq aytamiz.
            badges.add("✏️ Tahrirlangan");
        }
        if (!badges.isEmpty()) {
            text.append('\n').append(String.join("\n", badges)).append('\n');
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
        // Yakuniy manzil kelish shahrining o'zi bo'lsa takrorlanmaydi:
        // "Tokio → Toshkent → Toshkent" degan post uyatli ko'rinadi.
        String finalDestination = post.getFinalDestination();
        if (!isRedundantFinalDestination(finalDestination, post.getDestAirport(), post.getDestCityFree())) {
            line.append(" → ").append(TelegramHtml.escape(finalDestination));
        }
        return line.toString();
    }

    /**
     * Aeroportning uchala nomi bilan solishtiriladi: foydalanuvchi "Бухара"
     * deb yozishi mumkin, kanalda esa o'zbekcha nom chiqadi.
     */
    private boolean isRedundantFinalDestination(String finalDestination, Airport destAirport,
                                                String destCityFree) {
        if (finalDestination == null || finalDestination.isBlank()) {
            return true;
        }
        String target = finalDestination.strip();
        List<String> candidates = new ArrayList<>(4);
        if (destAirport != null) {
            candidates.add(destAirport.getCityUz());
            candidates.add(destAirport.getCityRu());
            candidates.add(destAirport.getCityEn());
        }
        candidates.add(destCityFree);
        return candidates.stream()
                .anyMatch(name -> name != null && name.strip().equalsIgnoreCase(target));
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

    /**
     * Og'irlik satri.
     *
     * <p>Oraliq to'liq ko'rsatiladi: ilgari faqat maksimal chiqardi va
     * "1 dan 50 kg gacha" e'loni kanalda "50 kg gacha" bo'lib turardi —
     * pastki chegara yo'qolar edi. Faqat minimal kiritilgan holat esa
     * "50 kg gacha" deb teskari ma'no berardi.
     */
    private String weight(Post post) {
        BigDecimal min = post.getWeightKg();
        BigDecimal max = post.getWeightKgMax();

        if (min != null && max != null) {
            // Bir xil bo'lsa oraliq emas, aniq qiymat.
            return min.compareTo(max) == 0
                    ? trim(min) + " kg"
                    : trim(min) + "-" + trim(max) + " kg";
        }
        if (max != null) {
            return trim(max) + " kg gacha";
        }
        if (min != null) {
            return "kamida " + trim(min) + " kg";
        }
        return null;
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
