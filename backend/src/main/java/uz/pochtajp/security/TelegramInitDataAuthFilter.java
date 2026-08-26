package uz.pochtajp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.pochtajp.common.ApiErrorResponse;
import uz.pochtajp.common.exception.ApiException;
import uz.pochtajp.common.exception.InitDataInvalidException;
import uz.pochtajp.service.UserService;

/**
 * {@code /api/miniapp/**} ostidagi har bir so'rovni {@code initData} bilan tekshiradi (§1.4, §7.1).
 *
 * <p>Header: {@code Authorization: tma <initDataRaw>}.
 * Tekshiruvsiz endpoint bo'lmaydi — istisno faqat {@code /health}, u bu filtrga tushmaydi.
 *
 * <p>Ataylab {@code @Component} emas: Spring Boot {@code Filter} bean'ini butun
 * servlet zanjiriga avtomatik ulab yuboradi va filtr ikki marta ishlaydi.
 * Shuning uchun {@link uz.pochtajp.config.SecurityConfig} uni qo'lda yaratadi.
 */
public class TelegramInitDataAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataAuthFilter.class);

    private static final String PROTECTED_PREFIX = "/api/miniapp/";
    private static final String SCHEME = "tma ";

    private final TelegramInitDataValidator validator;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public TelegramInitDataAuthFilter(TelegramInitDataValidator validator,
                                      UserService userService,
                                      ObjectMapper objectMapper) {
        this.validator = validator;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            if (header == null || !header.startsWith(SCHEME)) {
                throw new InitDataInvalidException("Authorization: tma <initData> headeri yo'q");
            }
            TelegramInitData initData = validator.validate(header.substring(SCHEME.length()).trim());
            UserService.Session session = userService.upsertFromInitData(initData);

            MiniAppPrincipal principal = new MiniAppPrincipal(
                    session.user().getId(), session.user().getTelegramId(), session.user().getRole(),
                    initData.startParam(), session.created());
            SecurityContextHolder.getContext().setAuthentication(new MiniAppAuthentication(principal));
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, ex);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        log.debug("Mini App so'rovi rad etildi: code={}", ex.getCode());
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = ApiErrorResponse.of(ex.getCode(),
                ex.getStatus().value() == 401
                        ? "Sessiya yaroqsiz. Ilovani yopib qaytadan oching."
                        : ex.getMessage());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
