package uz.pochtajp.security;

import java.util.UUID;
import uz.pochtajp.domain.enums.UserRole;

/**
 * Admin panelga kirgan xodim (§11.1).
 *
 * <p>Faqat tekshirilgan JWT'dan yasaladi. Mini App foydalanuvchisidan
 * ataylab alohida tur: ikkalasi bir xil {@code SecurityContext}da yurmasin,
 * chunki huquqlar butunlay boshqacha.
 */
public record AdminPrincipal(
        UUID userId,
        long telegramId,
        UserRole role
) {

    public boolean canModerate() {
        return role == UserRole.MODERATOR || role == UserRole.ADMIN;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
