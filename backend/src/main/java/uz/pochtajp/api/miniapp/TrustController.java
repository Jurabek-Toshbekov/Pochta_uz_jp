package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.NotificationOpenedRequest;
import uz.pochtajp.api.miniapp.dto.ReportRequest;
import uz.pochtajp.api.miniapp.dto.ReviewRequest;
import uz.pochtajp.api.miniapp.dto.TrustResponse;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.NotificationService;
import uz.pochtajp.service.RateLimitService;
import uz.pochtajp.service.ReportService;
import uz.pochtajp.service.ReviewService;

/**
 * Ishonch va xavfsizlik endpoint'lari (§12).
 *
 * <p>Uchtasi bir joyda, chunki uchalasi ham bitta narsaga xizmat qiladi:
 * platformaga ishonish mumkinmi. Shikoyat — salbiy signal, baho — ijobiy,
 * xabarnoma ochilishi esa xabarnoma keraklimi degan savolga javob.
 *
 * <p>Harakat qiluvchi shaxs faqat {@link CurrentUser} dan olinadi (§7.1).
 */
@RestController
@RequestMapping("/api/miniapp")
public class TrustController {

    private final ReportService reportService;
    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;

    public TrustController(ReportService reportService,
                           ReviewService reviewService,
                           NotificationService notificationService,
                           RateLimitService rateLimitService) {
        this.reportService = reportService;
        this.reviewService = reviewService;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
    }

    /** Shikoyat (§7.3). E'lon darhol yopilmaydi — qaror moderatorniki. */
    @PostMapping("/reports")
    public ResponseEntity<TrustResponse> report(@Valid @RequestBody ReportRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/reports");

        var id = reportService.submit(request.postId(), principal.userId(),
                request.reason(), request.details());
        return ResponseEntity.status(HttpStatus.CREATED).body(TrustResponse.reported(id));
    }

    /** Bitim sherigiga baho (§6.4, 7-band). */
    @PostMapping("/reviews")
    public ResponseEntity<TrustResponse> review(@Valid @RequestBody ReviewRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/reviews");

        int score = reviewService.leave(request.postId(), principal.userId(),
                request.rating(), request.comment(), EventSource.MINIAPP);
        return ResponseEntity.status(HttpStatus.CREATED).body(TrustResponse.reviewed(score));
    }

    /**
     * Xabarnomadagi havola ochildi.
     *
     * <p>Javob tanasi yo'q: bu faqat belgi qo'yish. Xabarnoma topilmasa ham
     * xato qaytarilmaydi — foydalanuvchi e'lonni boshqa yo'l bilan ochgan
     * bo'lishi mumkin va bu xato emas.
     */
    @PostMapping("/notifications/opened")
    public ResponseEntity<Void> notificationOpened(
            @Valid @RequestBody NotificationOpenedRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        notificationService.markOpened(request.postId(), principal.userId());
        return ResponseEntity.noContent().build();
    }
}
