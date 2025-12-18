package com.revvo.controller;

import com.revvo.domain.UserPermissions;
import com.revvo.sap.SapContextExtractor;
import com.revvo.service.PermissionService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/sap")
@RequiredArgsConstructor
public class SapController {

    private final SapContextExtractor sapContextExtractor;
    private final PermissionService permissionService;

    /**
     * Endpoint para detectar se a aplicação está rodando dentro do ambiente FIORI.
     * O frontend pode chamar este endpoint para decidir se deve pular a tela de login.
     *
     * Retorna:
     * - isFiori: true se detectar headers SAP
     * - username: nome do usuário se detectado
     */
    @GetMapping("/detect-environment")
    public ResponseEntity<Map<String, Object>> detectEnvironment(HttpServletRequest request) {
        String username = sapContextExtractor.extractUsername(request);
        boolean isFiori = username != null && !username.isBlank();

        Map<String, Object> response = new HashMap<>();
        response.put("isFiori", isFiori);
        response.put("authenticated", isFiori);

        if (isFiori) {
            response.put("username", username);
        }

        log.debug("Detecção de ambiente FIORI: {}", isFiori);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint que retorna o perfil completo do usuário autenticado via SAP SSO.
     * Inclui: username, nome, email, roles SAP, roles Revvo, origem da autenticação.
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request, Authentication authentication) {
        // Extrair informações completas do contexto SAP (dos headers)
        String username = sapContextExtractor.extractUsername(request);
        String name = sapContextExtractor.extractUserName(request);
        String email = sapContextExtractor.extractUserEmail(request);
        List<String> sapRoles = sapContextExtractor.extractSapRoles(request);

        // Se não encontrou nenhum dado do SAP, retorna erro
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Usuário não autenticado",
                "message", "Nenhum header SAP detectado (X-SAP-USER, X-Authenticated-User, etc.)",
                "hint", "Este endpoint precisa ser chamado via FIORI/Launchpad ou com headers SAP simulados"
            ));
        }

        // Processar permissões
        UserPermissions userPermissions = permissionService.processUserPermissions(username, name, email, sapRoles);

        return ResponseEntity.ok(userPermissions);
    }

}
