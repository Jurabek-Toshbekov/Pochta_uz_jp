package uz.pochtajp.bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.pochtajp.common.TelegramHtml;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.PostType;

/**
 * Bot orqali yuboriladigan xabarnomalar (§8.3, §10.3).
 *
 * <p>Bu klass faqat <b>matn yasaydi va yuboradi</b>. Kimga yuborish
 * kerakligini va takrorlanmaslikni {@code NotificationService} hal qiladi —
 * shunda "nima yuborildi" va "kimga yuborildi" mantiqlari aralashmaydi.
 *
 * <p>Matn har bir foydalanuvchining tilida ({@code users.ui_language}).
 * Foydalanuvchi kiritgan hech narsa (izoh, ism) xabarga tushmaydi —
 * faqat tuzilgan maydonlar: yo'nalish, sana, narx. Shu sabab HTML
 * injection yuzasi ham yo'q.
 */
@Component
public class BotNotifier {

    private static final Logger log = LoggerFactory.getLogger(BotNotifier.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Digest'da ko'pi bilan shuncha e'lon sanab o'tiladi, qolgani "va boshqalar". */
    private static final int DIGEST_PREVIEW = 3;

    private final BotMessenger messenger;
    private final BotKeyboards keyboards;

    public BotNotifier(BotMessenger messenger, BotKeyboards keyboards) {
        this.messenger = messenger;
        this.keyboards = keyboards;
    }

    /**
     * Obunaga mos e'lon(lar) haqida bitta xabar.
     *
     * <p>Bitta e'lon bo'lsa — tafsiloti va shu e'lonni ochadigan tugma.
     * Bir nechta bo'lsa — soni va qidiruvni ochadigan tugma: 5 ta kartani
     * ketma-ket yuborish spam bo'lardi (§10.3).
     *
     * @return Telegram xabarni qabul qilgan bo'lsa {@code true}
     */
    public boolean sendMatchDigest(User user, List<Post> posts) {
        if (posts.isEmpty()) {
            return false;
        }
        BotTexts.Pack texts = BotTexts.of(user.getUiLanguage());
        long chatId = user.getTelegramId();

        if (posts.size() == 1) {
            Post post = posts.get(0);
            return messenger.sendHtml(chatId,
                    texts.notifyMatchOne().formatted(summary(post)),
                    keyboards.openPost(texts.btnOpenPost(), post.getId()));
        }

        String preview = posts.stream()
                .limit(DIGEST_PREVIEW)
                .map(BotNotifier::summary)
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
        return messenger.sendHtml(chatId,
                texts.notifyMatchMany().formatted(posts.size(), preview),
                keyboards.single(texts.btnOpenSearch(), "/search"));
    }

    /** "Odam topdingizmi?" — publish'dan 3 kun keyin (§6.4, 1-band). */
    public boolean sendDealAsk(User user, Post post) {
        BotTexts.Pack texts = BotTexts.of(user.getUiLanguage());
        return messenger.sendHtml(user.getTelegramId(),
                texts.dealAsk().formatted(summary(post)),
                keyboards.dealAsk(texts, post.getId()));
    }

    /** Muddat tugashiga 1 kun qoldi (§8.3). */
    public boolean sendExpiryWarning(User user, Post post) {
        BotTexts.Pack texts = BotTexts.of(user.getUiLanguage());
        return messenger.sendHtml(user.getTelegramId(),
                texts.notifyExpiry().formatted(summary(post)),
                keyboards.openPost(texts.btnOpenPost(), post.getId()));
    }

    /** Bitim tasdiqlangandan keyin baho so'rovi (§6.4, 7-band). */
    public boolean sendReviewAsk(User user, Post post) {
        BotTexts.Pack texts = BotTexts.of(user.getUiLanguage());
        return messenger.sendHtml(user.getTelegramId(),
                texts.reviewAsk(),
                keyboards.reviewStars(texts, post.getId()));
    }

    /** Qisqa javob — tugma bosilgandan keyin. */
    public void sendPlain(User user, String text) {
        messenger.sendHtml(user.getTelegramId(), text, null);
    }

    /**
     * E'lonning bitta qatorlik tavsifi: <code>NRT → TAS · 28.08.2026 · 2000 JPY/kg</code>.
     *
     * <p>Faqat tuzilgan maydonlar. Erkin matn (izoh, shahar nomi) qo'shilsa
     * ham {@link TelegramHtml#escape} orqali o'tadi.
     */
    static String summary(Post post) {
        StringBuilder line = new StringBuilder();
        line.append("<b>").append(route(post)).append("</b>");

        LocalDate date = post.getPostType() == PostType.CARRY
                ? post.getDepartDate()
                : post.getDeadlineDate();
        if (date != null) {
            line.append(" · ").append(DATE.format(date));
        }
        if (post.getPriceAmount() != null && post.getPriceCurrency() != null) {
            line.append(" · ")
                    .append(post.getPriceAmount().stripTrailingZeros().toPlainString())
                    .append(' ')
                    .append(post.getPriceCurrency().name());
            if (post.getPriceUnit() != null) {
                line.append('/').append(unitSuffix(post));
            }
        }
        return line.toString();
    }

    private static String route(Post post) {
        String origin = post.getOriginAirport() != null
                ? post.getOriginAirport().getCode()
                : TelegramHtml.escape(post.getOriginCityFree());
        String dest = post.getDestAirport() != null
                ? post.getDestAirport().getCode()
                : TelegramHtml.escape(post.getDestCityFree());
        return (origin == null ? "?" : origin) + " → " + (dest == null ? "?" : dest);
    }

    private static String unitSuffix(Post post) {
        return switch (post.getPriceUnit()) {
            case PER_KG -> "kg";
            case TOTAL -> "jami";
            case NEGOTIABLE -> "kelishamiz";
        };
    }

    /** Log uchun: xabar matni yozilmaydi, faqat kimga ketgani (§1.7). */
    void logDelivery(String kind, java.util.UUID userId, boolean delivered) {
        log.debug("Xabarnoma: kind={} user_id={} delivered={}", kind, userId, delivered);
    }
}
