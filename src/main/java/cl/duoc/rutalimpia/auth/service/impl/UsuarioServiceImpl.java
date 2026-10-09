package cl.duoc.rutalimpia.auth.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.rutalimpia.auth.dto.ActualizarUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.exception.RecursoNoEncontradoException;
import cl.duoc.rutalimpia.auth.exception.ReglaNegocioException;
import cl.duoc.rutalimpia.auth.mapper.UsuarioMapper;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private static final String EMAIL_DUPLICADO = "El email ya está registrado";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    @Override
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ReglaNegocioException(EMAIL_DUPLICADO);
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.nombre().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .build();

        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // por si llegan dos registros con el mismo email al mismo tiempo
            throw new ReglaNegocioException(EMAIL_DUPLICADO);
        }

        log.info("Usuario {} creado con rol {}", usuario.getId(), usuario.getRol());
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long id) {
        return usuarioMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(Rol rol) {
        List<Usuario> usuarios = rol == null
                ? usuarioRepository.findAll(Sort.by("id"))
                : usuarioRepository.findByRol(rol);
        return usuarios.stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request, Long adminId) {
        Usuario usuario = buscar(id);
        String email = normalizarEmail(request.email());

        if (id.equals(adminId) && (request.rol() != Rol.ADMIN || !request.activo())) {
            throw new ReglaNegocioException("No puede quitarse el rol ADMIN ni desactivar su propia cuenta");
        }
        if (usuarioRepository.existsByEmailAndIdNot(email, id)) {
            throw new ReglaNegocioException(EMAIL_DUPLICADO);
        }

        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());

        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException(EMAIL_DUPLICADO);
        }

        log.info("Usuario {} actualizado por el admin {}", id, adminId);
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Long adminId) {
        if (id.equals(adminId)) {
            throw new ReglaNegocioException("No puede eliminar su propia cuenta");
        }
        usuarioRepository.delete(buscar(id));
        log.info("Usuario {} eliminado por el admin {}", id, adminId);
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no existe"));
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
