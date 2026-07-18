package com.github.app.dify.auth.service;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.auth.req.ForgotPasswordRequest;
import com.github.app.dify.auth.req.LoginRequest;
import com.github.app.dify.auth.req.RegisterRequest;
import com.github.app.dify.auth.req.VerificationCodePurpose;
import com.github.app.dify.auth.resp.LoginResponse;
import com.github.app.dify.auth.resp.RegisterResponse;
import com.github.app.dify.auth.service.impl.AuthServiceImpl;
import com.github.app.dify.auth.util.JwtUtil;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.permission.service.RbacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RbacService rbacService;

    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl();
        ReflectionTestUtils.setField(authService, "userRepository", userRepository);
        ReflectionTestUtils.setField(authService, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(authService, "rbacService", rbacService);
        ReflectionTestUtils.setField(authService, "emailVerificationService", emailVerificationService);
    }

    @Test
    void registerConsumesEmailCodeBeforeSavingUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(" new-user ");
        request.setEmail("User@Example.com");
        request.setPassword("password1");
        request.setVerificationCode("123456");

        when(userRepository.existsByUsername("new-user")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            return user;
        });

        RegisterResponse response = authService.register(request);

        InOrder order = inOrder(emailVerificationService, userRepository);
        order.verify(emailVerificationService).verifyAndConsume(
                "user@example.com", VerificationCodePurpose.REGISTER, "123456");
        order.verify(userRepository).save(any(User.class));
        assertEquals(9L, response.getUserId());
        assertEquals("user@example.com", response.getEmail());
        assertEquals(0, response.getStatus());
    }

    @Test
    void loginAcceptsRegisteredEmail() {
        User user = activeUser("alice", "alice@example.com", "password1", 3);
        LoginRequest request = new LoginRequest();
        request.setUsername("Alice@Example.com");
        request.setPassword("password1");

        when(userRepository.findByUsername("Alice@Example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("Alice@Example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(1L, "alice", 2, 3)).thenReturn("jwt-token");
        when(rbacService.getUserRoles(1L)).thenReturn(Collections.emptyList());
        when(rbacService.getUserPermissionCodes(1L)).thenReturn(Collections.emptyList());

        LoginResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("alice@example.com", response.getEmail());
    }

    @Test
    void changePasswordRejectsWrongOriginalPasswordWithoutWriting() {
        User user = activeUser("alice", "alice@example.com", "password1", 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.changePassword(1L, "wrong-password", "newPassword2"));

        assertEquals("原密码错误", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
        assertEquals(2, user.getPasswordVersion());
    }

    @Test
    void changePasswordWithOriginalPasswordIncrementsPasswordVersion() {
        User user = activeUser("alice", "alice@example.com", "password1", 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.changePassword(1L, "password1", "newPassword2");

        verify(userRepository).save(user);
        assertEquals(3, user.getPasswordVersion());
        assertTrue(new BCryptPasswordEncoder().matches("newPassword2", user.getPassword()));
    }

    @Test
    void emailResetConsumesCodeAndIncrementsPasswordVersion() {
        User user = activeUser("alice", "alice@example.com", "password1", 4);
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("Alice@Example.com");
        request.setVerificationCode("654321");
        request.setNewPassword("newPassword2");
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        authService.resetPasswordByEmail(request);

        InOrder order = inOrder(emailVerificationService, userRepository);
        order.verify(emailVerificationService).verifyAndConsume(
                "alice@example.com", VerificationCodePurpose.RESET_PASSWORD, "654321");
        order.verify(userRepository).save(user);
        assertEquals(5, user.getPasswordVersion());
        assertTrue(new BCryptPasswordEncoder().matches("newPassword2", user.getPassword()));
    }

    @Test
    void emailResetDoesNotWriteWhenCodeValidationFails() {
        User user = activeUser("alice", "alice@example.com", "password1", 4);
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("alice@example.com");
        request.setVerificationCode("000000");
        request.setNewPassword("newPassword2");
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new BusinessException("邮箱验证码错误"))
                .when(emailVerificationService)
                .verifyAndConsume("alice@example.com", VerificationCodePurpose.RESET_PASSWORD, "000000");

        assertThrows(BusinessException.class, () -> authService.resetPasswordByEmail(request));

        verify(userRepository, never()).save(any(User.class));
        assertEquals(4, user.getPasswordVersion());
    }

    private User activeUser(String username, String email, String rawPassword, int passwordVersion) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        user.setPasswordVersion(passwordVersion);
        user.setRole(2);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }
}
