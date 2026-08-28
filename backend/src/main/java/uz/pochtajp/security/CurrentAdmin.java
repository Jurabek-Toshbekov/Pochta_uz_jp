package uz.pochtajp.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.pochtajp.common.exception.ForbiddenException;

/**
 * Joriy adminni olishning yagona yo'li (§7.1 ruhi).
 *
 * <p>Controller {@code actor_id} ni so'rov tanasidan olmaydi — faqat shu
 * klassdan. Aks holda moderatsiya jurnalini soxtalashtirish mumkin bo'lardi.
 */
public final class CurrentAdmin {

    private CurrentAdmin() {
    }

    public static Optional<AdminPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AdminAuthentication admin && admin.isAuthenticated()) {
            return Optional.of(admin.getPrincipal());
        }
        return Optional.empty();
    }

    public static AdminPrincipal require() {
        return find().orElseThrow(() -> new ForbiddenException("Admin sessiyasi topilmadi."));
    }

    public static UUID requireId() {
        return require().userId();
    }
}
