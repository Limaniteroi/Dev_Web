package br.edu.trabalho.gateway.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final InMemoryUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, InMemoryUserDetailsManager users,
                          PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** Troca usuário/senha por um JWT. Credenciais inválidas => 401. */
    @PostMapping("/login")
    public TokenService.IssuedToken login(@Valid @RequestBody Credentials credentials) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(credentials.username(), credentials.password()));
        return tokenService.issue(auth.getName());
    }

    /** Cria um usuário (em memória) e já devolve o JWT. Usuário existente => 409. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenService.IssuedToken register(@Valid @RequestBody NewUser newUser) {
        synchronized (users) {
            if (users.userExists(newUser.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuário já existe");
            }
            users.createUser(User.withUsername(newUser.username())
                    .password(passwordEncoder.encode(newUser.password()))
                    .roles("USER")
                    .build());
        }
        return tokenService.issue(newUser.username());
    }

    public record Credentials(@NotBlank String username, @NotBlank String password) {
    }

    public record NewUser(
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}",
                    message = "deve ter de 3 a 32 caracteres (letras, números, _ . -)")
            String username,
            @NotBlank
            @Size(min = 6, max = 72, message = "deve ter entre 6 e 72 caracteres")
            String password) {
    }
}
