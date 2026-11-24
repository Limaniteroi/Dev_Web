package br.edu.trabalho.gateway.question;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * Estado de uma pergunta como o front-end enxerga (histórico, resposta do POST e eventos SSE).
 */
public record QuestionView(
        String correlationId,
        @JsonIgnore String userId,
        String question,
        QuestionStatus status,
        String answer,
        String error,
        Instant createdAt,
        Instant answeredAt
) {

    public static QuestionView pending(String correlationId, String userId, String question, Instant createdAt) {
        return new QuestionView(correlationId, userId, question, QuestionStatus.PENDING, null, null, createdAt, null);
    }
}
