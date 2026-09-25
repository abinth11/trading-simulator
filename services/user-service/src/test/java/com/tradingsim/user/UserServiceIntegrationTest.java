package com.tradingsim.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsim.user.dto.UserDtos.*;
import com.tradingsim.user.entity.User;
import com.tradingsim.user.repository.UserRepository;
import com.tradingsim.user.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_withValidData_returns201AndTokens() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setUsername("testuser");
        req.setPassword("password123");

        var result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.cashBalance").value(100000.00))
                .andReturn();

        // Verify user is persisted
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("dupe@example.com");
        req.setUsername("user1");
        req.setPassword("password123");

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second registration with same email
        req.setUsername("user2");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void login_withValidCredentials_returnsTokens() throws Exception {
        // Register first
        var registerReq = new RegisterRequest();
        registerReq.setEmail("login@example.com");
        registerReq.setUsername("loginuser");
        registerReq.setPassword("password123");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)));

        // Now login
        var loginReq = new LoginRequest();
        loginReq.setEmail("login@example.com");
        loginReq.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        var loginReq = new LoginRequest();
        loginReq.setEmail("nobody@example.com");
        loginReq.setPassword("wrongpass");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withShortPassword_returns400() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setUsername("testuser");
        req.setPassword("short"); // too short

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    // ── Admin API access ──────────────────────────────────────────
    private String accessTokenFor(User.Role role) {
        User user = userRepository.save(User.builder()
                .email(role.name().toLowerCase() + "@example.com")
                .username(role.name().toLowerCase() + "user")
                .passwordHash("unused")
                .role(role)
                .build());
        return jwtService.generateAccessToken(user.getId(), user.getEmail(), role.name());
    }

    @Test
    void adminApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApi_withUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + accessTokenFor(User.Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminApi_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + accessTokenFor(User.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }
}
