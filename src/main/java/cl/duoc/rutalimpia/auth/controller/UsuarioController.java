package cl.duoc.rutalimpia.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Perfil y gestión de usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/api/v1/usuarios/me")
    @Operation(summary = "Perfil del usuario dueño del token", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Perfil del usuario")
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido")
    public ResponseEntity<UsuarioResponse> obtenerPerfil(Authentication authentication) {
        // el id siempre sale del token, nunca del body ni de la URL
        Long usuarioId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(usuarioService.obtenerPorId(usuarioId));
    }

    @PostMapping("/api/v1/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea un usuario con cualquier rol (conductores)", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "403", description = "Solo ADMIN")
    @ApiResponse(responseCode = "409", description = "El email ya está registrado")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @GetMapping("/api/v1/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista usuarios, opcionalmente filtrados por rol", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Listado de usuarios")
    @ApiResponse(responseCode = "403", description = "Solo ADMIN")
    public ResponseEntity<List<UsuarioResponse>> listar(@RequestParam(required = false) Rol rol) {
        return ResponseEntity.ok(usuarioService.listar(rol));
    }

    @GetMapping("/api/v1/internal/usuarios/{id}")
    @Operation(summary = "Consulta interna entre servicios", description = "No se publica en API Gateway",
            security = @SecurityRequirement(name = "internalKey"))
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "403", description = "Clave interna ausente o inválida")
    @ApiResponse(responseCode = "404", description = "El usuario no existe")
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }
}
