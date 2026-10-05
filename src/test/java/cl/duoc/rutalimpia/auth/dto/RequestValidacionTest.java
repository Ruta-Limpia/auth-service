package cl.duoc.rutalimpia.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cl.duoc.rutalimpia.auth.model.enums.Rol;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RequestValidacionTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    @Test
    void registroValidoNoTieneErrores() {
        assertThat(validator.validate(new RegistroRequest("Valentina", "vecino@test.cl", "clave1234"))).isEmpty();
    }

    @Test
    void registroVacioReportaCadaCampo() {
        Set<String> mensajes = mensajes(validator.validate(new RegistroRequest(" ", null, null)));

        assertThat(mensajes).containsExactlyInAnyOrder(
                "El nombre es obligatorio",
                "El email es obligatorio",
                "La contraseña es obligatoria");
    }

    @Test
    void registroRechazaEmailMalFormado() {
        Set<String> mensajes = mensajes(validator.validate(new RegistroRequest("Ana", "no-es-email", "clave1234")));

        assertThat(mensajes).containsExactly("El email no tiene un formato válido");
    }

    @Test
    void registroRechazaNombreYEmailMasLargosQueLaColumna() {
        // 155 caracteres, con partes de largo valido para que solo falle el @Size
        String email = "a".repeat(60) + "@" + "b".repeat(60) + "." + "c".repeat(30) + ".cl";
        Set<String> mensajes = mensajes(validator.validate(new RegistroRequest("n".repeat(121), email, "clave1234")));

        assertThat(mensajes).containsExactlyInAnyOrder(
                "El nombre no puede superar los 120 caracteres",
                "El email no puede superar los 150 caracteres");
    }

    @Test
    void passwordDebeTenerAlMenosOchoCaracteres() {
        assertThat(validator.validate(new RegistroRequest("Ana", "ana@test.cl", "1234567"))).hasSize(1);
        assertThat(validator.validate(new RegistroRequest("Ana", "ana@test.cl", "12345678"))).isEmpty();
    }

    @Test
    void passwordNoPuedeSuperar72Bytes() {
        assertThat(validator.validate(new RegistroRequest("Ana", "ana@test.cl", "a".repeat(72)))).isEmpty();
        assertThat(validator.validate(new RegistroRequest("Ana", "ana@test.cl", "a".repeat(73)))).hasSize(1);
        // 40 letras ñ son 80 bytes en UTF-8
        assertThat(validator.validate(new RegistroRequest("Ana", "ana@test.cl", "ñ".repeat(40)))).hasSize(1);
    }

    @Test
    void crearUsuarioExigeRol() {
        Set<String> mensajes = mensajes(validator.validate(
                new CrearUsuarioRequest("Carlos", "conductor@test.cl", "clave1234", null)));

        assertThat(mensajes).containsExactly("El rol es obligatorio");
    }

    @Test
    void crearUsuarioValido() {
        assertThat(validator.validate(
                new CrearUsuarioRequest("Carlos", "conductor@test.cl", "clave1234", Rol.CONDUCTOR))).isEmpty();
    }

    @Test
    void loginSoloExigeCamposPresentes() {
        assertThat(validator.validate(new LoginRequest("vecino@test.cl", "123456"))).isEmpty();
        assertThat(validator.validate(new LoginRequest("", ""))).hasSize(2);
    }

    @Test
    void elToStringNoMuestraLaPassword() {
        assertThat(new RegistroRequest("Ana", "ana@test.cl", "secreta123").toString()).doesNotContain("secreta123");
        assertThat(new CrearUsuarioRequest("Ana", "ana@test.cl", "secreta123", Rol.ADMIN).toString())
                .doesNotContain("secreta123");
        assertThat(new LoginRequest("ana@test.cl", "secreta123").toString()).doesNotContain("secreta123");
        assertThat(LoginResponse.bearer("eyJ.token.firmado", 60, null).toString()).doesNotContain("eyJ.token.firmado");
    }

    private Set<String> mensajes(Set<? extends ConstraintViolation<?>> violaciones) {
        return violaciones.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }
}
