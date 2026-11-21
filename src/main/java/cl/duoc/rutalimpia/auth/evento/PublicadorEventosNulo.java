package cl.duoc.rutalimpia.auth.evento;

import lombok.extern.slf4j.Slf4j;

// se usa cuando no hay cola configurada (en local y en los tests)
@Slf4j
public class PublicadorEventosNulo implements PublicadorEventos {

    @Override
    public void publicar(EventoAuth evento) {
        log.debug("Evento {} no enviado: no hay cola configurada", evento.tipo());
    }
}
