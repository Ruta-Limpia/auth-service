package cl.duoc.rutalimpia.auth.config;

import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cl.duoc.rutalimpia.auth.evento.PublicadorEventos;
import cl.duoc.rutalimpia.auth.evento.PublicadorEventosNulo;
import cl.duoc.rutalimpia.auth.evento.SqsPublicadorEventos;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class EventosConfig {

    /*
     * Con app.eventos.cola-url vacia (local, tests, Docker Compose) los eventos no salen.
     * En AWS las credenciales las entrega el rol de la EC2 (LabInstanceProfile).
     */
    @Bean
    public PublicadorEventos publicadorEventos(
            @Value("${app.eventos.cola-url:}") String colaUrl,
            @Value("${app.eventos.region:us-east-1}") String region,
            JsonMapper jsonMapper) {
        if (colaUrl == null || colaUrl.isBlank()) {
            log.info("Sin cola SQS configurada: los eventos no se envian");
            return new PublicadorEventosNulo();
        }
        log.info("Eventos de login y registro hacia SQS: {}", colaUrl);
        SqsClient sqs = SqsClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        return new SqsPublicadorEventos(sqs, colaUrl, jsonMapper, Executors.newVirtualThreadPerTaskExecutor());
    }
}
