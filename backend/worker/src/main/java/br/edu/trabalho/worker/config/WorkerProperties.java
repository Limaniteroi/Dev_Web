package br.edu.trabalho.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações do Worker (prefixo {@code app} no application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record WorkerProperties(Retry retry, Tutor tutor) {

    /**
     * @param maxAttempts total de tentativas de chamada à LLM (inclui a primeira)
     * @param backoffMs   espera fixa entre tentativas, em milissegundos
     */
    public record Retry(int maxAttempts, long backoffMs) {
    }

    /**
     * @param systemPrompt   instrução de sistema enviada em toda chamada
     * @param promptTemplate máscara aplicada à pergunta; {@code {pergunta}} é substituído pelo texto do usuário
     */
    public record Tutor(String systemPrompt, String promptTemplate) {
    }
}
