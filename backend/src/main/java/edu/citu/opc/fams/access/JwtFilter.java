package edu.citu.opc.fams.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.citu.opc.fams.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Reads "Authorization: Bearer &lt;jwt&gt;" on every request (SDD 6.1) and loads the user from the
 * database. The request stays anonymous, and SecurityConfig then refuses it, when the token is invalid
 * or expired, the account was deactivated, or the password changed after the token was issued.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;
    private final ObjectMapper objectMapper;

    public JwtFilter(JwtService jwtService, AppUserRepository users, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            var user = jwtService.verify(header.substring(7))
                    .flatMap(v -> users.findById(v.userId())
                            .filter(AppUser::isActive)
                            // JWT times are whole seconds, so compare at second precision
                            .filter(u -> v.issuedAt().getEpochSecond()
                                    >= u.getCredentialsChangedAt().toEpochSecond()));
            if (user.isPresent()) {
                AppUser u = user.get();
                // A user with a temporary password may only read their profile and change the password.
                if (u.isMustChangePassword() && !ALLOWED_WITH_TEMP_PASSWORD.contains(request.getRequestURI())) {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(),
                            ApiError.of(403, "Change your temporary password first."));
                    return;
                }
                var cu = new CurrentUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                        u.getDepartmentId(), u.isMustChangePassword());
                var auth = new UsernamePasswordAuthenticationToken(
                        cu, null, List.of(new SimpleGrantedAuthority(u.getRole().authority())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private static final Set<String> ALLOWED_WITH_TEMP_PASSWORD =
            Set.of("/api/auth/me", "/api/auth/change-password", "/api/health");
}
