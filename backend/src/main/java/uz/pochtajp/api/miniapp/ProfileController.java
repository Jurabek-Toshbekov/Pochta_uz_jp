package uz.pochtajp.api.miniapp;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.miniapp.dto.ProfileResponse;
import uz.pochtajp.api.miniapp.dto.UpdateProfileRequest;
import uz.pochtajp.security.CurrentUser;
import uz.pochtajp.security.MiniAppPrincipal;
import uz.pochtajp.service.ProfileService;
import uz.pochtajp.service.RateLimitService;
import uz.pochtajp.service.UserDataService;

/**
 * {@code /api/miniapp/me} — profil ekrani (§9.1, §12).
 *
 * <p>Foydalanuvchi identifikatori faqat {@code initData}dan olinadi (§7.1),
 * shu sabab yo'lda ham, tanada ham {@code userId} yo'q — uni so'rovdan olish
 * qat'iy taqiqlangan.
 */
@RestController
@RequestMapping("/api/miniapp/me")
public class ProfileController {

    private final ProfileService profileService;
    private final UserDataService userDataService;
    private final RateLimitService rateLimitService;

    public ProfileController(ProfileService profileService,
                             UserDataService userDataService,
                             RateLimitService rateLimitService) {
        this.profileService = profileService;
        this.userDataService = userDataService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ProfileResponse me() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/me");

        return profileService.get(principal.userId());
    }

    /** Til va telefon. Qolgani tizim hisoblaydigan qiymatlar — tahrirlanmaydi. */
    @PatchMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "PATCH /api/miniapp/me");

        return profileService.update(principal.userId(), request);
    }

    /**
     * Ma'lumot eksporti (§7.2 maxfiylik) — botdagi
     * {@code /mening_malumotlarim} bilan bir xil JSON.
     *
     * <p>Fayl sifatida qaytadi: brauzerda ochilib ketmasin, foydalanuvchi
     * uni saqlab qo'ysin.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        MiniAppPrincipal principal = CurrentUser.require();
        rateLimitService.checkApiRequest(principal.userId(), "GET /api/miniapp/me/export");

        byte[] json = userDataService.exportAsJson(principal.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("pochta-malumotlarim.json").build().toString())
                .body(json);
    }
}
