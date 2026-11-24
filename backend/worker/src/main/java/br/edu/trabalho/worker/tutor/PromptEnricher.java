package br.edu.trabalho.worker.tutor;

import br.edu.trabalho.worker.config.WorkerProperties;
import org.springframework.stereotype.Component;

/**
 * Etapa de "prompt engineering": envelopa a pergunta crua do usuário numa máscara
 * que define o papel de tutor acadêmico de Ciência da Computação.
 * <p>
 * Ex.: "O que é escopo?" vira "Levando em conta que você é um tutor acadêmico de Ciência da
 * Computação, responda de forma didática à seguinte pergunta: O que é escopo?"
 */
@Component
public class PromptEnricher {

    static final String PLACEHOLDER = "{pergunta}";

    private final String template;
    private final String systemPrompt;

    public PromptEnricher(WorkerProperties props) {
        this.template = props.tutor().promptTemplate();
        this.systemPrompt = props.tutor().systemPrompt();
        if (template == null || !template.contains(PLACEHOLDER)) {
            throw new IllegalStateException("app.tutor.prompt-template deve conter " + PLACEHOLDER);
        }
    }

    public String enrich(String question) {
        // String.replace (e não um template engine) para que chaves digitadas pelo usuário
        // — comuns em perguntas sobre código — não sejam interpretadas.
        return template.replace(PLACEHOLDER, question.strip());
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
