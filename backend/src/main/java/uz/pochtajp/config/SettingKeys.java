package uz.pochtajp.config;

/**
 * `app_settings` jadvalidagi kalitlar (§11.2).
 *
 * <p>Kalit matnini kod bo'ylab tarqatmaslik uchun bitta joyda. Yangi
 * sozlama qo'shilganda migratsiyaga qatorni va shu yerga konstantani
 * qo'shish kerak.
 */
public final class SettingKeys {

    /** Yoqilsa e'lon darhol kanalga chiqmaydi, admin tasdiqlaydi. */
    public static final String MODERATION_REQUIRED = "moderation.required";

    /** VIP e'lon (6-bosqich). */
    public static final String VIP_ENABLED = "vip.enabled";

    public static final String RATE_LIMIT_POSTS_PER_DAY = "rate_limit.posts_per_day";
    public static final String RATE_LIMIT_REQUESTS_PER_MINUTE = "rate_limit.requests_per_minute";
    public static final String NOTIFICATIONS_MAX_PER_DAY = "notifications.max_per_day";

    private SettingKeys() {
    }
}
