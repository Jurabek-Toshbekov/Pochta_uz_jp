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
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.common.exception.UnauthorizedException;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.UserRole;
import uz.pochtajp.domain.enums.UserStatus;
import uz.pochtajp.repository.UserRepository;

/**
 * {@code /api/admin/**} ostidagi so'rovlarni JWT bilan tekshiradi (§11.1).
 *
 * <p>Ikki bosqichli tekshiruv:
 * <ol>
 *   <li>imzo va muddat — {@link AdminJwtService}</li>
 *   <li>rol hali ham kuchdami — har so'rovda bazadan. Token 2 soat yashaydi,
 *       shuncha vaqt ichida odamning huquqi olib qo'yilishi mumkin.</li>
 * </ol>
 *
 * <p>{@code /api/admin/auth/**} bu filtrga tushmaydi — u yerda token hali yo'q.
 *
 * <p>{@code @Component} emas: Spring Boot {@code Filter} bean'ini butun servlet
 * zanjiriga ulab yuboradi va filtr ikki marta ishlaydi.
 */
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtAuthFilter.class);

    private static final String PROTECTED_PREFIX = "/api/admin/";
    private static final String PUBLIC_PREFIX = "/api/admin/auth/";
    private static final String SCHEME = "Bearer ";

    private final AdminJwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminJwtAuthFilter(AdminJwtService jwtService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(PROTECTED_PREFIX) || uri.startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            if (header == null || !header.startsWith(SCHEME)) {
                throw new UnauthorizedException("Kirish tokeni yo'q. Qaytadan kiring.");
            }
            AdminPrincipal fromToken = jwtService.parse(
                    header.substring(SCHEME.length()).trim(), uz.pochtajp.domain.enums.AdminTokenType.ACCESS);

            AdminPrincipal principal = verifyStillAllowed(fromToken);
            SecurityContextHolder.getContext().setAuthentication(new AdminAuthentication(principal));
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

    /** Rol tokendan emas, bazadan olinadi — token eskirgan bo'lishi mumkin. */
    private AdminPrincipal verifyStillAllowed(AdminPrincipal fromToken) {
        User user = userRepository.findById(fromToken.userId())
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ForbiddenException("Huquq bekor qilingan."));

        UserRole role = user.getRole();
        if (role != UserRole.ADMIN && role != UserRole.MODERATOR) {
            throw new ForbiddenException("Huquq bekor qilingan.");
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ForbiddenException("Hisob bloklangan.");
        }
        return new AdminPrincipal(user.getId(), user.getTelegramId(), role);
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        // PII yozilmaydi — faqat kod (§1.7).
        log.debug("Admin so'rovi rad etildi: code={}", ex.getCode());
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(ex.getCode(), ex.getMessage()));
    }
}
