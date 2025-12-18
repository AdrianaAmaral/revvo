package com.revvo.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

@Component
public class JwtClaimsExtractor {

    private final ObjectMapper objectMapper;

    public JwtClaimsExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Tenta extrair claims de um JWT presente em:
     * - Authorization: Bearer <jwt>
     * - X-Forwarded-Access-Token: <jwt>
     * - X-JWT-Assertion: <jwt>
     *
     * Retorna mapa vazio se não tiver token ou não conseguir parsear.
     */
    public Map<String, Object> extractClaims(HttpServletRequest request) {
        String token = resolveJwt(request);
        if (token == null || token.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            // JWT = header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) return Collections.emptyMap();

            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);

            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String resolveJwt(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.toLowerCase().startsWith("bearer ")) {
            return auth.substring("bearer ".length()).trim();
        }

        String forwarded = request.getHeader("X-Forwarded-Access-Token");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.trim();

        String assertion = request.getHeader("X-JWT-Assertion");
        if (assertion != null && !assertion.isBlank()) return assertion.trim();

        return null;
    }

    private byte[] base64UrlDecode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url);
    }
}
