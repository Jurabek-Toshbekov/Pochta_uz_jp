package uz.pochtajp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.common.exception.RateLimitException;
import uz.pochtajp.domain.AdminLoginCode;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.AdminTokenType;
import uz.pochtajp.domain.enums.UserRole;
import uz.pochtajp.domain.enums.UserStatus;
import uz.pochtajp.repository.AdminLoginCodeRepository;
import uz.pochtajp.repository.UserRepository;
import uz.pochtajp.security.AdminJwtService;
import uz.pochtajp.security.AdminPrincipal;

/**
 * Admin panelga kirish oqimi (§11.1).
 *
 * <p>Nima uchun bir martalik kod, parol emas: parol saqlash, tiklash va
 * o'g'irlanish muammolarini olib keladi. Telegram allaqachon shaxsni
 * tasdiqlagan — bot faqat qisqa umrli kod beradi, panel uni tokenga
 * almashtiradi.
 *
 * <p>Kodning o'zi bazada saqlanmaydi — faqat SHA-256. Kod 5 daqiqa
 * yashaydi va bir marta ishlaydi.
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    /** Chalkashtirmaydigan alifbo: 0/O va 1/I/L olib tashlangan. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    /** Bitta odam 10 daqiqada shuncha kod so'ray oladi (§7.2). */
    private static final int MAX_CODES_PER_WINDOW = 5;
    private static final Duration CODE_WINDOW = Duration.ofMinutes(10);

    private final AdminLoginCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final AdminJwtService jwtService;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public AdminAuthService(AdminLoginCodeRepository codeRepository,
                            UserRepository userRepository,
                            AdminJwtService jwtService,
                            AuditService auditService) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /** Panelga kirish uchun berilgan token juftligi. */
    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds,
                            UUID userId, UserRole role) {
    }

    /**
     * Bot chaqiradi: adminga bir martalik kod beradi.
     *
     * @return kodning ochiq matni — faqat shaxsiy chatga yuboriladi va
     *         hech qayerda saqlanmaydi
     */
    @Transactional
    public String issueLoginCode(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ForbiddenException("Foydalanuvchi topilmadi."));

        requireStaff(user);
        if (!jwtService.isEnabled()) {
            throw new ForbiddenException("Admin paneli sozlanmagan.");
        }

        Instant windowStart = Instant.now().minus(CODE_WINDOW);
        if (codeRepository.countByUserIdAndCreatedAtAfter(userId, windowStart) >= MAX_CODES_PER_WINDOW) {
            throw new RateLimitException("Juda ko'p urinish. 10 daqiqadan keyin qayta so'rang.",
                    CODE_WINDOW.toSeconds());
        }

        String code = generateCode();
        AdminLoginCode entity = new AdminLoginCode();
        entity.setUserId(userId);
        entity.setCodeHash(sha256Hex(code));
        entity.setExpiresAt(Instant.now().plus(CODE_TTL));
        codeRepository.save(entity);

        log.info("Admin kirish kodi berildi: user_id={}", userId);
        return code;
    }

    /** Panel chaqiradi: kodni token juftligiga almashtiradi. */
    @Transactional
    public TokenPair exchangeCode(String rawCode) {
        String normalized = normalize(rawCode);
        if (normalized.length() != CODE_LENGTH) {
            throw new ForbiddenException("Kod noto'g'ri yoki muddati tugagan.");
        }

        AdminLoginCode code = codeRepository
                .findFirstByCodeHashAndUsedAtIsNullAndDeletedAtIsNull(sha256Hex(normalized))
                .orElseThrow(() -> new ForbiddenException("Kod noto'g'ri yoki muddati tugagan."));

        if (code.getExpiresAt().isBefore(Instant.now())) {
            throw new ForbiddenException("Kod noto'g'ri yoki muddati tugagan.");
        }

        User user = userRepository.findById(code.getUserId())
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ForbiddenException("Foydalanuvchi topilmadi."));
        requireStaff(user);

        // Kod bir marta ishlaydi. Yozuv o'chirilmaydi (§1.1) — ishlatilgan deb belgilanadi.
        code.setUsedAt(Instant.now());
        codeRepository.save(code);

        auditService.record(user.getId(), "ADMIN_LOGIN", "USER", user.getId().toString(),
                Map.of("method", "bot_code"));
        log.info("Admin panelga kirdi: user_id={}", user.getId());

        return issuePair(user);
    }

    /** Access muddati tugaganda panel shu bilan yangilaydi. */
    @Transactional(readOnly = true)
    public TokenPair refresh(String refreshToken) {
        AdminPrincipal fromToken = jwtService.parse(refreshToken, AdminTokenType.REFRESH);
        User user = userRepository.findById(fromToken.userId())
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ForbiddenException("Huquq bekor qilingan."));
        requireStaff(user);
        return issuePair(user);
    }

    private TokenPair issuePair(User user) {
        AdminPrincipal principal = new AdminPrincipal(user.getId(), user.getTelegramId(), user.getRole());
        return new TokenPair(
                jwtService.issueAccessToken(principal),
                jwtService.issueRefreshToken(principal),
                AdminJwtService.ACCESS_TTL.toSeconds(),
                user.getId(),
                user.getRole());
    }

    private static void requireStaff(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ForbiddenException("Hisob bloklangan.");
        }
        UserRole role = user.getRole();
        if (role != UserRole.ADMIN && role != UserRole.MODERATOR) {
            throw new ForbiddenException("Sizda admin paneliga kirish huquqi yo'q.");
        }
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    /** Foydalanuvchi kichik harfda yoki bo'shliq bilan kiritishi mumkin. */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.strip().replace(" ", "").replace("-", "").toUpperCase();
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 mavjud emas", ex);
        }
    }
}
