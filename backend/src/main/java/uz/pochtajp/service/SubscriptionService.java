package uz.pochtajp.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.SubscriptionRequest;
import uz.pochtajp.api.miniapp.dto.SubscriptionResponse;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.repository.NotificationSubscriptionRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * Saqlangan qidiruv → obuna (§10.3).
 *
 * <p>Xabarnoma yuborish va mos e'lonni topish 5-bosqichda
 * ({@code NotificationService}). Bu bosqichda obuna <b>yozib olinadi</b>:
 * natijasiz qidiruv paytida "chiqsa xabar beraymi?" degan taklif shu
 * jadvalga tushadi va o'sha payt qoplanmagan talab qayd etiladi (§6.4, 4-band).
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    /** Anti-spam: bitta foydalanuvchida cheksiz obuna bo'lmaydi (§10.3). */
    private static final int MAX_ACTIVE_SUBSCRIPTIONS = 10;

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EventLogger eventLogger;

    public SubscriptionService(NotificationSubscriptionRepository subscriptionRepository,
                               UserRepository userRepository,
                               EventLogger eventLogger) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.eventLogger = eventLogger;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(UUID userId) {
        return subscriptionRepository.findByUser_IdAndActiveTrueAndDeletedAtIsNull(userId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @Transactional
    public SubscriptionResponse create(UUID userId, SubscriptionRequest request) {
        if (!request.hasAnyCriterion()) {
            throw ValidationException.field("filters",
                    "Obuna uchun kamida bitta shart tanlang — aks holda hamma e'lon keladi.");
        }

        List<NotificationSubscription> existing =
                subscriptionRepository.findByUser_IdAndActiveTrueAndDeletedAtIsNull(userId);
        if (existing.size() >= MAX_ACTIVE_SUBSCRIPTIONS) {
            throw ValidationException.field("subscriptions",
                    "Obunalar soni " + MAX_ACTIVE_SUBSCRIPTIONS + " tadan oshmaydi. Keraksizini o'chiring.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));

        NotificationSubscription subscription = new NotificationSubscription();
        subscription.setUser(user);
        subscription.setPostType(request.postType());
        subscription.setDirection(request.direction());
        subscription.setOriginAirport(normalize(request.originAirport()));
        subscription.setDestAirport(normalize(request.destAirport()));
        subscription.setDateFrom(request.dateFrom());
        subscription.setDateTo(request.dateTo());
        subscription.setCategoryIds(request.categoryIds() == null || request.categoryIds().isEmpty()
                ? null
                : request.categoryIds().toArray(new Short[0]));
        subscription.setActive(true);

        NotificationSubscription saved = subscriptionRepository.save(subscription);
        log.info("Obuna yaratildi: user_id={} subscription_id={}", userId, saved.getId());

        eventLogger.track(TrackedEvent.of(EventName.SEARCH_SAVED, EventSource.MINIAPP)
                .user(userId)
                .session(request.sessionId())
                .platform(request.platform())
                .property("post_type", request.postType() == null ? null : request.postType().name())
                .property("direction", request.direction() == null ? null : request.direction().name())
                .property("origin", subscription.getOriginAirport())
                .property("dest", subscription.getDestAirport())
                .property("has_categories", subscription.getCategoryIds() != null)
                .build());

        return SubscriptionResponse.from(saved);
    }

    /** Soft delete (§1.1) — obuna tarixi qoladi, xabarnoma kelmaydi. */
    @Transactional
    public void delete(UUID userId, UUID subscriptionId) {
        NotificationSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Obuna topilmadi."));

        if (!subscription.getUser().getId().equals(userId) || subscription.getDeletedAt() != null) {
            // Begonaning obunasi — mavjudligini oshkor qilmaymiz.
            throw new NotFoundException("Obuna topilmadi.");
        }

        subscription.setActive(false);
        subscription.setDeletedAt(Instant.now());
        subscriptionRepository.save(subscription);
        log.info("Obuna o'chirildi: user_id={} subscription_id={}", userId, subscriptionId);
    }

    private String normalize(String code) {
        return code == null || code.isBlank() ? null : code.strip().toUpperCase();
    }
}
