package uz.pochtajp.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.config.AppProperties;
import uz.pochtajp.domain.enums.AdminTokenType;
import uz.pochtajp.domain.enums.UserRole;

/**
 * Admin API tokenlari (§11.1).
 *
 * <p>HS256, kalit {@code ADMIN_JWT_SECRET}dan olinadi. Sir ixtiyoriy
 * uzunlikda bo'lishi mumkin — undan SHA-256 bilan 256-bitli kalit
 * hosil qilinadi, chunki HS256 shuncha talab qiladi.
 *
 * <p>Ikki xil token:
 * <ul>
 *   <li>{@code ACCESS} — 2 soat, API'ga kiritadi</li>
 *   <li>{@code REFRESH} — 14 kun, faqat yangi juftlik olish uchun</li>
 * </ul>
 *
 * <p>Sir sozlanmagan bo'lsa xizmat <b>o'chiq</b> turadi va har qanday
 * urinish 403 bilan tugaydi. Bu ataylab: sirsiz admin panel ochiq
 * eshik bo'lardi.
 */
@Service
public class AdminJwtService {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtService.class);

    private static final String ISSUER = "pochta-admin";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_TELEGRAM_ID = "tid";

    public static final Duration ACCESS_TTL = Duration.ofHours(2);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final boolean enabled;

    public AdminJwtService(AppProperties appProperties) {
        String secret = appProperties.adminJwtSecret();
        this.enabled = secret != null && !secret.isBlank();
        if (!enabled) {
            log.warn("ADMIN_JWT_SECRET sozlanmagan — admin API butunlay yopiq turadi");
            this.encoder = null;
            this.decoder = null;
            return;
        }
        SecretKeySpec key = deriveKey(secret);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** HS256 uchun 256-bit kalit: sirning SHA-256 xesh natijasi. */
    private static SecretKeySpec deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "HmacSHA256");
        } catch (NoSuchAlgorithmException ex) {
            // Har bir JVM'da SHA-256 bor — bu yerga yetib kelinmaydi.
            throw new IllegalStateException("SHA-256 mavjud emas", ex);
        }
    }

    public String issueAccessToken(AdminPrincipal principal) {
        return issue(principal, AdminTokenType.ACCESS, ACCESS_TTL);
    }

    public String issueRefreshToken(AdminPrincipal principal) {
        return issue(principal, AdminTokenType.REFRESH, REFRESH_TTL);
    }

    private String issue(AdminPrincipal principal, AdminTokenType type, Duration ttl) {
        requireEnabled();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(principal.userId().toString())
                .claim(CLAIM_TELEGRAM_ID, principal.telegramId())
                .claim(CLAIM_ROLE, principal.role().name())
                .claim(CLAIM_TYPE, type.name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /**
     * Tokenni tekshiradi va ichidagi da'voni qaytaradi.
     *
     * @throws ForbiddenException imzo, muddat yoki tur mos kelmasa
     */
    public AdminPrincipal parse(String token, AdminTokenType expectedType) {
        requireEnabled();
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException ex) {
            // Token matni log'ga yozilmaydi.
            throw new ForbiddenException("Token yaroqsiz. Qaytadan kiring.");
        }
        // `getIssuer()` qiymatni URL deb o'qishga urinadi — bizniki URL emas,
        // shuning uchun da'vo matn sifatida olinadi.
        if (!ISSUER.equals(jwt.getClaimAsString("iss"))) {
            throw new ForbiddenException("Token yaroqsiz. Qaytadan kiring.");
        }
        if (!expectedType.name().equals(jwt.getClaimAsString(CLAIM_TYPE))) {
            throw new ForbiddenException("Token turi mos emas. Qaytadan kiring.");
        }
        UserRole role = parseRole(jwt.getClaimAsString(CLAIM_ROLE));
        Long telegramId = jwt.getClaim(CLAIM_TELEGRAM_ID);
        return new AdminPrincipal(UUID.fromString(jwt.getSubject()),
                telegramId == null ? 0L : telegramId, role);
    }

    private static UserRole parseRole(String raw) {
        try {
            return UserRole.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ForbiddenException("Token yaroqsiz. Qaytadan kiring.");
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ForbiddenException("Admin paneli sozlanmagan.");
        }
    }
}
