package br.edu.trabalho.worker.tutor;

import br.edu.trabalho.common.messaging.AnswerMessage;
import br.edu.trabalho.common.messaging.AnswerStatus;
import br.edu.trabalho.common.messaging.QuestionMessage;
import br.edu.trabalho.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionProcessorTest {

    private static final WorkerProperties PROPS = new WorkerProperties(
            new WorkerProperties.Retry(3, 10),
            new WorkerProperties.Tutor("sistema", "Tutor de CC, responda: {pergunta}"));

    private static final QuestionMessage QUESTION =
            new QuestionMessage("corr-1", "alice", "  O que é escopo?  ", Instant.now());

    /** Dublê da LLM: falha as primeiras N chamadas e registra os prompts recebidos. */
    static class FlakyLlm implements TutorLlm {
        final int failuresBeforeSuccess;
        final List<String> prompts = new ArrayList<>();

        FlakyLlm(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public String ask(String systemPrompt, String userPrompt) {
            prompts.add(userPrompt);
            if (prompts.size() <= failuresBeforeSuccess) {
                throw new RuntimeException("429 Too Many Requests");
            }
            return "Escopo é a região do código onde um nome é visível.";
        }
    }

    private QuestionProcessor processor(TutorLlm llm) {
        return new QuestionProcessor(new PromptEnricher(PROPS), llm, PROPS);
    }

    @Test
    void enriqueceOPromptEPreservaCorrelationId() {
        FlakyLlm llm = new FlakyLlm(0);

        QuestionProcessor.Result result = processor(llm).process(QUESTION);

        assertThat(llm.prompts).containsExactly("Tutor de CC, responda: O que é escopo?");
        AnswerMessage answer = result.answer();
        assertThat(result.failed()).isFalse();
        assertThat(answer.status()).isEqualTo(AnswerStatus.OK);
        assertThat(answer.correlationId()).isEqualTo("corr-1");
        assertThat(answer.userId()).isEqualTo("alice");
        assertThat(answer.attempts()).isEqualTo(1);
    }

    @Test
    void refazAChamadaQuandoALlmFalhaTemporariamente() {
        FlakyLlm llm = new FlakyLlm(2);

        QuestionProcessor.Result result = processor(llm).process(QUESTION);

        assertThat(result.failed()).isFalse();
        assertThat(result.answer().status()).isEqualTo(AnswerStatus.OK);
        assertThat(result.answer().attempts()).isEqualTo(3);
        assertThat(llm.prompts).hasSize(3);
    }

    @Test
    void devolveRespostaDeErroQuandoAsTentativasAcabam() {
        FlakyLlm llm = new FlakyLlm(Integer.MAX_VALUE);

        QuestionProcessor.Result result = processor(llm).process(QUESTION);

        assertThat(result.failed()).isTrue();
        assertThat(result.answer().status()).isEqualTo(AnswerStatus.ERROR);
        assertThat(result.answer().error()).isEqualTo(QuestionProcessor.USER_FACING_ERROR);
        assertThat(result.answer().correlationId()).isEqualTo("corr-1");
        assertThat(result.answer().attempts()).isEqualTo(3);
        assertThat(llm.prompts).hasSize(3);
    }

    @Test
    void chavesNaPerguntaNaoQuebramOTemplate() {
        PromptEnricher enricher = new PromptEnricher(PROPS);

        assertThat(enricher.enrich("o que faz for (int i = 0; i < n; i++) { }?"))
                .isEqualTo("Tutor de CC, responda: o que faz for (int i = 0; i < n; i++) { }?");
    }
}
