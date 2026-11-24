package br.edu.trabalho.gateway.question;

import br.edu.trabalho.gateway.sse.SseHub;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * O userId vem sempre do JWT validado ({@code sub}), nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/api/perguntas")
public class QuestionController {

    static final String HISTORY_CLEARED_EVENT = "historico-limpo";

    private final QuestionService service;
    private final QuestionStore store;
    private final SseHub hub;

    public QuestionController(QuestionService service, QuestionStore store, SseHub hub) {
        this.service = service;
        this.store = store;
        this.hub = hub;
    }

    /** 202 Accepted: a pergunta entrou na fila; a resposta virá pelo SSE. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QuestionView ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return service.submit(jwt.getSubject(), request.question());
    }

    @GetMapping
    public List<QuestionView> history(@AuthenticationPrincipal Jwt jwt) {
        return store.history(jwt.getSubject());
    }

    /**
     * "Limpar conversa": apaga todo o histórico do usuário (inclusive perguntas em andamento,
     * cujas respostas serão descartadas) e avisa as outras abas dele pelo SSE.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        int removed = store.clear(userId);
        hub.send(userId, HISTORY_CLEARED_EVENT, Map.of("removidas", removed));
    }

    @GetMapping("/{correlationId}")
    public QuestionView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String correlationId) {
        return store.find(correlationId, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pergunta não encontrada"));
    }

    public record AskRequest(@NotBlank(message = "A pergunta não pode estar vazia") String question) {
    }
}
