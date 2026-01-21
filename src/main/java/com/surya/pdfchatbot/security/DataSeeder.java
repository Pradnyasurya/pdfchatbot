package com.surya.pdfchatbot.security;

import com.surya.pdfchatbot.model.entity.AppUser;
import com.surya.pdfchatbot.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin.username:admin}")
    private String adminUsername;

    @Value("${app.security.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.security.user.username:user}")
    private String userUsername;

    @Value("${app.security.user.password:user123}")
    private String userPassword;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void seedUsers() {
        seedUser(adminUsername, adminPassword, AppUser.Role.ADMIN);
        seedUser(userUsername, userPassword, AppUser.Role.USER);
    }

    private void seedUser(String username, String password, AppUser.Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            AppUser user = new AppUser(username, passwordEncoder.encode(password), role);
            userRepository.save(user);
            log.info("Seeded {} user: {}", role, username);
        }
    }
}
