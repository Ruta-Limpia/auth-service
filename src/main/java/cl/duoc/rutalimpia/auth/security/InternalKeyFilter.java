package cl.duoc.rutalimpia.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// protege /api/v1/internal/**, que solo usan los otros servicios y la Lambda
@Slf4j
public class InternalKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    private static final String RUTA_INTERNA = "/api/v1/internal/";

    private final byte[] claveInterna;
    private final SecurityErrorHandler errores;

    public InternalKeyFilter(String claveInterna, SecurityErrorHandler errores) {
        if (claveInterna == null || claveInterna.isBlank()) {
            throw new IllegalStateException("app.internal.key no puede estar vacia");
        }
        this.claveInterna = claveInterna.getBytes(StandardCharsets.UTF_8);
        this.errores = errores;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return !ruta.startsWith(RUTA_INTERNA);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(HEADER);

        // MessageDigest.isEqual compara en tiempo constante, no se puede adivinar la clave por tiempos
        if (recibida == null || !MessageDigest.isEqual(recibida.getBytes(StandardCharsets.UTF_8), claveInterna)) {
            log.warn("Llamada interna rechazada: {} {}", request.getMethod(), request.getRequestURI());
            errores.escribir(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Clave interna ausente o inválida");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "servicio-interno", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
