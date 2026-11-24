package br.edu.trabalho.worker.tutor;

import br.edu.trabalho.common.messaging.AnswerMessage;
import br.edu.trabalho.common.messaging.QuestionMessage;
import org.slf4j.Logger;
import br.edu.trabalho.worker.config.WorkerConfig;
import br.edu.trabalho.worker.config.WorkerProperties;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Regra de negócio do Worker: enriquecer o prompt, chamar a LLM com retry e
 * montar a {@link AnswerMessage} com o mesmo correlationId.
 */
@Service
public class QuestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(QuestionProcessor.class);

    static final String USER_FACING_ERROR =
            "Não foi possível gerar a resposta agora. O serviço de IA está indisponível ou "
                    + "atingiu o limite de requisições. Tente novamente em instantes.";

    private final PromptEnricher enricher;
    private final TutorLlm llm;
    private final RetryTemplate retryTemplate;

    @Autowired
    public QuestionProcessor(PromptEnricher enricher, TutorLlm llm, WorkerProperties props) {
        this(enricher, llm, WorkerConfig.llmRetryTemplate(props));
    }

    QuestionProcessor(PromptEnricher enricher, TutorLlm llm, RetryTemplate retryTemplate) {
        this.enricher = enricher;
        this.llm = llm;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Nunca lança exceção por falha da LLM: depois de esgotar as tentativas devolve uma
     * resposta com status ERROR, para que o front-end seja avisado em vez de ficar esperando.
     */
    public Result process(QuestionMessage question) {
        String prompt = enricher.enrich(question.question());
        int[] attempts = {0};
        long start = System.currentTimeMillis();
        try {
            String answer = retryTemplate.execute(ctx -> {
                attempts[0] = ctx.getRetryCount() + 1;
                return llm.ask(enricher.systemPrompt(), prompt);
            });
            log.info("Pergunta {} respondida em {} ms ({} tentativa(s))",
                    question.correlationId(), System.currentTimeMillis() - start, attempts[0]);
            return new Result(AnswerMessage.ok(question, answer, attempts[0]), null);
        } catch (Exception e) {
            log.error("Pergunta {} falhou após {} tentativa(s): {}",
                    question.correlationId(), attempts[0], e.toString());
            return new Result(AnswerMessage.error(question, USER_FACING_ERROR, attempts[0]), e);
        }
    }

    /**
     * @param answer  mensagem a publicar em respostas.queue
     * @param failure exceção final quando todas as tentativas falharam (null em caso de sucesso)
     */
    public record Result(AnswerMessage answer, Exception failure) {
        public boolean failed() {
            return failure != null;
        }
    }
}
