package br.edu.trabalho.gateway.security;

import br.edu.trabalho.gateway.config.GatewayProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Emite JWTs assinados. O {@code sub} do token é o userId usado em toda a aplicação:
 * nas mensagens do RabbitMQ e para vincular o stream SSE ao usuário.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final GatewayProperties props;

    public TokenService(JwtEncoder encoder, GatewayProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public IssuedToken issue(String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.security().jwtTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("tutoria-gateway")
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, username, expiresAt);
    }

    public record IssuedToken(String token, String username, Instant expiresAt) {
    }
}
