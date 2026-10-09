package cl.duoc.rutalimpia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtUtilTest {

    private static final String SECRETO = "rutalimpia-secreto-super-largo-de-32-chars-minimo";

    private final JwtUtil jwtUtil = new JwtUtil(SECRETO, 60);

    @Test
    void generaTokenConLosClaimsDelContrato() {
        String token = jwtUtil.generarToken(3L, "vecino@rutalimpia.cl", "VECINO");

        SecretKey key = Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        Claims claims = jws.getPayload();

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getSubject()).isEqualTo("3");
        assertThat(claims.get("email", String.class)).isEqualTo("vecino@rutalimpia.cl");
        assertThat(claims.get("rol", String.class)).isEqualTo("VECINO");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void leeLosDatosDeUnTokenValido() {
        String token = jwtUtil.generarToken(2L, "conductor@rutalimpia.cl", "CONDUCTOR");

        assertThat(jwtUtil.esValido(token)).isTrue();
        assertThat(jwtUtil.obtenerUsuarioId(token)).isEqualTo(2L);
        assertThat(jwtUtil.obtenerRol(token)).isEqualTo("CONDUCTOR");
        assertThat(jwtUtil.obtenerEmail(token)).isEqualTo("conductor@rutalimpia.cl");
    }

    @Test
    void rechazaTokenFirmadoConOtroSecreto() {
        JwtUtil otro = new JwtUtil("otro-secreto-distinto-de-al-menos-32-caracteres", 60);
        String token = otro.generarToken(1L, "admin@rutalimpia.cl", "ADMIN");

        assertThat(jwtUtil.esValido(token)).isFalse();
    }

    @Test
    void rechazaTokenVencido() {
        Instant hace2Horas = Instant.now().minus(Duration.ofHours(2));
        String token = Jwts.builder()
                .subject("3")
                .claim("rol", "VECINO")
                .issuedAt(Date.from(hace2Horas))
                .expiration(Date.from(hace2Horas.plus(Duration.ofMinutes(60))))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtUtil.esValido(token)).isFalse();
    }

    @Test
    void rechazaTokenConPayloadModificado() {
        String token = jwtUtil.generarToken(3L, "vecino@rutalimpia.cl", "VECINO");
        String[] partes = token.split("\\.");
        String payloadFalso = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"3\",\"email\":\"vecino@rutalimpia.cl\",\"rol\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwtUtil.esValido(partes[0] + "." + payloadFalso + "." + partes[2])).isFalse();
    }

    @Test
    void rechazaTokenSinFirma() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"rol\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwtUtil.esValido(header + "." + payload + ".")).isFalse();
    }

    @Test
    void rechazaTokenSinRolOConSubNoNumerico() {
        SecretKey key = Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));
        String sinRol = Jwts.builder().subject("3").signWith(key, Jwts.SIG.HS256).compact();
        String subTexto = Jwts.builder().subject("admin").claim("rol", "ADMIN").signWith(key, Jwts.SIG.HS256).compact();

        assertThat(jwtUtil.esValido(sinRol)).isFalse();
        assertThat(jwtUtil.esValido(subTexto)).isFalse();
    }

    @Test
    void rechazaValoresVaciosOBasura() {
        assertThat(jwtUtil.esValido(null)).isFalse();
        assertThat(jwtUtil.esValido("")).isFalse();
        assertThat(jwtUtil.esValido("   ")).isFalse();
        assertThat(jwtUtil.esValido("esto.no.es-un-jwt")).isFalse();
    }

    @Test
    void noArrancaConSecretoCorto() {
        assertThatThrownBy(() -> new JwtUtil("secreto-corto", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    @Test
    void noArrancaConExpiracionInvalida() {
        assertThatThrownBy(() -> new JwtUtil(SECRETO, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
