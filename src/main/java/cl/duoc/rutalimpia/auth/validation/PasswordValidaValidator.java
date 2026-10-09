package cl.duoc.rutalimpia.auth.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidaValidator implements ConstraintValidator<PasswordValida, String> {

    static final int MIN_CARACTERES = 8;

    // bcrypt no acepta mas de 72 bytes (ojo con tildes y ñ)
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        return password.codePointCount(0, password.length()) >= MIN_CARACTERES
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
