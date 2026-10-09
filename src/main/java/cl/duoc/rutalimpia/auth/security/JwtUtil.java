package cl.duoc.rutalimpia.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final int LARGO_MINIMO_SECRETO = 32;

    private static final long DESFASE_RELOJ_SEGUNDOS = 30;

    private final SecretKey key;
    private final JwtParser parser;
    private final long expiracionMinutos;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:60}") long expiracionMinutos) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < LARGO_MINIMO_SECRETO) {
            throw new IllegalStateException("app.jwt.secret debe tener al menos 32 caracteres");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalStateException("app.jwt.expiration-minutes debe ser mayor a 0");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(DESFASE_RELOJ_SEGUNDOS)
                .build();
        this.expiracionMinutos = expiracionMinutos;
    }

    // solo se usa en auth-service
    public String generarToken(Long id, String email, String rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracionMinutos, ChronoUnit.MINUTES)))
                // HS256 fijo, con secretos largos jjwt usa HS384
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public boolean esValido(String token) {
        try {
            Claims claims = leerClaims(token);
            Long.parseLong(claims.getSubject());
            return claims.get("rol", String.class) != null;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long obtenerUsuarioId(String token) {
        return Long.parseLong(leerClaims(token).getSubject());
    }

    public String obtenerRol(String token) {
        return leerClaims(token).get("rol", String.class);
    }

    public String obtenerEmail(String token) {
        return leerClaims(token).get("email", String.class);
    }

    public long getExpiracionMinutos() {
        return expiracionMinutos;
    }

    private Claims leerClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}
