package uz.pochtajp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.domain.AuditLog;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.ClosedReason;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.repository.AuditLogRepository;
import uz.pochtajp.repository.NotificationSubscriptionRepository;
import uz.pochtajp.repository.PostDraftRepository;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * {@code /mening_malumotlarim} — ma'lumot eksporti va o'chirish (§7.2, §8.1).
 *
 * <p>O'chirish qanday ishlaydi (§1.1 bilan qanday yashaydi):
 * <ul>
 *   <li>PII maydonlari tozalanadi: ism, familiya, username, telefon, kontaktlar</li>
 *   <li>{@code users.deleted_at} qo'yiladi — qator o'chirilmaydi</li>
 *   <li>faol e'lonlar {@code CLOSED} qilinadi, o'chirilmaydi</li>
 *   <li>event'lar va analitika joyida qoladi — ularda PII yo'q (§1.7)</li>
 * </ul>
 * Ya'ni foydalanuvchi haqidagi <b>shaxsiy</b> ma'lumot yo'qoladi, bozor
 * statistikasi esa buzilmaydi.
 */
@Service
public class UserDataService {

    private static final Logger log = LoggerFactory.getLogger(UserDataService.class);

    public static final String ACTION_EXPORT = "USER_DATA_EXPORT";
    public static final String ACTION_DELETE = "USER_DATA_DELETE";

    private static final List<PostStatus> ACTIVE_STATUSES =
            List.of(PostStatus.DRAFT, PostStatus.PENDING, PostStatus.PUBLISHED);

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostDraftRepository draftRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public UserDataService(UserRepository userRepository,
                           PostRepository postRepository,
                           PostDraftRepository draftRepository,
                           NotificationSubscriptionRepository subscriptionRepository,
                           AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.draftRepository = draftRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Foydalanuvchi haqidagi barcha ma'lumot — JSON.
     *
     * @return UTF-8 baytlar; bot fayl sifatida yuboradi
     */
    @Transactional
    public byte[] exportAsJson(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now().toString());
        export.put("profile", profileMap(user));
        export.put("posts", postRepository
                .findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(this::postMap)
                .toList());
        export.put("draft", draftRepository.findByUser_Id(userId)
                .map(draft -> Map.<String, Object>of(
                        "step", String.valueOf(draft.getStep()),
                        "payload", draft.getPayload(),
                        "updatedAt", String.valueOf(draft.getUpdatedAt())))
                .orElse(Map.of()));
        export.put("subscriptions", subscriptionRepository
                .findByUser_IdAndActiveTrueAndDeletedAtIsNull(userId).stream()
                .map(this::subscriptionMap)
                .toList());

        audit(userId, ACTION_EXPORT, Map.of("posts", ((List<?>) export.get("posts")).size()));
        log.info("Ma'lumot eksporti berildi: user_id={}", userId);

        return serialize(export);
    }

    /**
     * O'chirish so'rovi. Qaytarib bo'lmaydi.
     *
     * @return yopilgan e'lonlar soni
     */
    @Transactional
    public int deletePersonalData(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        List<Post> posts = postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        int closed = 0;
        for (Post post : posts) {
            // Kontakt ma'lumoti e'londa ham saqlanadi — u ham tozalanadi (§1.7).
            post.setContactPhone(null);
            post.setContactTelegram(null);
            post.setContactOther(null);
            if (ACTIVE_STATUSES.contains(post.getStatus())) {
                post.setStatus(PostStatus.CLOSED);
                post.setClosedReason(ClosedReason.CANCELLED);
                closed++;
            }
        }
        postRepository.saveAll(posts);

        draftRepository.findByUser_Id(userId).ifPresent(draftRepository::delete);

        List<NotificationSubscription> subscriptions =
                subscriptionRepository.findByUser_IdAndActiveTrueAndDeletedAtIsNull(userId);
        Instant now = Instant.now();
        subscriptions.forEach(subscription -> {
            subscription.setActive(false);
            subscription.setDeletedAt(now);
        });
        subscriptionRepository.saveAll(subscriptions);

        user.setUsername(null);
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setPhoneVerifiedAt(null);
        user.setDeletedAt(now);
        userRepository.save(user);

        audit(userId, ACTION_DELETE, Map.of("closedPosts", closed, "subscriptions", subscriptions.size()));
        log.info("Shaxsiy ma'lumot o'chirildi: user_id={} yopilgan_elon={}", userId, closed);

        return closed;
    }

