package br.edu.trabalho.gateway;

import br.edu.trabalho.common.messaging.AnswerMessage;
import br.edu.trabalho.common.messaging.AnswerStatus;
import br.edu.trabalho.common.messaging.MessagingProperties;
import br.edu.trabalho.common.messaging.QuestionMessage;
import br.edu.trabalho.gateway.messaging.AnswerListener;
import br.edu.trabalho.gateway.question.QuestionPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        // Sem broker nos testes: listeners não iniciam e a publicação é simulada
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "app.rate-limit.questions-per-minute=3",
        "app.rate-limit.max-pending=10",
        // Fixo aqui para o teste não depender do backend/.env de quem roda
        "app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000"
})
@AutoConfigureMockMvc
class GatewayApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AnswerListener answerListener;
    @Autowired
    MessagingProperties messaging;
    @MockitoBean
    QuestionPublisher publisher;

    private String login(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private String register(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"segredo1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private JsonNode ask(String token, String question) throws Exception {
        String body = mvc.perform(post("/api/perguntas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("question", question))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void rotasProtegidasExigemJwt() throws Exception {
        mvc.perform(get("/api/perguntas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stream")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/perguntas").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/perguntas").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAceitaOFrontEndPorLocalhostE127EBloqueiaOutrasOrigens() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:3000", "http://127.0.0.1:3000"}) {
            mvc.perform(post("/api/auth/login").header("Origin", origin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"aluno\",\"password\":\"senha123\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/login").header("Origin", "http://site-malicioso.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"aluno\",\"password\":\"senha123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroDeUsuarioExistenteRetorna409ComMensagem() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"aluno\",\"password\":\"senha123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Usuário já existe"));
    }

    @Test
    void routingKeyDaRespostaEhDoUsuarioEOBindingAceitaQualquerUsuario() {
        assertThat(messaging.answersRoutingKeyFor("aluno")).isEqualTo("respostas.aluno");
        assertThat(messaging.answersBindingPattern()).isEqualTo("respostas.#");
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"aluno\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perguntaEPublicadaComUserIdDoTokenERespostaAtualizaOHistorico() throws Exception {
        String token = register("maria");

        JsonNode accepted = ask(token, "  O que é escopo?  ");
        String correlationId = accepted.get("correlationId").asText();
        assertThat(accepted.get("status").asText()).isEqualTo("PENDING");
        assertThat(accepted.has("userId")).isFalse();

        ArgumentCaptor<QuestionMessage> sent = ArgumentCaptor.forClass(QuestionMessage.class);
        verify(publisher).publish(sent.capture());
        assertThat(sent.getValue().userId()).isEqualTo("maria");
        assertThat(sent.getValue().question()).isEqualTo("O que é escopo?");
        assertThat(sent.getValue().correlationId()).isEqualTo(correlationId);

        // Simula o Worker devolvendo a resposta pela respostas.queue
        answerListener.onAnswer(new AnswerMessage(correlationId, "maria", "O que é escopo?",
                AnswerStatus.OK, "Escopo é...", null, 1, Instant.now()));

        mvc.perform(get("/api/perguntas/" + correlationId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answer").value("Escopo é..."));
    }

    @Test
    void limparConversaApagaTudoEDescartaRespostaQueChegaDepois() throws Exception {
        String carla = register("carla");
        String answered = ask(carla, "Pergunta respondida").get("correlationId").asText();
        String pending = ask(carla, "Pergunta ainda em andamento").get("correlationId").asText();
        answerListener.onAnswer(new AnswerMessage(answered, "carla", "Pergunta respondida",
                AnswerStatus.OK, "Resposta", null, 1, Instant.now()));

        String outro = register("davi");
        String doOutro = ask(outro, "Pergunta do Davi").get("correlationId").asText();

        mvc.perform(delete("/api/perguntas").header("Authorization", carla))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/perguntas").header("Authorization", carla))
                .andExpect(jsonPath("$").isEmpty());

        // A resposta da pergunta que estava em andamento chega depois da limpeza: é descartada
        answerListener.onAnswer(new AnswerMessage(pending, "carla", "Pergunta ainda em andamento",
                AnswerStatus.OK, "Resposta atrasada", null, 1, Instant.now()));
        mvc.perform(get("/api/perguntas").header("Authorization", carla))
                .andExpect(jsonPath("$").isEmpty());

        // O histórico de outro usuário não é afetado
        mvc.perform(get("/api/perguntas/" + doOutro).header("Authorization", outro))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioNaoEnxergaPerguntaDeOutro() throws Exception {
        String joao = register("joao");
        String correlationId = ask(joao, "Pergunta do João").get("correlationId").asText();

        String aluno = login("aluno");
        mvc.perform(get("/api/perguntas/" + correlationId).header("Authorization", aluno))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/perguntas").header("Authorization", aluno))
                .andExpect(jsonPath("$[?(@.correlationId == '" + correlationId + "')]").isEmpty());
    }

    @Test
    void perguntaVaziaRetorna400() throws Exception {
        String token = register("ana");
        mvc.perform(post("/api/perguntas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limiteDePerguntasPorMinutoRetorna429() throws Exception {
        String token = register("bia");
        for (int i = 0; i < 3; i++) {
            ask(token, "Pergunta " + i);
        }
        mvc.perform(post("/api/perguntas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"mais uma\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
