package com.kbv.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.entity.Role;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.repository.RoleRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.SystemSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for login through account lockout and rate limiting
 * (Phase 5 Step 7), run through the real Spring context and filter chain -
 * not mocked. This is the scenario that caught a real bug during Step 7's
 * live verification: the failed-attempt counter was silently rolled back
 * because AuthServiceImpl.login() was @Transactional and threw an exception
 * right after recording the attempt. Only a test that goes through the real
 * TransactionManager (unlike the LoginAttemptServiceImplTest unit test,
 * which mocks the repository and can't see a rollback) would have caught it.
 *
 * <p>All requests hit /api/auth/login, which RateLimitFilter tracks
 * per-IP+path with an in-memory, single-JVM sliding window (10 requests /
 * 60s) - MockMvc requests share a simulated remote address, so this single
 * test method walks through success -> lockout -> rate-limit in one
 * sequence deliberately, rather than splitting into independent @Test
 * methods that would corrupt each other's request counts.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SystemSettingsService systemSettingsService;

    private static final String CORRECT_PASSWORD = "Correct-Horse-Battery-Staple-1";
    private static final String WRONG_PASSWORD = "definitely-wrong";

    private User testUser;

    @BeforeEach
    void createTestUser() {
        Role studentRole = roleRepository.findByName(RoleType.STUDENT).orElseThrow();
        User user = new User();
        user.setEmail("auth-it-" + UUID.randomUUID() + "@kbv.edu");
        user.setPasswordHash(passwordEncoder.encode(CORRECT_PASSWORD));
        user.setFirstName("Integration");
        user.setLastName("Test");
        user.setRole(studentRole);
        testUser = userRepository.save(user);
    }

    @AfterEach
    void deleteTestUser() {
        userRepository.deleteById(testUser.getId());
    }

    @Test
    void loginThenLockoutThenRateLimit() throws Exception {
        int maxAttempts = systemSettingsService.getActiveEntity().getMaxLoginAttempts();

        // 1. Correct credentials succeed.
        mockMvc.perform(loginRequest(testUser.getEmail(), CORRECT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());

        // 2. maxAttempts wrong-password attempts -> each 401, account locks on the last one.
        for (int i = 0; i < maxAttempts; i++) {
            mockMvc.perform(loginRequest(testUser.getEmail(), WRONG_PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
        }

        User locked = userRepository.findByIdAndDeletedFalse(testUser.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(locked.isLocked()).isTrue();

        // 3. Further attempts (even with the correct password) are rejected as locked,
        //    until the rate limiter's 10-requests/60s window is exhausted for this IP+path.
        int requestsSoFar = 1 + maxAttempts;
        int remainingBeforeRateLimit = 10 - requestsSoFar;
        for (int i = 0; i < remainingBeforeRateLimit; i++) {
            mockMvc.perform(loginRequest(testUser.getEmail(), CORRECT_PASSWORD))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.error.code", is("ACCOUNT_LOCKED")));
        }

        // 4. The 11th request against this path this window is rate-limited before
        //    it even reaches the lockout check.
        mockMvc.perform(loginRequest(testUser.getEmail(), CORRECT_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code", is("RATE_LIMITED")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password)));
    }
}
