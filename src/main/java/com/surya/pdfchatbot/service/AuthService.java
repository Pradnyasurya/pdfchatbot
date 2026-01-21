package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.model.dto.AuthRequest;
import com.surya.pdfchatbot.model.dto.AuthResponse;
import com.surya.pdfchatbot.model.entity.AppUser;
import com.surya.pdfchatbot.repository.UserRepository;
import com.surya.pdfchatbot.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long expirationSeconds;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       @Value("${app.security.jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.expirationSeconds = expirationSeconds;
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        AppUser user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, "Bearer", expirationSeconds);
    }
}
