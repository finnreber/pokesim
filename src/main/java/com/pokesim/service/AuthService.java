package com.pokesim.service;

import com.pokesim.api.dto.LoginRequest;
import com.pokesim.api.dto.RegisterRequest;
import com.pokesim.api.dto.TokenResponse;
import com.pokesim.api.dto.UserResponse;
import com.pokesim.api.error.ConflictException;
import com.pokesim.api.error.UnauthorizedException;
import com.pokesim.persistence.UserEntity;
import com.pokesim.persistence.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    /** Compared against when the username does not exist, so "no such user" costs the same time as "wrong password". */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        if (users.existsByUsername(username)) throw new ConflictException("Username already taken");
        try {
            UserEntity saved = users.saveAndFlush(new UserEntity(username, encoder.encode(request.password())));
            return new UserResponse(saved.getId(), saved.getUsername());
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("Username already taken");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<UserEntity> user = users.findByUsername(normalize(request.username()));
        boolean passwordOk = encoder.matches(request.password(), user.map(UserEntity::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !passwordOk) {
            // Same message for both cases: do not reveal which usernames exist.
            throw new UnauthorizedException("Invalid username or password");
        }
        return tokens.issue(user.get());
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
