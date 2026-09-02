package uz.pochtajp.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.DraftRequest;
import uz.pochtajp.api.miniapp.dto.DraftResponse;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.PostDraft;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.repository.PostDraftRepository;
import uz.pochtajp.repository.UserRepository;

/**
 * Forma autosave (§6.4, 5-band). Foydalanuvchida bittadan ko'p draft bo'lmaydi
 * ({@code idx_drafts_user} UNIQUE).
 *
 * <p>Draft o'chirilishi — yagona istisno emas: bu vaqtinchalik ish holati,
 * analitika ma'lumoti emas. Tashlab ketilgan formaning o'zi
 * {@code post_form_abandon} eventida qoladi, shuning uchun draftni tozalash
 * ma'lumot yo'qotmaydi (§1.1).
 */
@Service
public class PostDraftService {

    private static final Logger log = LoggerFactory.getLogger(PostDraftService.class);

    /** JSONB'ga cheksiz ma'lumot yozilmasligi uchun. */
    private static final int MAX_PAYLOAD_KEYS = 64;

    private final PostDraftRepository draftRepository;
    private final UserRepository userRepository;
    private final EventLogger eventLogger;

    public PostDraftService(PostDraftRepository draftRepository,
                            UserRepository userRepository,
                            EventLogger eventLogger) {
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.eventLogger = eventLogger;
    }

    @Transactional(readOnly = true)
    public DraftResponse find(UUID userId) {
        return draftRepository.findByUser_Id(userId)
                .map(DraftResponse::from)
                .orElseGet(DraftResponse::empty);
    }

    @Transactional
    public DraftResponse save(UUID userId, DraftRequest request) {
        Map<String, Object> payload = request.payload();
        if (payload.size() > MAX_PAYLOAD_KEYS) {
            throw ValidationException.field("payload", "Draft juda katta.");
        }

        PostDraft draft = draftRepository.findByUser_Id(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));
            PostDraft created = new PostDraft();
            created.setUser(user);
            return created;
        });
        draft.setStep(request.step());
        draft.setPayload(payload);
        PostDraft saved = draftRepository.save(draft);

        eventLogger.track(TrackedEvent.of(EventName.POST_DRAFT_SAVED, EventSource.MINIAPP)
                .user(userId)
                .property("step", request.step())
                .property("filled_fields", payload.size())
                .build());

        return DraftResponse.from(saved);
    }

    /** Publish bo'lgach yoki foydalanuvchi bekor qilganda. */
    @Transactional
    public void discard(UUID userId) {
        Optional<PostDraft> draft = draftRepository.findByUser_Id(userId);
        if (draft.isPresent()) {
            draftRepository.delete(draft.get());
            log.debug("Draft tozalandi: user_id={}", userId);
        }
    }
}
