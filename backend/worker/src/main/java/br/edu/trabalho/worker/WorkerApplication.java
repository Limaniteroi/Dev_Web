package br.edu.trabalho.worker;

import br.edu.trabalho.common.messaging.RabbitTopologyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * Worker assíncrono. Roda sem servidor web ({@code spring.main.web-application-type: none}):
 * o processo fica vivo apenas por causa dos consumidores do RabbitMQ.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(RabbitTopologyConfig.class)
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
