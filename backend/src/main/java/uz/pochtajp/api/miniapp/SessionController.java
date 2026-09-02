package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.SessionRequest;
import uz.pochtajp.api.miniapp.dto.SessionResponse;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.RateLimitService;
import uz.pochtajp.service.UserService;

/**
 * {@code POST /api/miniapp/session} — Mini App ochilganda birinchi so'rov (§12).
 *
 * <p>Foydalanuvchi allaqachon {@code initData} filtrida yaratilgan/yangilangan;
 * bu endpoint profilni qaytaradi va ToS/Privacy roziligini yozadi (§7.2).
 */
@RestController
@RequestMapping("/api/miniapp/session")
public class SessionController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    public SessionController(UserService userService, RateLimitService rateLimitService) {
        this.userService = userService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public SessionResponse open(@Valid @RequestBody(required = false) SessionRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "POST /api/miniapp/session");

        SessionRequest body = request == null ? SessionRequest.empty() : request;
        return SessionResponse.from(
                userService.applySessionPreferences(principal.userId(), body),
                principal.startParam(),
                principal.newUser());
    }
}
