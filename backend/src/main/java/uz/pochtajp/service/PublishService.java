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
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.domain.CargoCategory;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.PostCategory;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.repository.PostCategoryRepository;
import uz.pochtajp.repository.PostRepository;

/**
 * E'lonni kanalga chiqaradi va {@code channel_message_id}ni saqlaydi (§8.3).
 *
 * <p>Xato bo'lganda e'lon <b>o'chirilmaydi</b> va {@code PENDING} holatida
 * qoladi (§1.1): keyinroq admin qo'lda yoki qayta urinish bilan chiqarishi
 * mumkin. Foydalanuvchi ma'lumoti hech qachon yo'qolmaydi.
 */
@Service
public class PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);

    private final PostRepository postRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final ChannelPostFormatter formatter;
    private final ChannelPublisher channelPublisher;
    private final EventLogger eventLogger;
    private final NotificationService notificationService;

    public PublishService(PostRepository postRepository,
                          PostCategoryRepository postCategoryRepository,
                          ChannelPostFormatter formatter,
                          ChannelPublisher channelPublisher,
                          EventLogger eventLogger,
                          NotificationService notificationService) {
        this.postRepository = postRepository;
        this.postCategoryRepository = postCategoryRepository;
        this.formatter = formatter;
        this.channelPublisher = channelPublisher;
        this.eventLogger = eventLogger;
        this.notificationService = notificationService;
    }

    /**
     * @return kanalga chiqqan bo'lsa {@code true}
     */
    @Transactional
    public boolean publish(UUID postId, UUID sessionId, String platform, long formStartedAtMillis) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));

        if (post.getStatus() == PostStatus.PUBLISHED) {
            return true;
        }
        if (post.getStatus() != PostStatus.PENDING) {
            log.warn("Publish o'tkazib yuborildi: post_id={} status={}", postId, post.getStatus());
            return false;
        }

        List<CargoCategory> categories = postCategoryRepository
                .findWithCategoryByPostIds(List.of(postId)).stream()
                .map(PostCategory::getCategory)
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .toList();

        String html = formatter.format(post, categories);

        try {
            long messageId = channelPublisher.publishToChannel(html);
            post.setChannelMessageId(messageId);
            post.setStatus(PostStatus.PUBLISHED);
            post.setPublishedAt(Instant.now());
            postRepository.save(post);

            eventLogger.track(TrackedEvent.of(EventName.POST_PUBLISH_SUCCESS, EventSource.MINIAPP)
                    .user(post.getUser().getId())
                    .session(sessionId)
                    .post(postId)
                    .platform(platform)
                    .property("channel_message_id", messageId)
                    .property("total_time_ms", elapsed(formStartedAtMillis))
                    .build());

            // Obuna bo'lganlarga xabar (§10.3). Alohida oqimda: xabarnoma
            // qidirish publish javobini kechiktirmasligi kerak va uning
            // xatosi e'lonni rollback qilmasligi shart.
            notificationService.enqueueMatches(postId);
            return true;

        } catch (ChannelPublisher.ChannelPublishException ex) {
            // E'lon PENDING holatida qoladi — moderatsiya navbatida ko'rinadi (§11.2).
            log.error("Kanalga yuborilmadi: post_id={} error_code={}", postId, ex.getErrorCode(), ex);
            eventLogger.track(TrackedEvent.of(EventName.POST_PUBLISH_FAIL, EventSource.MINIAPP)
                    .user(post.getUser().getId())
                    .session(sessionId)
                    .post(postId)
                    .platform(platform)
                    .property("error_code", ex.getErrorCode())
                    .build());
            return false;
        }
    }

    private long elapsed(long formStartedAtMillis) {
        if (formStartedAtMillis <= 0) {
            return 0;
        }
        return Math.max(0, System.currentTimeMillis() - formStartedAtMillis);
    }
}
