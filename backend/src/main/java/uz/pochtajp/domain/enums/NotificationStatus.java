package uz.pochtajp.domain.enums;

/**
 * Xabarnoma yuborilish holati.
 *
 * <p>{@code PENDING} — navbatga yozilgan, lekin hali yuborilmagan. Digest job
 * bir foydalanuvchiga tegishli barcha navbatdagilarni bitta xabarga
 * birlashtiradi (§10.3 anti-spam).
 */
public enum NotificationStatus {
    PENDING, SENT, FAILED, BLOCKED
}
