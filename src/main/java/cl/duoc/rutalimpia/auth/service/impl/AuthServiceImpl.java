package cl.duoc.rutalimpia.auth.service.impl;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.LoginResponse;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.mapper.UsuarioMapper;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.security.JwtUtil;
import cl.duoc.rutalimpia.auth.service.AuthService;
import cl.duoc.rutalimpia.auth.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UsuarioMapper usuarioMapper;

    // se compara contra este hash cuando el email no existe, asi la respuesta tarda
    // lo mismo que con una clave incorrecta y no se puede adivinar que emails estan registrados
    private final String hashReferencia;

    public AuthServiceImpl(UsuarioRepository usuarioRepository, UsuarioService usuarioService,
            PasswordEncoder passwordEncoder, JwtUtil jwtUtil, UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.usuarioMapper = usuarioMapper;
        this.hashReferencia = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public UsuarioResponse registrar(RegistroRequest request) {
        // el registro publico siempre crea vecinos; el rol nunca se toma del cliente
        return usuarioService.crear(new CrearUsuarioRequest(
                request.nombre(), request.email(), request.password(), Rol.VECINO));
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(email);

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(request.password(), hashReferencia);
            log.warn("Login rechazado: email no registrado");
            throw credencialesInvalidas();
        }

        Usuario usuario = encontrado.get();
        boolean passwordCorrecta = passwordEncoder.matches(request.password(), usuario.getPasswordHash());
        if (!passwordCorrecta || !usuario.isActivo()) {
            log.warn("Login rechazado para usuario {}", usuario.getId());
            throw credencialesInvalidas();
        }

        String token = jwtUtil.generarToken(usuario.getId(), usuario.getEmail(), usuario.getRol().name());
        return LoginResponse.bearer(token, jwtUtil.getExpiracionMinutos(), usuarioMapper.toResponse(usuario));
    }

    // mismo mensaje para email inexistente, clave mala o cuenta desactivada
    private BadCredentialsException credencialesInvalidas() {
        return new BadCredentialsException("Email o contraseña incorrectos");
    }
}
