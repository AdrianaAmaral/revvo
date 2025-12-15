package com.revvo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller para testar a autenticação via JWT do XSUAA.
 *
 * Use este endpoint para verificar se o token JWT está sendo validado corretamente
 * e se as roles/scopes estão sendo extraídas.
 */
@Slf4j
@RestController
@RequestMapping("/sap/jwt")
@RequiredArgsConstructor
public class JwtTestController {

    /**
     * Endpoint para testar autenticação via JWT.
     *
     * Como testar:
     * 1. Obtenha um token JWT do XSUAA
     * 2. Envie uma requisição com header: Authorization: Bearer <token>
     * 3. Este endpoint retornará informações sobre o token e o usuário autenticado
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testJwt(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (authentication == null) {
            response.put("authenticated", false);
            response.put("message", "Nenhuma autenticação encontrada");
            return ResponseEntity.ok(response);
        }

        response.put("authenticated", true);
        response.put("authenticationType", authentication.getClass().getSimpleName());
        response.put("username", authentication.getName());
        response.put("principal", authentication.getPrincipal().getClass().getSimpleName());

        // Extrair authorities (roles/scopes)
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        response.put("authorities", authorities);

        // Se for JwtAuthenticationToken, extrair claims do token
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            Map<String, Object> tokenInfo = new LinkedHashMap<>();
            tokenInfo.put("subject", jwt.getSubject());
            tokenInfo.put("issuer", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
            tokenInfo.put("issuedAt", jwt.getIssuedAt());
            tokenInfo.put("expiresAt", jwt.getExpiresAt());

            // Claims importantes do XSUAA
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("user_name", jwt.getClaim("user_name"));
            claims.put("email", jwt.getClaim("email"));
            claims.put("given_name", jwt.getClaim("given_name"));
            claims.put("family_name", jwt.getClaim("family_name"));
            claims.put("scope", jwt.getClaim("scope"));
            claims.put("zid", jwt.getClaim("zid")); // zone id
            claims.put("grant_type", jwt.getClaim("grant_type"));
            claims.put("client_id", jwt.getClaim("client_id"));

            tokenInfo.put("claims", claims);
            tokenInfo.put("allClaims", jwt.getClaims());

            response.put("jwtToken", tokenInfo);
        }

        log.info("JWT Test - User: {}, Authorities: {}", authentication.getName(), authorities);

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para verificar o status da autenticação JWT.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> jwtStatus(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("timestamp", System.currentTimeMillis());
        response.put("authenticated", authentication != null && authentication.isAuthenticated());

        if (authentication != null) {
            response.put("username", authentication.getName());
            response.put("authType", authentication.getClass().getSimpleName());
            response.put("rolesCount", authentication.getAuthorities().size());

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            response.put("roles", roles);
        } else {
            response.put("message", "Não autenticado. Envie um JWT válido no header Authorization: Bearer <token>");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint protegido que requer roles específicas.
     * Teste para verificar se as roles do JWT estão sendo aplicadas corretamente.
     */
    @GetMapping("/protected")
    public ResponseEntity<Map<String, Object>> protectedEndpoint(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("message", "Você acessou um endpoint protegido!");
        response.put("username", authentication.getName());

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        response.put("yourRoles", roles);

        return ResponseEntity.ok(response);
    }
}

