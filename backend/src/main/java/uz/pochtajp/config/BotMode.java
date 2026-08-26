package uz.pochtajp.config;

/**
 * Bot qanday ishlaydi (§13, 2-bosqich).
 *
 * <ul>
 *   <li>{@link #POLLING} — lokal ishlab chiqish: long polling</li>
 *   <li>{@link #WEBHOOK} — prod: Telegram bizga so'rov yuboradi</li>
 *   <li>{@link #OFF} — bot ishga tushmaydi (testlar va faqat API kerak bo'lgan holat)</li>
 * </ul>
 */
public enum BotMode {
    POLLING,
    WEBHOOK,
    OFF
}
