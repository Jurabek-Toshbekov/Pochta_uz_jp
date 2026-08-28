package uz.pochtajp.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import uz.pochtajp.api.miniapp.dto.CreatePostRequest;
import uz.pochtajp.api.miniapp.dto.PostResponse;
import uz.pochtajp.config.SettingKeys;
import uz.pochtajp.security.MiniAppPrincipal;

/**
 * "Kanalga yuborish" tugmasining to'liq oqimi (§9.2 — Preview + publish).
 *
 * <p>Ataylab alohida klass: e'lonni yozish va kanalga yuborish ikki xil
 * tranzaksiyada bo'lishi kerak. Tashqi API chaqiruvi DB tranzaksiyasini
 * ushlab turmasligi, kanal xatosi esa yozilgan e'lonni rollback qilmasligi shart.
 *
 * <p>{@code moderation.required} feature flag'i yoqilgan bo'lsa (§11.2),
 * e'lon kanalga chiqmaydi va {@code PENDING} holatida admin tasdiqlashini
 * kutadi. Foydalanuvchi ma'lumoti har ikki holatda ham saqlanadi (§1.1).
 */
@Service
public class PostSubmissionService {

    private final PostService postService;
    private final PublishService publishService;
    private final PostDraftService draftService;
    private final SettingsService settingsService;

    public PostSubmissionService(PostService postService,
                                 PublishService publishService,
                                 PostDraftService draftService,
                                 SettingsService settingsService) {
        this.postService = postService;
        this.publishService = publishService;
        this.draftService = draftService;
        this.settingsService = settingsService;
    }

    public PostResponse submit(CreatePostRequest request, MiniAppPrincipal principal) {
        UUID postId = postService.createPending(request, principal);

        if (!settingsService.flag(SettingKeys.MODERATION_REQUIRED, false)) {
            long formStartedAt = request.formStartedAtMs() == null ? 0L : request.formStartedAtMs();
            publishService.publish(postId, request.sessionId(), request.platform(), formStartedAt);
        }

        // Draft o'z vazifasini bajardi — endi tozalanadi (§6.4, 5-band).
        draftService.discard(principal.userId());

        return postService.getOwned(postId, principal.userId());
    }
}
