package br.edu.trabalho.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

/**
 * Fábrica da política de resiliência da chamada à LLM.
 * <p>
 * Intencionalmente NÃO é um @Bean do tipo RetryTemplate: o Spring AI usa qualquer bean
 * RetryTemplate do contexto como retry interno, o que aninharia as tentativas (3 x 3 = 9).
 */
public final class WorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkerConfig.class);

    private WorkerConfig() {
    }

    /**
     * N tentativas com espera fixa entre elas (ex.: 3 tentativas, 2 s).
     * Cobre falhas de rede, 5xx e rate limit (429) do provedor.
     */
    public static RetryTemplate llmRetryTemplate(WorkerProperties props) {
        return RetryTemplate.builder()
                .maxAttempts(props.retry().maxAttempts())
                .fixedBackoff(props.retry().backoffMs())
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
                                                                 Throwable throwable) {
                        log.warn("Falha na chamada à LLM (tentativa {}/{}): {}",
                                context.getRetryCount(), props.retry().maxAttempts(), throwable.toString());
                    }
                })
                .build();
    }
}
