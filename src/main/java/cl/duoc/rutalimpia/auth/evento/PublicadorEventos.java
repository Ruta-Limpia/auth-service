package cl.duoc.rutalimpia.auth.evento;

// si la cola no responde, el evento se pierde y queda en el log,
// pero el login y el registro siguen funcionando
public interface PublicadorEventos {

    void publicar(EventoAuth evento);
}
