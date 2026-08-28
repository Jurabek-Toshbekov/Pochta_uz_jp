package uz.pochtajp.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Tekshirilgan admin JWT'dan yasalgan sessiya (§11.1).
 *
 * <p>Bu token borligi imzo tekshirilganini va foydalanuvchi rolining
 * hali ham kuchda ekanini bildiradi — filtr har so'rovda bazadan
 * tekshiradi, chunki rol bekor qilinishi mumkin.
 */
public class AdminAuthentication extends AbstractAuthenticationToken {

    private final AdminPrincipal principal;

    public AdminAuthentication(AdminPrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> authorities(AdminPrincipal principal) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
    }

    @Override
    public AdminPrincipal getPrincipal() {
        return principal;
    }

    /** JWT saqlanmaydi — imzo allaqachon tekshirilgan. */
    @Override
    public Object getCredentials() {
        return null;
    }
}
