package br.edu.trabalho.gateway.question;

public enum QuestionStatus {
    /** Publicada em perguntas.queue, aguardando o Worker. */
    PENDING,
    /** O Worker devolveu uma resposta da LLM. */
    ANSWERED,
    /** O Worker esgotou as tentativas de chamar a LLM. */
    ERROR
}
