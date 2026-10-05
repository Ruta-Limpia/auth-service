package cl.duoc.rutalimpia.auth.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidaValidator.class)
public @interface PasswordValida {

    String message() default "La contraseña debe tener entre 8 y 72 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
