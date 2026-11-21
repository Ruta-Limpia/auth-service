package cl.duoc.rutalimpia.auth.evento;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

// los eventos salen recien cuando la transaccion se confirmo, asi no se avisa
// de un registro que despues se deshizo. Si no hay transaccion se envian igual
@Component
@RequiredArgsConstructor
public class EventosListener {

    private final PublicadorEventos publicadorEventos;

    @TransactionalEventListener(fallbackExecution = true)
    public void alConfirmar(EventoAuth evento) {
        publicadorEventos.publicar(evento);
    }
}
