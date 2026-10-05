package cl.duoc.rutalimpia.auth.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidaValidator implements ConstraintValidator<PasswordValida, String> {

    static final int MIN_CARACTERES = 8;

    // BCrypt no acepta mas de 72 bytes; una clave con muchas tildes o ñ los supera
    // antes de llegar a 72 caracteres y Spring Security lanza excepcion al hashearla
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // eso lo valida @NotBlank
        }
        return password.codePointCount(0, password.length()) >= MIN_CARACTERES
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
