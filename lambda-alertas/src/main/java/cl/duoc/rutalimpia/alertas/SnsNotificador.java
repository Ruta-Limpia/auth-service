package cl.duoc.rutalimpia.alertas;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

// publica en el tema SNS; los suscritos (el correo del admin) reciben el aviso
public class SnsNotificador implements Notificador {

    private final SnsClient sns;
    private final String temaArn;

    public SnsNotificador(String temaArn) {
        if (temaArn == null || temaArn.isBlank()) {
            throw new IllegalStateException("Falta la variable ALERTAS_TOPIC_ARN");
        }
        // region y credenciales las pone Lambda en las variables de entorno
        this.sns = SnsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.temaArn = temaArn;
    }

    @Override
    public void enviar(String asunto, String mensaje) {
        sns.publish(PublishRequest.builder()
                .topicArn(temaArn)
                .subject(asunto)
                .message(mensaje)
                .build());
    }
}
