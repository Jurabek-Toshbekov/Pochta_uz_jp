package uz.pochtajp.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.pochtajp.common.exception.InitDataInvalidException;

/**
 * Joriy foydalanuvchini olishning yagona yo'li.
 *
 * <p>Controller'lar {@code user_id} ni request body yoki query'dan olmaydi —
 * bu qat'iy taqiqlangan (§7.1). Faqat shu klass ishlatiladi.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<MiniAppPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MiniAppAuthentication miniApp && miniApp.isAuthenticated()) {
            return Optional.of(miniApp.getPrincipal());
        }
        return Optional.empty();
    }

    public static MiniAppPrincipal require() {
        return find().orElseThrow(() -> new InitDataInvalidException("Sessiya topilmadi"));
    }

    public static UUID requireId() {
        return require().userId();
    }
}
