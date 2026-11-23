package br.edu.trabalho.gateway.question;

import br.edu.trabalho.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela deslizante de 1 minuto por usuário. Como cada pergunta aciona a LLM (custo por token),
 * limitar o volume por usuário autenticado evita abuso da API.
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final int maxPerWindow;

    public RateLimiter(GatewayProperties props) {
        this.maxPerWindow = props.rateLimit().questionsPerMinute();
    }

    /** @return true se a requisição foi aceita (e contabilizada) */
    public boolean tryAcquire(String userId) {
        Deque<Instant> timestamps = requests.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant now = Instant.now();
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(WINDOW))) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxPerWindow) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
