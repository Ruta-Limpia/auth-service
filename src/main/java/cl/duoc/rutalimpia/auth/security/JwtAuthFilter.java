package cl.duoc.rutalimpia.auth.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// si el token no es valido no se corta la peticion: queda sin autenticar y Spring responde 401
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    // un token con otro rol (por ejemplo INTERNAL) no se acepta aunque la firma sea correcta
    private static final Set<String> ROLES_VALIDOS = Set.of("VECINO", "CONDUCTOR", "ADMIN");

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null
                && header.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIJO.length()).trim();
            if (jwtUtil.esValido(token)) {
                String rol = jwtUtil.obtenerRol(token);
                if (ROLES_VALIDOS.contains(rol)) {
                    autenticar(request, jwtUtil.obtenerUsuarioId(token), rol);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void autenticar(HttpServletRequest request, Long usuarioId, String rol) {
        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                usuarioId, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }
}
