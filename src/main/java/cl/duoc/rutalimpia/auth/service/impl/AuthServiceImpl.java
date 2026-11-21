package cl.duoc.rutalimpia.auth.service.impl;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.LoginResponse;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.evento.EventoAuth;
import cl.duoc.rutalimpia.auth.mapper.UsuarioMapper;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.security.JwtUtil;
import cl.duoc.rutalimpia.auth.service.AuthService;
import cl.duoc.rutalimpia.auth.service.RegistroAccesoService;
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
    private final RegistroAccesoService registroAccesoService;
    private final ApplicationEventPublisher eventos;

    // para que un email que no existe demore lo mismo que una clave mala
    private final String hashReferencia;

    public AuthServiceImpl(UsuarioRepository usuarioRepository, UsuarioService usuarioService,
            PasswordEncoder passwordEncoder, JwtUtil jwtUtil, UsuarioMapper usuarioMapper,
            RegistroAccesoService registroAccesoService, ApplicationEventPublisher eventos) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.usuarioMapper = usuarioMapper;
        this.registroAccesoService = registroAccesoService;
        this.eventos = eventos;
        this.hashReferencia = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public UsuarioResponse registrar(RegistroRequest request) {
        UsuarioResponse creado = usuarioService.crear(new CrearUsuarioRequest(
                request.nombre(), request.email(), request.password(), Rol.VECINO));
        eventos.publishEvent(EventoAuth.usuarioRegistrado(creado.id(), creado.email()));
        return creado;
    }

    // sin rollback en el 401, si no el intento fallido no quedaria guardado
    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(LoginRequest request, String ip) {
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
            ResultadoAcceso motivo = passwordCorrecta ? ResultadoAcceso.CUENTA_INACTIVA : ResultadoAcceso.CLAVE_INCORRECTA;
            registroAccesoService.registrar(usuario, motivo, ip);
            // la Lambda de alertas decide si avisa segun cuantos fallidos seguidos lleva
            eventos.publishEvent(EventoAuth.loginFallido(usuario.getId(), usuario.getEmail(), ip, motivo,
                    registroAccesoService.contarFallidosSeguidos(usuario.getId())));
            log.warn("Login rechazado para usuario {}", usuario.getId());
            throw credencialesInvalidas();
        }

        registroAccesoService.registrar(usuario, ResultadoAcceso.EXITOSO, ip);

        String token = jwtUtil.generarToken(usuario.getId(), usuario.getEmail(), usuario.getRol().name());
        return LoginResponse.bearer(token, jwtUtil.getExpiracionMinutos(), usuarioMapper.toResponse(usuario));
    }

    private BadCredentialsException credencialesInvalidas() {
        return new BadCredentialsException("Email o contraseña incorrectos");
    }
}
