package com.github.app.dify.auth.interceptor;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private JwtInterceptor interceptor;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new JwtInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(interceptor, "userRepository", userRepository);
        responseBody = new StringWriter();
    }

    @Test
    void rejectsTokenIssuedBeforePasswordChange() throws Exception {
        User user = activeUser(2);
        prepareToken(user, 1);
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        verify(response).setStatus(401);
        assertTrue(responseBody.toString().contains("密码已变更，请重新登录"));
    }

    @Test
    void acceptsTokenWithCurrentPasswordVersion() {
        User user = activeUser(2);
        prepareToken(user, 2);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        verify(request).setAttribute("userId", 1L);
        verify(request).setAttribute("username", "alice");
    }

    private void prepareToken(User user, int tokenPasswordVersion) {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("token")).thenReturn(1L);
        when(jwtUtil.getUsernameFromToken("token")).thenReturn("alice");
        when(jwtUtil.getRoleFromToken("token")).thenReturn(2);
        when(jwtUtil.getPasswordVersionFromToken("token")).thenReturn(tokenPasswordVersion);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private User activeUser(int passwordVersion) {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("hash");
        user.setPasswordVersion(passwordVersion);
        user.setRole(2);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }
}
