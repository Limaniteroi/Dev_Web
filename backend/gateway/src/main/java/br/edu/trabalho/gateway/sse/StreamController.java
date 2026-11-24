package br.edu.trabalho.gateway.sse;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Stream SSE do usuário autenticado. O front-end abre com
 * {@code @microsoft/fetch-event-source}, enviando {@code Authorization: Bearer <jwt>}.
 * O emitter fica vinculado ao {@code sub} do token: só as respostas desse usuário passam por ele.
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final SseHub hub;

    public StreamController(SseHub hub) {
        this.hub = hub;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        return hub.connect(jwt.getSubject());
    }
}
