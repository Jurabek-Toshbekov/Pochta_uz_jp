package uz.pochtajp.api.miniapp.dto;

import java.time.Instant;
import java.util.Map;
import uz.pochtajp.domain.PostDraft;

/** {@code GET/PUT /api/miniapp/drafts} javobi. */
public record DraftResponse(
        String step,
        Map<String, Object> payload,
        Instant updatedAt
) {

    public static DraftResponse from(PostDraft draft) {
        return new DraftResponse(draft.getStep(), draft.getPayload(), draft.getUpdatedAt());
    }

    /** Draft yo'q holati — Mini App bo'sh formadan boshlaydi. */
    public static DraftResponse empty() {
        return new DraftResponse(null, Map.of(), null);
    }
}
