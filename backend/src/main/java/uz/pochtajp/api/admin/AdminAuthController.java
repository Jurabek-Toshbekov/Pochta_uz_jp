package uz.pochtajp.api.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.api.admin.dto.AdminRequests;
import uz.pochtajp.service.AdminAuthService;

/**
 * Admin panelga kirish (§11.1, §12).
 *
 * <p>Yagona ochiq admin endpoint'i — bu yerda token hali yo'q. Kod
 * botdan olinadi ({@code /admin} buyrug'i), 5 daqiqa yashaydi va bir
 * marta ishlaydi.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    /** Kodni token juftligiga almashtiradi. */
    @PostMapping("/telegram")
    public AdminDto.LoginResponse login(@Valid @RequestBody AdminRequests.LoginRequest request) {
        return toResponse(authService.exchangeCode(request.code()));
    }

    /** Access muddati tugaganda. */
    @PostMapping("/refresh")
    public AdminDto.LoginResponse refresh(@Valid @RequestBody AdminRequests.RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    private static AdminDto.LoginResponse toResponse(AdminAuthService.TokenPair pair) {
        return new AdminDto.LoginResponse(
                pair.accessToken(),
                pair.refreshToken(),
                pair.expiresInSeconds(),
                pair.userId(),
                pair.role().name());
    }
}
