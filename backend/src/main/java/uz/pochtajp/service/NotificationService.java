package uz.pochtajp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.bot.BotNotifier;
import uz.pochtajp.config.SettingKeys;
import uz.pochtajp.domain.NotificationSent;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.NotificationKind;
import uz.pochtajp.domain.enums.NotificationStatus;
import uz.pochtajp.repository.NotificationSentRepository;
import uz.pochtajp.repository.NotificationSubscriptionRepository;
import uz.pochtajp.repository.PostCategoryRepository;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * Obunaga mos e'lon chiqqanda xabar berish (§10.3).
 *
 * <p>Ikki bosqichli: publish paytida moslar <b>navbatga</b> yoziladi
 * ({@code PENDING}), keyin digest job ularni yuboradi. Nima uchun shunday:
 * bir vaqtda 5 ta e'lon chiqsa, bitta odam 5 ta xabar olmasligi kerak —
 * ular bitta "Sizga mos 5 ta yangi e'lon bor" xabariga birlashadi.
 *
 * <p>Uchta chegara (§10.3 anti-spam):
 * <ul>
 *   <li>bitta e'lon + bitta odam + bitta tur = bir marta (unikal indeks)</li>
 *   <li>kuniga maksimal {@code notifications.max_per_day} xabar</li>
 *   <li>bir yuborishda bitta xabar — nechta e'lon bo'lsa ham</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Digest oralig'i: 2 daqiqa. Xabar sezilarli kechikmaydi, lekin birlashadi. */
    private static final long FLUSH_INTERVAL_MS = 2L * 60 * 1000;

    /** Bir yugurishda ko'pi bilan shuncha navbat yozuvi olinadi. */
    private static final int QUEUE_BATCH = 500;

    /** Kunlik chegara oynasi. */
    private static final Duration DAY = Duration.ofDays(1);

    private static final int DEFAULT_MAX_PER_DAY = 5;

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationSentRepository sentRepository;
    private final PostRepository postRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final UserRepository userRepository;
    private final BotNotifier notifier;
    private final SettingsService settingsService;
    private final EventLogger eventLogger;

    public NotificationService(NotificationSubscriptionRepository subscriptionRepository,
                               NotificationSentRepository sentRepository,
                               PostRepository postRepository,
                               PostCategoryRepository postCategoryRepository,
                               UserRepository userRepository,
                               BotNotifier notifier,
                               SettingsService settingsService,
                               EventLogger eventLogger) {
        this.subscriptionRepository = subscriptionRepository;
        this.sentRepository = sentRepository;
        this.postRepository = postRepository;
        this.postCategoryRepository = postCategoryRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.settingsService = settingsService;
        this.eventLogger = eventLogger;
    }

    // ------------------------------------------------------------------
    // 1. Navbatga yozish
    // ------------------------------------------------------------------

    /**
     * E'lon kanalga chiqqandan keyin chaqiriladi.
     *
     * <p>{@code @Async}: xabarnoma qidirish publish javobini kechiktirmasligi
     * kerak. Xato bo'lsa ham e'lon chiqqan holida qoladi.
     */
    @Async
    @Transactional
    public void enqueueMatches(UUID postId) {
        try {
            Post post = postRepository.findByIdAndDeletedAtIsNull(postId).orElse(null);
            if (post == null) {
                return;
            }
            List<Short> categoryIds = postCategoryRepository.findCategoryIdsByPostId(postId);
            UUID ownerId = post.getUser().getId();

            List<NotificationSubscription> candidates = subscriptionRepository.findCandidates(
                    ownerId, post.getDirection(), post.getPostType());

            int queued = 0;
            for (NotificationSubscription subscription : candidates) {
                if (!SubscriptionMatcher.matches(subscription, post, categoryIds)) {
                    continue;
                }
                UUID userId = subscription.getUser().getId();
                if (sentRepository.existsByPostIdAndUserIdAndKind(postId, userId, NotificationKind.MATCH)) {
                    continue;
                }
                sentRepository.save(row(userId, postId, subscription.getId(),
                        NotificationKind.MATCH, NotificationStatus.PENDING));
                queued++;
            }
            if (queued > 0) {
                log.info("Xabarnoma navbatga yozildi: post_id={} count={}", postId, queued);
            }
        } catch (RuntimeException ex) {
            log.error("Xabarnoma navbati yozilmadi: post_id={}", postId, ex);
        }
    }

    /** Bitta odamga bitta e'lon uchun xabar — job'lar shu orqali navbatga qo'yadi. */
    @Transactional
    public boolean enqueue(UUID userId, UUID postId, NotificationKind kind) {
        if (sentRepository.existsByPostIdAndUserIdAndKind(postId, userId, kind)) {
            return false;
        }
        sentRepository.save(row(userId, postId, null, kind, NotificationStatus.PENDING));
        return true;
    }

    private static NotificationSent row(UUID userId, UUID postId, UUID subscriptionId,
                                        NotificationKind kind, NotificationStatus status) {
        NotificationSent entity = new NotificationSent();
        entity.setUserId(userId);
        entity.setPostId(postId);
        entity.setSubscriptionId(subscriptionId);
        entity.setKind(kind);
        entity.setStatus(status);
        return entity;
    }

    // ------------------------------------------------------------------
    // 2. Navbatni yuborish
    // ------------------------------------------------------------------

    /**
     * Navbatni foydalanuvchi kesimida guruhlab yuboradi.
     *
     * <p>Har bir foydalanuvchi uchun bitta xabar ketadi. Kunlik chegara
     * oshgan bo'lsa yozuv {@code BLOCKED} qilinadi — o'chirilmaydi (§1.1),
     * shunda "nega xabar kelmadi" savoliga javob bor.
     */
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS, initialDelay = 30_000)
    @Transactional
    public void flushQueue() {
        List<NotificationSent> pending = sentRepository.findPendingQueue(PageRequest.of(0, QUEUE_BATCH));
        if (pending.isEmpty()) {
            return;
        }

        Map<UUID, List<NotificationSent>> byUser = new LinkedHashMap<>();
        for (NotificationSent row : pending) {
            byUser.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row);
        }

        int sent = 0;
        int blocked = 0;
        for (Map.Entry<UUID, List<NotificationSent>> entry : byUser.entrySet()) {
            Outcome outcome = deliver(entry.getKey(), entry.getValue());
            sent += outcome.sent();
            blocked += outcome.blocked();
        }
        if (sent > 0 || blocked > 0) {
            log.info("Xabarnoma yuborildi: users={} sent={} blocked={}", byUser.size(), sent, blocked);
        }
    }

    private record Outcome(int sent, int blocked) {
    }

    private Outcome deliver(UUID userId, List<NotificationSent> rows) {
        User user = userRepository.findById(userId).filter(u -> u.getDeletedAt() == null).orElse(null);
        if (user == null || user.getTelegramId() == null) {
            rows.forEach(row -> finish(row, NotificationStatus.FAILED));
            return new Outcome(0, 0);
        }

        int maxPerDay = settingsService.number(SettingKeys.NOTIFICATIONS_MAX_PER_DAY, DEFAULT_MAX_PER_DAY);
        // Mos e'lonlar emas, YUBORILGAN XABARLAR sanaladi (§10.3): bitta
        // digestda 6 ta e'lon bo'lsa ham foydalanuvchi bitta xabar oladi.
        long alreadySent = sentRepository.countMessages(
                userId, NotificationStatus.SENT, Instant.now().minus(DAY));
        if (alreadySent >= maxPerDay) {
            // Chegara oshdi: bugungi qolgan xabarlar bloklanadi. Ertaga
            // qayta yuborilmaydi — bu ataylab, aks holda navbat cheksiz o'sadi.
            rows.forEach(row -> finish(row, NotificationStatus.BLOCKED));
            return new Outcome(0, rows.size());
        }

        List<Post> posts = rows.stream()
                .map(NotificationSent::getPostId)
                .filter(java.util.Objects::nonNull)
                .map(id -> postRepository.findByIdAndDeletedAtIsNull(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        boolean delivered = notifier.sendMatchDigest(user, posts);
        NotificationStatus status = delivered ? NotificationStatus.SENT : NotificationStatus.FAILED;
        // Butun digest — bitta xabar, demak bitta batch.
        UUID batchId = UUID.randomUUID();
        rows.forEach(row -> {
            row.setBatchId(batchId);
            finish(row, status);
        });

        if (delivered) {
            eventLogger.track(TrackedEvent.of(EventName.NOTIFICATION_SENT, EventSource.SYSTEM)
                    .user(userId)
                    .property("kind", NotificationKind.MATCH.name())
                    .property("post_count", posts.size())
                    .build());
        }
        return new Outcome(delivered ? rows.size() : 0, 0);
    }

    private void finish(NotificationSent row, NotificationStatus status) {
        row.setStatus(status);
        sentRepository.save(row);
    }

    /**
     * Bitta odamga bitta xabarni <b>bir marta</b> yuboradi.
     *
     * <p>{@code MATCH} dan boshqa turlar navbatga tushmaydi: ularning
     * har biri o'z tugmalari bilan keladi va boshqasi bilan birlashtirib
     * bo'lmaydi ("Odam topdingizmi?" savolini digest'ga qo'shish mantiqsiz).
     *
     * <p>Takrorlanmaslik {@code (post_id, user_id, kind)} unikal indeksi
     * bilan kafolatlanadi — job qayta ishga tushsa ham ikkinchi xabar ketmaydi.
     *
     * @return xabar aynan shu chaqiruvda yuborilgan bo'lsa {@code true}
     */
    @Transactional
    public boolean sendOnce(User user, Post post, NotificationKind kind) {
        if (kind == NotificationKind.MATCH) {
            throw new IllegalArgumentException("MATCH turi navbat orqali yuboriladi");
        }
        UUID userId = user.getId();
        if (sentRepository.existsByPostIdAndUserIdAndKind(post.getId(), userId, kind)) {
            return false;
        }

        boolean delivered = switch (kind) {
            case DEAL_ASK -> notifier.sendDealAsk(user, post);
            case EXPIRY_WARNING -> notifier.sendExpiryWarning(user, post);
            case REVIEW_ASK -> notifier.sendReviewAsk(user, post);
            case MATCH -> false;
        };

        NotificationSent row = row(userId, post.getId(), null, kind,
                delivered ? NotificationStatus.SENT : NotificationStatus.FAILED);
        // Har biri alohida xabar — o'z batch'i (kunlik chegara shuni sanaydi).
        row.setBatchId(UUID.randomUUID());
        sentRepository.save(row);

        if (delivered) {
            eventLogger.track(TrackedEvent.of(EventName.NOTIFICATION_SENT, EventSource.SYSTEM)
                    .user(userId)
                    .post(post.getId())
                    .property("kind", kind.name())
                    .build());
        }
        return delivered;
    }

    // ------------------------------------------------------------------
    // 3. Ochilishni belgilash
    // ------------------------------------------------------------------

    /**
     * Xabarnomadagi havola bosilganda chaqiriladi (§6.1 — notification_opened).
     *
     * <p>CTR shu yerdan hisoblanadi: {@code opened_at / sent}.
     *
     * @return ochilish birinchi marta yozilgan bo'lsa {@code true}
     */
    @Transactional
    public boolean markOpened(UUID postId, UUID userId) {
        return sentRepository.findLatestUnopened(postId, userId)
                .map(row -> {
                    row.setOpenedAt(Instant.now());
                    sentRepository.save(row);
                    eventLogger.track(TrackedEvent.of(EventName.NOTIFICATION_OPENED, EventSource.MINIAPP)
                            .user(userId)
                            .post(postId)
                            .property("kind", row.getKind().name())
                            .build());
                    return true;
                })
                .orElse(false);
    }
}
