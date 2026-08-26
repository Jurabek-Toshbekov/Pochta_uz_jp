package uz.pochtajp.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * {@code initData} tekshiruvidan o'tgan sessiya.
 * Boshqa yo'l bilan yaratilmaydi — ya'ni bu token borligi imzo tekshirilganini bildiradi.
 */
public class MiniAppAuthentication extends AbstractAuthenticationToken {

    private final MiniAppPrincipal principal;

    public MiniAppAuthentication(MiniAppPrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> authorities(MiniAppPrincipal principal) {
        return List.of(
                new SimpleGrantedAuthority("ROLE_MINIAPP_USER"),
                new SimpleGrantedAuthority("ROLE_" + principal.role().name())
        );
    }

    @Override
    public MiniAppPrincipal getPrincipal() {
        return principal;
    }

    /** Credential (initData) saqlanmaydi — imzo allaqachon tekshirilgan. */
    @Override
    public Object getCredentials() {
        return null;
    }
}
