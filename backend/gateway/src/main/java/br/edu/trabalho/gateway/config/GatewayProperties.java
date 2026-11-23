package br.edu.trabalho.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configurações do Gateway (prefixo {@code app} no application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record GatewayProperties(Security security, Cors cors, RateLimit rateLimit, Sse sse, History history) {

    /**
     * @param jwtSecret segredo HMAC-SHA256 (mínimo 32 bytes)
     * @param jwtTtl    validade do token
     * @param users     usuários pré-cadastrados (além dos criados via /api/auth/register)
     */
    public record Security(String jwtSecret, Duration jwtTtl, List<User> users) {
    }

    public record User(String username, String password) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Controle de uso da LLM por usuário (cada pergunta custa tokens).
     *
     * @param questionsPerMinute máximo de perguntas aceitas por minuto
     * @param maxPending         máximo de perguntas aguardando resposta ao mesmo tempo
     * @param maxQuestionLength  tamanho máximo do texto da pergunta
     */
    public record RateLimit(int questionsPerMinute, int maxPending, int maxQuestionLength) {
    }

    /**
     * @param timeout   tempo máximo de uma conexão SSE (o cliente reconecta sozinho depois)
     * @param heartbeat intervalo dos comentários "ping" que mantêm a conexão viva
     */
    public record Sse(Duration timeout, Duration heartbeat) {
    }

    /**
     * @param retention por quanto tempo o histórico em memória é mantido
     */
    public record History(Duration retention) {
    }
}
