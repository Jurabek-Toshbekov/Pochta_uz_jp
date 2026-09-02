package uz.pochtajp.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Testlar uchun haqiqiy imzolangan {@code initData} yasaydi (§7.1).
 *
 * <p>Imzo aynan Telegram algoritmi bilan hisoblanadi — shu sabab bu klass
 * {@code TelegramInitDataValidator} uchun mustaqil tekshiruv bo'lib xizmat qiladi.
 */
public final class InitDataFactory {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private InitDataFactory() {
    }

    /** Standart test foydalanuvchisi bilan yaroqli initData. */
    public static String valid(String botToken, long telegramId) {
        return build(botToken, fields(telegramId, Instant.now(), null));
    }

    public static String validWithStartParam(String botToken, long telegramId, String startParam) {
        return build(botToken, fields(telegramId, Instant.now(), startParam));
    }

    /** {@code auth_date} eskirgan initData — 401 kutiladi. */
    public static String expired(String botToken, long telegramId, long ageSeconds) {
        return build(botToken, fields(telegramId, Instant.now().minusSeconds(ageSeconds), null));
    }

    /** Imzosi buzilgan initData — 401 kutiladi. */
    public static String tampered(String botToken, long telegramId) {
        String raw = valid(botToken, telegramId);
        // hash to'g'ri, lekin ma'lumot imzodan keyin o'zgargan holat.
        return raw.replace("first_name", "first_nam3");
    }

    private static Map<String, String> fields(long telegramId, Instant authDate, String startParam) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user", """
                {"id":%d,"first_name":"Test","last_name":"Foydalanuvchi",\
                "username":"testuser","language_code":"uz","is_premium":false}"""
                .formatted(telegramId));
        fields.put("auth_date", String.valueOf(authDate.getEpochSecond()));
        fields.put("chat_type", "private");
        if (startParam != null) {
            fields.put("start_param", startParam);
        }
        return fields;
    }

    /** {@code hash} ni hisoblab, query-string ko'rinishida qaytaradi. */
    public static String build(String botToken, Map<String, String> fields) {
        Map<String, String> sorted = new TreeMap<>(fields);
        StringBuilder dataCheckString = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (dataCheckString.length() > 0) {
                dataCheckString.append('\n');
            }
            dataCheckString.append(key).append('=').append(value);
        });

        byte[] secretKey = hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                botToken.getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hmac(secretKey, dataCheckString.toString().getBytes(StandardCharsets.UTF_8)));

        StringBuilder query = new StringBuilder();
        fields.forEach((key, value) -> {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        query.append("&hash=").append(hash);
        return query.toString();
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