    private Map<String, Object> profileMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("telegramId", user.getTelegramId());
        map.put("username", user.getUsername());
        map.put("firstName", user.getFirstName());
        map.put("lastName", user.getLastName());
        map.put("phone", user.getPhone());
        map.put("uiLanguage", user.getUiLanguage());
        map.put("role", user.getRole().name());
        map.put("status", user.getStatus().name());
        map.put("verificationLevel", user.getVerificationLevel().name());
        map.put("trustScore", user.getTrustScore());
        map.put("consentTosAt", String.valueOf(user.getConsentTosAt()));
        map.put("consentPrivacyAt", String.valueOf(user.getConsentPrivacyAt()));
        map.put("firstSeenAt", String.valueOf(user.getFirstSeenAt()));
        map.put("lastSeenAt", String.valueOf(user.getLastSeenAt()));
        map.put("referralSource", user.getReferralSource());
        return map;
    }

    private Map<String, Object> postMap(Post post) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", post.getId().toString());
        map.put("postType", post.getPostType().name());
        map.put("direction", post.getDirection().name());
        map.put("originAirport", post.getOriginAirport() == null ? null : post.getOriginAirport().getCode());
        map.put("destAirport", post.getDestAirport() == null ? null : post.getDestAirport().getCode());
        map.put("originCityFree", post.getOriginCityFree());
        map.put("destCityFree", post.getDestCityFree());
        map.put("finalDestination", post.getFinalDestination());
        map.put("departDate", String.valueOf(post.getDepartDate()));
        map.put("deadlineDate", String.valueOf(post.getDeadlineDate()));
        map.put("dateFlexibleDays", post.getDateFlexibleDays());
        map.put("weightKg", post.getWeightKg());
        map.put("weightKgMax", post.getWeightKgMax());
        map.put("priceAmount", post.getPriceAmount());
        map.put("priceCurrency", post.getPriceCurrency() == null ? null : post.getPriceCurrency().name());
        map.put("priceUnit", post.getPriceUnit() == null ? null : post.getPriceUnit().name());
        map.put("comment", post.getComment());
        map.put("contactPhone", post.getContactPhone());
        map.put("contactTelegram", post.getContactTelegram());
        map.put("contactOther", post.getContactOther());
        map.put("status", post.getStatus().name());
        map.put("channelMessageId", post.getChannelMessageId());
        map.put("publishedAt", String.valueOf(post.getPublishedAt()));
        map.put("expiresAt", String.valueOf(post.getExpiresAt()));
        map.put("viewCount", post.getViewCount());
        map.put("contactRevealCount", post.getContactRevealCount());
        map.put("createdAt", String.valueOf(post.getCreatedAt()));
        return map;
    }

    private Map<String, Object> subscriptionMap(NotificationSubscription subscription) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", subscription.getId().toString());
        map.put("postType", subscription.getPostType() == null ? null : subscription.getPostType().name());
        map.put("direction", subscription.getDirection() == null ? null : subscription.getDirection().name());
        map.put("originAirport", subscription.getOriginAirport());
        map.put("destAirport", subscription.getDestAirport());
        map.put("dateFrom", String.valueOf(subscription.getDateFrom()));
        map.put("dateTo", String.valueOf(subscription.getDateTo()));
        map.put("createdAt", String.valueOf(subscription.getCreatedAt()));
        return map;
    }

    private void audit(UUID userId, String action, Map<String, Object> payload) {
        AuditLog entry = new AuditLog();
        entry.setActorId(userId);
        entry.setAction(action);
        entry.setEntity("USER");
        entry.setEntityId(userId.toString());
        entry.setPayload(new LinkedHashMap<>(payload));
        auditLogRepository.save(entry);
    }

    private byte[] serialize(Map<String, Object> export) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(export)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            // Bu yerga tushish — bizning xatomiz, foydalanuvchining emas.
            log.error("Eksportni JSON'ga o'girib bo'lmadi", ex);
            throw new IllegalStateException("Eksport tayyorlanmadi", ex);
        }
    }
}
