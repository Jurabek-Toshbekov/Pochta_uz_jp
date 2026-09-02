package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.SubscriptionRequest;
import uz.pochtajp.api.miniapp.dto.SubscriptionResponse;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.RateLimitService;
import uz.pochtajp.service.SubscriptionService;

/** Xabarnoma obunalari (§10.3, §12). */
@RestController
@RequestMapping("/api/miniapp/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final RateLimitService rateLimitService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  RateLimitService rateLimitService) {
        this.subscriptionService = subscriptionService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public List<SubscriptionResponse> list() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/subscriptions");

        return subscriptionService.list(principal.userId());
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/subscriptions");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.create(principal.userId(), request));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID subscriptionId) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "DELETE /api/miniapp/subscriptions/{id}");

        subscriptionService.delete(principal.userId(), subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
