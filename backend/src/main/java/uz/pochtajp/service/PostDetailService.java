package uz.pochtajp.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.ContactResponse;
import uz.pochtajp.api.miniapp.dto.PostDetailResponse;
import uz.pochtajp.api.miniapp.dto.PostSummaryResponse;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.config.BotProperties;
import uz.pochtajp.domain.ContactReveal;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.domain.enums.RevealChannel;
import uz.pochtajp.repository.ContactRevealRepository;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.PostSearchRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * E'lon tafsiloti va kontaktni ochish (§10.2, §6.4 2-band).
 *
 * <p>Kontakt darhol ko'rinmaydi — "Bog'lanish" bosilganda ochiladi. Bu
 * foydalanuvchiga qulaylikni yo'qotmaydi, bizga esa <b>niyat signalini</b>
 * qoldiradi: {@code contact_reveals} jadvali fill rate va match latency
 * metrikalarining yagona manbasi (§6.3).
 */
@Service
public class PostDetailService {

    private static final Logger log = LoggerFactory.getLogger(PostDetailService.class);

    private final PostRepository postRepository;
    private final PostSearchRepository searchRepository;
    private final ContactRevealRepository contactRevealRepository;
    private final UserRepository userRepository;
    private final BotProperties botProperties;
    private final EventLogger eventLogger;

    public PostDetailService(PostRepository postRepository,
                             PostSearchRepository searchRepository,
                             ContactRevealRepository contactRevealRepository,
                             UserRepository userRepository,
                             BotProperties botProperties,
                             EventLogger eventLogger) {
        this.postRepository = postRepository;
        this.searchRepository = searchRepository;
        this.contactRevealRepository = contactRevealRepository;
        this.userRepository = userRepository;
        this.botProperties = botProperties;
        this.eventLogger = eventLogger;
    }

    /** Tafsilot — kontaktsiz. Ko'rish hisoblagichi oshadi (egasi ko'rsa oshmaydi). */
    @Transactional
    public PostDetailResponse detail(UUID postId, UUID viewerId) {
        PostSummaryResponse summary = searchRepository.findPublicById(postId)
                .orElseThrow(() -> new NotFoundException(
                        "E'lon topilmadi — yopilgan yoki muddati tugagan bo'lishi mumkin."));

        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));
        boolean own = post.getUser().getId().equals(viewerId);

        if (!own) {
            postRepository.incrementViewCount(postId);
        }

        boolean revealed = own || contactRevealRepository.existsByPost_IdAndViewer_Id(postId, viewerId);

        return new PostDetailResponse(
                summary,
                own,
                revealed,
                botProperties.deepLinkForPost(postId),
                botProperties.channelUrlForMessage(post.getChannelMessageId()));
    }

    /**
     * Kontaktni ochadi va shu faktni yozib qo'yadi.
     *
     * <p>Bir foydalanuvchi bir e'londa bir marta hisoblanadi — takroriy bosish
     * fill rate'ni shishirmasligi kerak. O'z e'loni umuman hisoblanmaydi.
     */
    @Transactional
    public ContactResponse revealContact(UUID postId, UUID viewerId, UUID sessionId, String platform) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));

        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new NotFoundException("E'lon yopilgan yoki muddati tugagan.");
        }

        UUID ownerId = post.getUser().getId();
        if (ownerId.equals(viewerId)) {
            // O'z e'loni — kontakt ko'rsatiladi, lekin metrikaga tushmaydi.
            return contacts(post, true);
        }

        if (contactRevealRepository.existsByPost_IdAndViewer_Id(postId, viewerId)) {
            return contacts(post, true);
        }

        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));

        ContactReveal reveal = new ContactReveal();
        reveal.setPost(post);
        reveal.setViewer(viewer);
        reveal.setOwner(post.getUser());
        reveal.setChannel(RevealChannel.MINIAPP);
        contactRevealRepository.save(reveal);
        postRepository.incrementContactRevealCount(postId);

        log.info("Kontakt ochildi: post_id={} viewer_id={}", postId, viewerId);

        // Bitim boshlangan payt (§6.1).
        eventLogger.track(TrackedEvent.of(EventName.CONTACT_REVEAL, EventSource.MINIAPP)
                .user(viewerId)
                .session(sessionId)
                .post(postId)
                .platform(platform)
                .property("owner_id", ownerId.toString())
                .property("channel", RevealChannel.MINIAPP.name())
                .build());

        return contacts(post, false);
    }

    private ContactResponse contacts(Post post, boolean alreadyRevealed) {
        return new ContactResponse(
                post.getContactTelegram(),
                post.getContactPhone(),
                post.getContactOther(),
                alreadyRevealed);
    }
}
