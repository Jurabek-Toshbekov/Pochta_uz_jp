package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.ContactResponse;
import uz.pochtajp.api.miniapp.dto.PostDetailResponse;
import uz.pochtajp.api.miniapp.dto.PostSearchRequest;
import uz.pochtajp.api.miniapp.dto.PostSearchResponse;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.PostDetailService;
import uz.pochtajp.service.PostSearchService;
import uz.pochtajp.service.RateLimitService;

/**
 * Qidiruv va e'lon tafsiloti (§10, §12).
 *
 * <p>Sessiya va platforma header'da keladi ({@code X-Session-Id}, {@code X-Platform}) —
 * ular query'ga qo'shilsa kesh kalitlarini va {@code search_queries} tahlilini
 * chalkashtirardi.
 */
@RestController
@RequestMapping("/api/miniapp/posts")
public class SearchController {

    private final PostSearchService searchService;
    private final PostDetailService detailService;
    private final RateLimitService rateLimitService;

    public SearchController(PostSearchService searchService,
                           PostDetailService detailService,
                           RateLimitService rateLimitService) {
        this.searchService = searchService;
        this.detailService = detailService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public PostSearchResponse search(@Valid @ModelAttribute PostSearchRequest request,
                                     @RequestHeader(name = "X-Session-Id", required = false) UUID sessionId,
                                     @RequestHeader(name = "X-Platform", required = false) String platform) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/posts");

        return searchService.search(request, principal.userId(),
                new PostSearchRequest.Context(sessionId, platform));
    }

    /** Tafsilot — kontaktsiz (§6.4, 2-band). */
    @GetMapping("/{postId}")
    public PostDetailResponse detail(@PathVariable UUID postId) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/posts/{id}");

        return detailService.detail(postId, principal.userId());
    }

    /** "Bog'lanish" — kontakt ochiladi va bu fakt yozib qo'yiladi. */
    @PostMapping("/{postId}/reveal-contact")
    public ContactResponse revealContact(
            @PathVariable UUID postId,
            @RequestHeader(name = "X-Session-Id", required = false) UUID sessionId,
            @RequestHeader(name = "X-Platform", required = false) String platform) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/posts/{id}/reveal-contact");

        return detailService.revealContact(postId, principal.userId(), sessionId, platform);
    }
}
