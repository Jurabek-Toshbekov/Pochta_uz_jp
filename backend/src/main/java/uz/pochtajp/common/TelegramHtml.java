package uz.pochtajp.common;

/**
 * Telegram HTML parse_mode uchun matn tayyorlash (§7.2).
 *
 * <p>Foydalanuvchi kiritgan har bir matn kanalga chiqishdan oldin shu yerdan
 * o'tadi. Aks holda izohdagi {@code <b>} yoki tugallanmagan teg butun xabarni
 * buzadi, yomon holatda esa boshqa foydalanuvchiga havola yasab beradi.
 */
public final class TelegramHtml {

    private TelegramHtml() {
    }

    /** Telegram talab qiladigan uchta belgi: {@code & < >}. */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Hashtag uchun xavfsiz qism: faqat harf, raqam va pastki chiziq qoldiriladi.
     * Bo'sh natija qaytsa hashtag qo'shilmaydi.
     */
    public static String hashtagToken(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
