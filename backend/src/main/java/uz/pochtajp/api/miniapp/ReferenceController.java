package uz.pochtajp.api.miniapp;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.ReferenceResponse;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.RateLimitService;
import uz.pochtajp.service.ReferenceService;

/**
 * {@code GET /api/miniapp/reference} — aeroportlar, kategoriyalar, koridorlar (§12).
 *
 * <p>Javob 1 soat keshlanadi: serverda ham, klientda ham.
 */
@RestController
@RequestMapping("/api/miniapp/reference")
public class ReferenceController {

    private static final long CLIENT_CACHE_SECONDS = 3600;

    private final ReferenceService referenceService;
    private final RateLimitService rateLimitService;

    public ReferenceController(ReferenceService referenceService, RateLimitService rateLimitService) {
        this.referenceService = referenceService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ResponseEntity<ReferenceResponse> reference() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/reference");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(CLIENT_CACHE_SECONDS)).cachePrivate())
                .body(referenceService.load());
    }
}
