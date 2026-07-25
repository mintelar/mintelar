package com.hilo.rewards.security;

import com.hilo.rewards.exception.BusinessException;
import com.hilo.rewards.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.util.Optional;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final JwtValidator jwtValidator;
    private final JdbcTemplate jdbcTemplate;

    public AuthenticationFilter(JwtValidator jwtValidator, JdbcTemplate jdbcTemplate) {
        this.jwtValidator = jwtValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestId = java.util.UUID.randomUUID().toString();

        try {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendError(response, ErrorCode.UNAUTHORIZED, requestId, "Missing authorization header");
                return;
            }

            String token = authHeader.substring(7);
            Optional<Jwt> jwt = jwtValidator.validate(token);

            if (jwt.isEmpty()) {
                sendError(response, ErrorCode.UNAUTHORIZED, requestId, "Invalid or expired token");
                return;
            }

            String userId = jwtValidator.getUserId(jwt.get());

            // Fetch user role from profiles
            String role = jdbcTemplate.queryForObject(
                "SELECT role FROM profiles WHERE id = ?",
                String.class,
                userId
            );

            if (role == null) {
                sendError(response, ErrorCode.FORBIDDEN, requestId, "User not found in profiles");
                return;
            }

            if (!"admin".equals(role)) {
                sendError(response, ErrorCode.FORBIDDEN, requestId, "Admin role required");
                return;
            }

            // Set attributes for downstream use
            request.setAttribute("userId", userId);
            request.setAttribute("userRole", role);
            request.setAttribute("requestId", requestId);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Authentication error", e);
            sendError(response, ErrorCode.INTERNAL_ERROR, requestId, null);
        }
    }

    private void sendError(HttpServletResponse response, ErrorCode errorCode, String requestId, String message) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String body = String.format(
            "{\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
            errorCode.getCode(),
            message != null ? message : errorCode.getMessage(),
            requestId
        );
        response.getWriter().write(body);
    }
}
