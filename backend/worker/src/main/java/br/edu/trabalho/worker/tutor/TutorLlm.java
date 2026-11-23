package br.edu.trabalho.worker.tutor;

/**
 * Abstração da chamada à LLM. A implementação real usa Spring AI; nos testes é substituída
 * por um dublê para simular falhas e rate limit.
 */
public interface TutorLlm {

    /**
     * Chamada bloqueante: só retorna quando a LLM terminar de gerar o texto.
     */
    String ask(String systemPrompt, String userPrompt);
}
