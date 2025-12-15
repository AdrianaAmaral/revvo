package com.revvo.controller;

import com.revvo.security.SapSsoCache;
import com.revvo.sap.SapContextExtractor;
import com.revvo.security.JwtClaimsExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Endpoints administrativos para gerenciar o SSO e cache.
 *
 * Em produção, proteja estes endpoints com role ADMIN.
 */
@RestController
@RequestMapping("/sap/admin")
@RequiredArgsConstructor
public class SsoAdminController {

    private final SapSsoCache sapSsoCache;
    private final SapContextExtractor sapContextExtractor;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Limpar todo o cache de permissões SSO
     */
    @DeleteMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        sapSsoCache.clear();
        return ResponseEntity.ok(Map.of(
            "message", "Cache SSO limpo com sucesso",
            "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }

    /**
     * Invalidar cache de um usuário específico
     */
    @DeleteMapping("/cache/user/{username}")
    public ResponseEntity<Map<String, String>> invalidateUser(@PathVariable String username) {
        sapSsoCache.invalidate(username);
        return ResponseEntity.ok(Map.of(
            "message", "Cache invalidado para usuário: " + username,
            "username", username
        ));
    }

    /**
     * Health check do SSO
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "ssoEnabled", true,
            "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado"));
        }
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities(),
                "principal", authentication.getPrincipal()
        ));
    }

    /**
     * Endpoint de DEBUG para ver o que o sistema está extraindo do request SAP.
     * Mostra todos os headers, JWT claims e roles detectados.
     */
    @GetMapping("/debug-sso")
    public ResponseEntity<?> debugSso(HttpServletRequest request) {
        // Extrair username
        String username = sapContextExtractor.extractUsername(request);

        // Extrair roles
        List<String> roles = sapContextExtractor.extractSapRoles(request);

        // Extrair claims do JWT
        Map<String, Object> jwtClaims = jwtClaimsExtractor.extractClaims(request);

        // Listar todos os headers relevantes
        Map<String, String> relevantHeaders = new HashMap<>();
        List<String> headerNames = Arrays.asList(
            "Authorization", "X-Authenticated-User", "X-SAP-USER", "X-User",
            "X-Forwarded-User", "X-User-Name", "X-User-Email", "X-SAP-ROLES",
            "X-User-Roles", "X-Groups", "X-SAP-GROUPS", "X-JWT-Assertion",
            "X-Forwarded-Access-Token"
        );

        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (value != null) {
                relevantHeaders.put(headerName, value);
            }
        }

        // Verificar se já está autenticado
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();

        Map<String, Object> authInfo = new HashMap<>();
        if (auth != null && auth.isAuthenticated()) {
            authInfo.put("authenticated", true);
            authInfo.put("name", auth.getName());
            authInfo.put("authorities", auth.getAuthorities());
        } else {
            authInfo.put("authenticated", false);
        }

        return ResponseEntity.ok(Map.of(
            "extractedUsername", username != null ? username : "null",
            "extractedRoles", roles,
            "jwtClaims", jwtClaims.isEmpty() ? "No JWT found" : jwtClaims,
            "relevantHeaders", relevantHeaders,
            "currentAuthentication", authInfo,
            "tip", "Para testar com roles, adicione header: X-SAP-ROLES ou X-User-Roles com valores separados por vírgula"
        ));
    }

}
