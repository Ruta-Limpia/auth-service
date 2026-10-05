package cl.duoc.rutalimpia.auth.dto;

import cl.duoc.rutalimpia.auth.validation.PasswordValida;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @PasswordValida
        String password) {

    // el toString del record imprimiria la clave si alguien loguea el request
    @Override
    public String toString() {
        return "RegistroRequest[nombre=" + nombre + ", email=" + email + ", password=****]";
    }
}
