package com.ecommerce.controller;

import com.ecommerce.dto.AuthDto;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(com.ecommerce.config.SecurityConfig.class)
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean com.ecommerce.security.JwtService jwtService;
    @MockBean com.ecommerce.security.UserDetailsServiceImpl userDetailsService;
    @MockBean com.ecommerce.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final AuthDto.AuthResponse MOCK_RESPONSE = AuthDto.AuthResponse.builder()
            .token("jwt-token").type("Bearer").userId(1L)
            .email("jane@example.com").firstName("Jane")
            .lastName("Doe").role("USER").build();

    // ─── Register ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register → 201 Created on success")
    void register_validRequest_returns201() throws Exception {
        when(authService.register(any())).thenReturn(MOCK_RESPONSE);

        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest(
                "Jane", "Doe", "jane@example.com", "Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.email").value("jane@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/register → 400 on invalid email")
    void register_invalidEmail_returns400() throws Exception {
        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest(
                "Jane", "Doe", "not-an-email", "Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/register → 400 on weak password")
    void register_weakPassword_returns400() throws Exception {
        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest(
                "Jane", "Doe", "jane@example.com", "weak");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register → 400 on duplicate email")
    void register_duplicateEmail_returns400() throws Exception {
        when(authService.register(any())).thenThrow(
                new BusinessException("Email is already registered"));

        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest(
                "Jane", "Doe", "jane@example.com", "Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    // ─── Login ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login → 200 OK on valid credentials")
    void login_validCredentials_returns200() throws Exception {
        when(authService.login(any())).thenReturn(MOCK_RESPONSE);

        AuthDto.LoginRequest request = new AuthDto.LoginRequest(
                "jane@example.com", "Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/login → 401 on bad credentials")
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        AuthDto.LoginRequest request = new AuthDto.LoginRequest(
                "jane@example.com", "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login → 400 on missing fields")
    void login_missingFields_returns400() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
