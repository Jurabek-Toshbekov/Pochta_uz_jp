package uz.pochtajp.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ilova sozlamalari. Sirlar faqat environment variable'dan keladi (CLAUDE.md §1.2).
 *
 * @param adminJwtSecret             ADMIN_JWT_SECRET — admin API tokenini imzolash uchun (4-bosqich)
 * @param adminTelegramIds           ADMIN_TELEGRAM_IDS — avtomatik ADMIN roli beriladigan telegram_id'lar
 * @param adminUrl                   ADMIN_URL — CORS uchun ruxsat berilgan admin domeni (§7.2)
 * @param rateLimitPostsPerDay       kuniga bitta foydalanuvchi bera oladigan e'lon soni (§7.2)
 * @param rateLimitRequestsPerMinute daqiqada API so'rovlari chegarasi (§7.2)
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String adminJwtSecret,
        List<Long> adminTelegramIds,
        String adminUrl,
        int rateLimitPostsPerDay,
        int rateLimitRequestsPerMinute
) {

    public AppProperties {
        adminTelegramIds = adminTelegramIds == null ? List.of() : List.copyOf(adminTelegramIds);
        rateLimitPostsPerDay = rateLimitPostsPerDay <= 0 ? 5 : rateLimitPostsPerDay;
        rateLimitRequestsPerMinute = rateLimitRequestsPerMinute <= 0 ? 60 : rateLimitRequestsPerMinute;
    }

    public boolean isAdmin(long telegramId) {
        return adminTelegramIds.contains(telegramId);
    }
}
