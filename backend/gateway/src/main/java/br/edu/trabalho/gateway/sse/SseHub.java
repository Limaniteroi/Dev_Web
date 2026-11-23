package br.edu.trabalho.gateway.sse;

import br.edu.trabalho.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mapa em memória userId -> conexões SSE abertas.
 * <p>
 * Cada usuário pode ter mais de uma aba aberta, por isso a lista. Uma resposta só é enviada
 * para os emitters do dono da pergunta (userId extraído do JWT), nunca para outros usuários.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final GatewayProperties props;

    public SseHub(GatewayProperties props) {
        this.props = props;
    }

    public SseEmitter connect(String userId) {
        SseEmitter emitter = new SseEmitter(props.sse().timeout().toMillis());
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete(); // o cliente (fetch-event-source) reconecta sozinho
        });
        emitter.onError(e -> cleanup.run());

        // Primeiro evento: confirma a conexão e já envia os headers ao navegador.
        sendTo(userId, emitter, SseEmitter.event().name("conectado").data(Map.of("userId", userId)));
        log.info("SSE aberto para {} ({} conexão(ões))", userId, count(userId));
        return emitter;
    }

    /** @return quantas conexões do usuário receberam o evento */
    public int send(String userId, String eventName, Object payload) {
        List<SseEmitter> userEmitters = emitters.getOrDefault(userId, List.of());
        int delivered = 0;
        for (SseEmitter emitter : userEmitters) {
            if (sendTo(userId, emitter, SseEmitter.event().name(eventName).data(payload))) {
                delivered++;
            }
        }
        return delivered;
    }

    public int count(String userId) {
        return emitters.getOrDefault(userId, List.of()).size();
    }

    /** Comentário periódico: impede que proxies derrubem a conexão ociosa e detecta clientes mortos. */
    @Scheduled(fixedDelayString = "${app.sse.heartbeat}")
    void heartbeat() {
        emitters.forEach((userId, list) -> list.forEach(emitter ->
                sendTo(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    private boolean sendTo(String userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            // Cliente fechou a aba / conexão caiu
            remove(userId, emitter);
            emitter.completeWithError(e);
            return false;
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
