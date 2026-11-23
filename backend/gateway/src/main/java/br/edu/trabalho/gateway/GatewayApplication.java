package br.edu.trabalho.gateway;

import br.edu.trabalho.common.messaging.RabbitTopologyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Gateway HTTP. Nunca chama a LLM: apenas autentica, publica perguntas no RabbitMQ
 * e repassa as respostas (consumidas de respostas.queue) ao navegador via SSE.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@Import(RabbitTopologyConfig.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
