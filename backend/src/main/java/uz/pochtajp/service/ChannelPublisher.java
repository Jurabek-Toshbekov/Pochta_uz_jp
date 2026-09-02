package uz.pochtajp.service;

/**
 * Kanalga xabar yuboruvchi. Interfeys ataylab: testlarda haqiqiy Telegram
 * API'ga chiqmasdan publish oqimini tekshirish uchun (§14).
 */
public interface ChannelPublisher {

    /**
     * @param html Telegram HTML {@code parse_mode} formatidagi matn
     * @return kanaldagi xabar ID'si ({@code channel_message_id})
     * @throws ChannelPublishException yuborib bo'lmasa
     */
    long publishToChannel(String html);

    /**
     * Kanaldagi mavjud postni yangilaydi (e'lon tahrirlanganda, §12 PATCH).
     *
     * <p>Kanalda eski narx yoki sana turib qolsa, odam noto'g'ri ma'lumot
     * bilan bog'lanadi — shuning uchun tahrir kanalga ham yetkaziladi.
     *
     * @param messageId {@code channel_message_id}
     * @throws ChannelPublishException yangilab bo'lmasa
     */
    void editChannelMessage(long messageId, String html);

    /** Kanalga yuborish imkoni bormi (token/chat_id sozlanganmi). */
    boolean isConfigured();

    /** Publish xatosi — sabab kodi bilan, chunki u {@code post_publish_fail} eventiga tushadi. */
    class ChannelPublishException extends RuntimeException {

        private final String errorCode;

        public ChannelPublishException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public ChannelPublishException(String errorCode, String message) {
            this(errorCode, message, null);
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
