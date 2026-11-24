package br.edu.trabalho.common.messaging;

import java.time.Instant;

/**
 * Payload publicado pelo Worker em {@code respostas.queue}.
 *
 * @param correlationId mesmo identificador da {@link QuestionMessage} de origem
 * @param userId        dono da pergunta; usado pelo Gateway para achar o stream SSE certo
 * @param question      texto original da pergunta (permite ao Gateway reconstruir o histórico
 *                      mesmo que tenha reiniciado enquanto o Worker processava)
 * @param status        {@link AnswerStatus#OK} ou {@link AnswerStatus#ERROR}
 * @param answer        texto gerado pela LLM (quando OK)
 * @param error         mensagem amigável de erro (quando ERROR)
 * @param attempts      quantas tentativas de chamada à LLM foram feitas
 * @param answeredAt    instante em que o Worker concluiu o processamento
 */
public record AnswerMessage(
        String correlationId,
        String userId,
        String question,
        AnswerStatus status,
        String answer,
        String error,
        int attempts,
        Instant answeredAt
) {

    public static AnswerMessage ok(QuestionMessage question, String answer, int attempts) {
        return new AnswerMessage(question.correlationId(), question.userId(), question.question(),
                AnswerStatus.OK, answer, null, attempts, Instant.now());
    }

    public static AnswerMessage error(QuestionMessage question, String error, int attempts) {
        return new AnswerMessage(question.correlationId(), question.userId(), question.question(),
                AnswerStatus.ERROR, null, error, attempts, Instant.now());
    }
}
