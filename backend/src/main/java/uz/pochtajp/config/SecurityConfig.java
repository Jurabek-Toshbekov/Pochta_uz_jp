package uz.pochtajp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import uz.pochtajp.repository.UserRepository;
import uz.pochtajp.security.AdminJwtAuthFilter;
import uz.pochtajp.security.AdminJwtService;
import uz.pochtajp.security.TelegramInitDataAuthFilter;
import uz.pochtajp.security.TelegramInitDataValidator;
import uz.pochtajp.service.UserService;

/**
 * Xavfsizlik konfiguratsiyasi (§7).
 *
 * <ul>
 *   <li>{@code /health} — yagona ochiq endpoint (§1.4)</li>
 *   <li>{@code /api/miniapp/**} — {@code initData} filtri majburiy</li>
 *   <li>{@code /api/admin/auth/**} — ochiq: bu yerda token hali yo'q (§11.1)</li>
 *   <li>{@code /api/admin/**} — admin JWT filtri majburiy, rol har so'rovda bazadan tekshiriladi</li>
 *   <li>CORS — faqat {@code MINIAPP_URL} va admin domeni (§7.2)</li>
 *   <li>Sessiya yo'q — har bir so'rov o'zini o'zi autentifikatsiya qiladi</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TelegramInitDataAuthFilter initDataAuthFilter;
    private final AdminJwtAuthFilter adminJwtAuthFilter;
    private final BotProperties botProperties;
    private final AppProperties appProperties;

    public SecurityConfig(TelegramInitDataValidator validator,
                          UserService userService,
                          UserRepository userRepository,
                          AdminJwtService adminJwtService,
                          ObjectMapper objectMapper,
                          BotProperties botProperties,
                          AppProperties appProperties) {
        this.initDataAuthFilter = new TelegramInitDataAuthFilter(validator, userService, objectMapper);
        this.adminJwtAuthFilter = new AdminJwtAuthFilter(adminJwtService, userRepository, objectMapper);
        this.botProperties = botProperties;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Sessiya (cookie) ishlatilmaydi — CSRF hujum yuzasi yo'q.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        // Webhook o'z sirini o'zi tekshiradi (§7.2) — 2-bosqichda qo'shiladi.
                        .requestMatchers("/webhook/telegram/**").permitAll()
                        .requestMatchers("/api/miniapp/**").hasRole("MINIAPP_USER")
                        // Kirish oqimi: kod almashtirish va token yangilash (§11.1).
                        // Filtr bu yerga tushmaydi — token hali yo'q.
                        .requestMatchers("/api/admin/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("MODERATOR", "ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(initDataAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminJwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS: faqat ma'lum domenlar. Bo'sh bo'lsa — hech kim (lokal dev'da ham xavfsiz). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = new ArrayList<>();
        addIfPresent(origins, botProperties.miniappUrl());
        addIfPresent(origins, appProperties.adminUrl());

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Session-Id", "X-Platform"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void addIfPresent(List<String> origins, String url) {
        if (url != null && !url.isBlank()) {
            origins.add(url.strip());
        }
    }
}
