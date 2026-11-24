package br.edu.trabalho.gateway.question;

import br.edu.trabalho.common.messaging.AnswerMessage;
import br.edu.trabalho.common.messaging.AnswerStatus;
import br.edu.trabalho.gateway.config.GatewayProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Histórico em memória das perguntas por correlationId.
 * <p>
 * Serve para o front-end recuperar respostas que chegaram enquanto o stream SSE estava
 * desconectado (troca de aba, queda de rede, reload) — o SSE entrega em tempo real, e este
 * histórico garante que nada se perde do ponto de vista do usuário.
 */
@Component
public class QuestionStore {

    private final Map<String, QuestionView> byCorrelationId = new ConcurrentHashMap<>();
    /** Perguntas apagadas por "limpar conversa" antes de serem respondidas (id -> quando). */
    private final Map<String, Instant> discarded = new ConcurrentHashMap<>();
    private final GatewayProperties props;

    public QuestionStore(GatewayProperties props) {
        this.props = props;
    }

    public void save(QuestionView view) {
        byCorrelationId.put(view.correlationId(), view);
    }

    /**
     * Aplica a resposta do Worker e devolve o novo estado da pergunta, ou vazio se a pergunta
     * foi apagada pelo usuário ("limpar conversa") enquanto ainda estava sendo processada.
     */
    public Optional<QuestionView> applyAnswer(AnswerMessage answer) {
        // A checagem fica dentro do compute (atômico por chave) para não haver janela entre
        // "verificar se foi descartada" e "gravar a resposta".
        return Optional.ofNullable(byCorrelationId.compute(answer.correlationId(), (id, current) -> {
            if (discarded.remove(id) != null) {
                return null; // usuário limpou a conversa: não grava nem entrega
            }
            // current == null também acontece se o Gateway reiniciou: reconstrói pelo payload
            String question = current != null ? current.question() : answer.question();
            Instant createdAt = current != null ? current.createdAt() : answer.answeredAt();
            boolean ok = answer.status() == AnswerStatus.OK;
            return new QuestionView(id, answer.userId(), question,
                    ok ? QuestionStatus.ANSWERED : QuestionStatus.ERROR,
                    ok ? answer.answer() : null,
                    ok ? null : answer.error(),
                    createdAt, answer.answeredAt());
        }));
    }

    /** Busca restrita ao dono: um usuário nunca enxerga a pergunta de outro. */
    public Optional<QuestionView> find(String correlationId, String userId) {
        return Optional.ofNullable(byCorrelationId.get(correlationId))
                .filter(v -> v.userId().equals(userId));
    }

    public List<QuestionView> history(String userId) {
        return byCorrelationId.values().stream()
                .filter(v -> v.userId().equals(userId))
                .sorted(Comparator.comparing(QuestionView::createdAt))
                .toList();
    }

    public long countPending(String userId) {
        return byCorrelationId.values().stream()
                .filter(v -> v.userId().equals(userId) && v.status() == QuestionStatus.PENDING)
                .count();
    }

    /**
     * "Limpar conversa": apaga todo o histórico do usuário. Perguntas ainda em processamento
     * também somem, e a resposta delas, quando chegar do Worker, é descartada.
     *
     * @return quantas perguntas foram removidas
     */
    public int clear(String userId) {
        int[] removed = {0};
        Instant now = Instant.now();
        for (String id : byCorrelationId.keySet()) {
            // computeIfPresent é atômico por chave: não se mistura com um applyAnswer simultâneo
            byCorrelationId.computeIfPresent(id, (k, v) -> {
                if (!v.userId().equals(userId)) {
                    return v;
                }
                if (v.status() == QuestionStatus.PENDING) {
                    discarded.put(k, now);
                }
                removed[0]++;
                return null;
            });
        }
        return removed[0];
    }

    public void remove(String correlationId) {
        byCorrelationId.remove(correlationId);
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void evictOld() {
        Instant limit = Instant.now().minus(props.history().retention());
        byCorrelationId.values().removeIf(v -> v.createdAt().isBefore(limit));
        discarded.values().removeIf(at -> at.isBefore(limit));
    }
}
