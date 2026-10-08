package cl.duoc.rutalimpia.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.exception.ErrorResponse;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void jsonUsaFechasIsoYEnumsComoTexto() {
		String error = jsonMapper.writeValueAsString(
				new ErrorResponse(LocalDateTime.of(2026, 10, 5, 10, 30), 404, "NOT_FOUND", "x", "/api/v1/x"));
		String usuario = jsonMapper.writeValueAsString(
				new UsuarioResponse(3L, "Valentina", "vecino@rutalimpia.cl", Rol.VECINO, true));

		assertThat(error).contains("\"timestamp\":\"2026-10-05T10:30:00\"");
		assertThat(usuario).contains("\"rol\":\"VECINO\"").doesNotContain("password");
	}

}
