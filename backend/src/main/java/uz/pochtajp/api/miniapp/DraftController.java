package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.DraftRequest;
import uz.pochtajp.api.miniapp.dto.DraftResponse;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.PostDraftService;
import uz.pochtajp.service.RateLimitService;

/**
 * Draft autosave (§12, §6.4 5-band). Har bir foydalanuvchida bitta draft.
 */
@RestController
@RequestMapping("/api/miniapp/drafts")
public class DraftController {

    private final PostDraftService draftService;
    private final RateLimitService rateLimitService;

    public DraftController(PostDraftService draftService, RateLimitService rateLimitService) {
        this.draftService = draftService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public DraftResponse current() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/drafts");

        return draftService.find(principal.userId());
    }

    @PutMapping
    public DraftResponse save(@Valid @RequestBody DraftRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "PUT /api/miniapp/drafts");

        return draftService.save(principal.userId(), request);
    }

    @DeleteMapping
    public ResponseEntity<Void> discard() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "DELETE /api/miniapp/drafts");

        draftService.discard(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
