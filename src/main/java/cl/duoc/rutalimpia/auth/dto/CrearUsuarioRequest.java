package cl.duoc.rutalimpia.auth.dto;

import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.validation.PasswordValida;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @PasswordValida
        String password,

        @NotNull(message = "El rol es obligatorio")
        Rol rol) {

    @Override
    public String toString() {
        return "CrearUsuarioRequest[nombre=" + nombre + ", email=" + email + ", password=****, rol=" + rol + "]";
    }
}
