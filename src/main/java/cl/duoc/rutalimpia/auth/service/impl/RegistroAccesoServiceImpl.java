package cl.duoc.rutalimpia.auth.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.rutalimpia.auth.dto.RegistroAccesoResponse;
import cl.duoc.rutalimpia.auth.exception.RecursoNoEncontradoException;
import cl.duoc.rutalimpia.auth.mapper.RegistroAccesoMapper;
import cl.duoc.rutalimpia.auth.model.RegistroAcceso;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;
import cl.duoc.rutalimpia.auth.repository.RegistroAccesoRepository;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.service.RegistroAccesoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistroAccesoServiceImpl implements RegistroAccesoService {

    private static final int LARGO_MAXIMO_IP = 45;

    private final RegistroAccesoRepository registroAccesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegistroAccesoMapper registroAccesoMapper;

    @Override
    @Transactional
    public void registrar(Usuario usuario, ResultadoAcceso resultado, String ip) {
        registroAccesoRepository.save(RegistroAcceso.builder()
                .usuario(usuario)
                .resultado(resultado)
                .ip(recortar(ip))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroAccesoResponse> listarPorUsuario(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new RecursoNoEncontradoException("Usuario " + usuarioId + " no existe");
        }
        return registroAccesoRepository.findTop20ByUsuarioIdOrderByFechaDescIdDesc(usuarioId).stream()
                .map(registroAccesoMapper::toResponse)
                .toList();
    }

    private static String recortar(String ip) {
        if (ip == null || ip.length() <= LARGO_MAXIMO_IP) {
            return ip;
        }
        return ip.substring(0, LARGO_MAXIMO_IP);
    }
}
