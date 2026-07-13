package com.github.app.dify.common.security;

import com.github.app.dify.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Resolves the authenticated user from interceptor-populated request data or a bearer token. */
@Component
public class RequestUserResolver {

    private final JwtUtil jwtUtil;

    public RequestUserResolver(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public Optional<RequestUser> resolve(HttpServletRequest request) {
        Long userId = toLong(request.getAttribute("userId"));
        String username = toString(request.getAttribute("username"));
        if (userId != null) {
            return Optional.of(new RequestUser(userId, username));
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        try {
            String token = authorization.substring(7);
            return Optional.of(new RequestUser(
                    jwtUtil.getUserIdFromToken(token),
                    jwtUtil.getUsernameFromToken(token)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String toString(Object value) {
        return value == null ? null : value.toString();
    }

    public record RequestUser(Long userId, String username) {
    }
}
