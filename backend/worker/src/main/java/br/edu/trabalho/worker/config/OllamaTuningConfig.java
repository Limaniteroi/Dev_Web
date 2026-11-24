package br.edu.trabalho.worker.config;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ajustes que só existem no Ollama e não têm como ser definidos no application.yml.
 * <p>
 * Modelos com "thinking" (Qwen3/Qwen3.5, DeepSeek-R1...) raciocinam antes de responder.
 * Esse raciocínio consome o mesmo orçamento de tokens (num-predict): com ele ligado, o
 * qwen3.5:4b gastou os 1024 tokens pensando e devolveu uma resposta VAZIA. Para um tutor
 * didático a resposta direta é suficiente, então o padrão é desligado ({@code LLM_THINKING=false}).
 */
@Configuration
public class OllamaTuningConfig {

    @Bean
    public ChatClientCustomizer ollamaThinkingCustomizer(@Value("${app.llm.thinking:false}") boolean thinking) {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .thinkOption(thinking ? ThinkOption.ThinkBoolean.ENABLED : ThinkOption.ThinkBoolean.DISABLED)
                .build();
        return builder -> builder.defaultOptions(options);
    }
}
