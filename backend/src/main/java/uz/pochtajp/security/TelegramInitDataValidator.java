package uz.pochtajp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.pochtajp.common.exception.InitDataInvalidException;
import uz.pochtajp.config.BotProperties;

/**
 * Telegram Mini App {@code initData} imzosini tekshiradi (CLAUDE.md §7.1).
 *
 * <p>Algoritm:
 * <ol>
 *   <li>{@code initData} query-string sifatida tahlil qilinadi, {@code hash} ajratiladi</li>
 *   <li>qolgan juftliklar kalit bo'yicha saralanib {@code \n} bilan birlashtiriladi</li>
 *   <li>{@code secret_key = HMAC_SHA256(key="WebAppData", data=BOT_TOKEN)}</li>
 *   <li>{@code computed = HMAC_SHA256(key=secret_key, data=data_check_string)}</li>
 *   <li>{@code computed != hash} bo'lsa — 401</li>
 *   <li>{@code auth_date} eskirgan bo'lsa — 401</li>
 * </ol>
 *
 * <p>MUHIM: {@code user.id} faqat shu yerdan olinadi. Request body'dagi
 * {@code user_id} ga ishonish qat'iy taqiqlangan (§7.1).
 */
@Component
public class TelegramInitDataValidator {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataValidator.class);

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final byte[] WEB_APP_DATA_KEY = "WebAppData".getBytes(StandardCharsets.UTF_8);
    private static final String HASH_FIELD = "hash";

    private final ObjectMapper objectMapper;
    private final byte[] secretKey;
    private final Duration maxAge;

    public TelegramInitDataValidator(BotProperties botProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // secret_key bir marta hisoblanadi — token log'ga hech qachon chiqmaydi.
        this.secretKey = hmac(WEB_APP_DATA_KEY, botProperties.token().getBytes(StandardCharsets.UTF_8));
        long seconds = botProperties.initDataMaxAgeSeconds() > 0 ? botProperties.initDataMaxAgeSeconds() : 86_400L;
        this.maxAge = Duration.ofSeconds(seconds);
    }

    /**
     * @param initDataRaw {@code Authorization: tma <initDataRaw>} headeridan olingan xom qiymat
     * @return tekshirilgan ma'lumot
     * @throws InitDataInvalidException imzo, format yoki muddat yaroqsiz bo'lsa
     */
    public TelegramInitData validate(String initDataRaw) {
        if (initDataRaw == null || initDataRaw.isBlank()) {
            throw new InitDataInvalidException("initData bo'sh");
        }

        Map<String, String> fields = parseQueryString(initDataRaw);
        String receivedHash = fields.remove(HASH_FIELD);
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new InitDataInvalidException("initData'da hash yo'q");
        }

        String dataCheckString = buildDataCheckString(fields);
        byte[] computed = hmac(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(computed, decodeHex(receivedHash))) {
            // Xom initData log'ga yozilmaydi (§1.7).
            log.warn("initData imzosi mos kelmadi");
            throw new InitDataInvalidException("initData imzosi yaroqsiz");
        }

        Instant authDate = parseAuthDate(fields.get("auth_date"));
        if (authDate.plus(maxAge).isBefore(Instant.now())) {
            log.warn("initData muddati o'tgan: authDate={}", authDate);
            throw new InitDataInvalidException("initData muddati o'tgan");
        }

        TelegramWebAppUser user = parseUser(fields.get("user"));
        if (user == null || user.id() == null) {
            throw new InitDataInvalidException("initData'da foydalanuvchi yo'q");
        }

        return new TelegramInitData(
                user,
                authDate,
                emptyToNull(fields.get("start_param")),
                emptyToNull(fields.get("chat_type")),
                emptyToNull(fields.get("query_id"))
        );
    }

    /** Query-string -> map. Kalit ham, qiymat ham URL-decode qilinadi. */
    private Map<String, String> parseQueryString(String raw) {
        Map<String, String> result = new HashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                result.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ex) {
                throw new InitDataInvalidException("initData formati buzilgan");
            }
        }
        if (result.isEmpty()) {
            throw new InitDataInvalidException("initData formati buzilgan");
        }
        return result;
    }

    /** {@code key=value} juftliklari alifbo tartibida, {@code \n} bilan. */
    private String buildDataCheckString(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>(fields.size());
        fields.forEach((key, value) -> pairs.add(key + "=" + value));
        pairs.sort(String::compareTo);
        return String.join("\n", pairs);
    }

    private Instant parseAuthDate(String value) {
        if (value == null || value.isBlank()) {
            throw new InitDataInvalidException("initData'da auth_date yo'q");
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            throw new InitDataInvalidException("auth_date formati to'g'ri emas");
        }
    }

    private TelegramWebAppUser parseUser(String userJson) {
        if (userJson == null || userJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(userJson, TelegramWebAppUser.class);
        } catch (Exception ex) {
            // JSON matni log'ga yozilmaydi — ichida ism bor (§1.7).
            log.warn("initData user JSON'ini o'qib bo'lmadi");
            throw new InitDataInvalidException("initData'dagi foydalanuvchi ma'lumoti buzilgan");
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 hisoblab bo'lmadi", ex);
        }
    }

    private static byte[] decodeHex(String hex) {
        if (hex.length() % 2 != 0) {
            throw new InitDataInvalidException("hash formati to'g'ri emas");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new InitDataInvalidException("hash formati to'g'ri emas");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
