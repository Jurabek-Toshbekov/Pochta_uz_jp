package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.CreatePostRequest;
import uz.pochtajp.api.miniapp.dto.PostResponse;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.PostService;
import uz.pochtajp.service.PostSubmissionService;
import uz.pochtajp.service.RateLimitService;

/**
 * E'lon yaratish va o'z e'lonlarini ko'rish (§12).
 *
 * <p>Controller'da mantiq yo'q (§14): autentifikatsiya filtrda, chegara
 * {@link RateLimitService}da, qolgani servislarda.
 *
 * <p>Ochiq qidiruv ({@code GET /posts}) va begonaning e'lon tafsiloti
 * 3-bosqichda qo'shiladi — u yerda kontakt ko'rsatilmaydi (§6.4, 2-band).
 */
@RestController
@RequestMapping("/api/miniapp")
public class PostController {

    private final PostSubmissionService submissionService;
    private final PostService postService;
    private final RateLimitService rateLimitService;

    public PostController(PostSubmissionService submissionService,
                          PostService postService,
                          RateLimitService rateLimitService) {
        this.submissionService = submissionService;
        this.postService = postService;
        this.rateLimitService = rateLimitService;
    }

    /** Formani yuborish: e'lon yaratiladi va darhol kanalga chiqariladi. */
    @PostMapping("/posts")
    public ResponseEntity<PostResponse> create(@Valid @RequestBody CreatePostRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/posts");

        PostResponse response = submissionService.submit(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my/posts")
    public List<PostResponse> myPosts() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/my/posts");

        return postService.listOwn(principal.userId());
    }

    @GetMapping("/my/posts/{postId}")
    public PostResponse myPost(@PathVariable UUID postId) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/my/posts/{id}");

        return postService.getOwned(postId, principal.userId());
    }
}
