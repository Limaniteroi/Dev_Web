package br.edu.trabalho.worker.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Chamada à LLM via Spring AI. O {@link ChatClient} abstrai o provedor (OpenAI, Ollama, ...)
 * da mesma forma que o Spring Data abstrai o banco: trocar de provedor é só configuração
 * ({@code spring.ai.model.chat}).
 */
@Component
public class SpringAiTutorLlm implements TutorLlm {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTutorLlm.class);

    private final ChatClient chatClient;

    public SpringAiTutorLlm(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String ask(String systemPrompt, String userPrompt) {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();

        String content = response != null && response.getResult() != null
                ? response.getResult().getOutput().getText()
                : null;
        if (response != null) {
            Usage usage = response.getMetadata().getUsage();
            String finish = response.getResult() != null
                    ? response.getResult().getMetadata().getFinishReason() : null;
            // Tokens consomem orçamento/custo: registrar ajuda a acompanhar o uso da LLM
            log.info("LLM {}: {} tokens de entrada, {} de saída, fim={}",
                    response.getMetadata().getModel(), usage.getPromptTokens(), usage.getCompletionTokens(), finish);
        }
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("A LLM retornou uma resposta vazia");
        }
        return content;
    }
}
