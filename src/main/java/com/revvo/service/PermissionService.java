package com.revvo.service;

import com.revvo.config.SapRevvoMappingProvider;
import com.revvo.domain.UserPermissions;
import com.revvo.keycloak.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SapRevvoMappingProvider mappingProvider;
    private final KeycloakService keycloakService;

    public UserPermissions processUserPermissions(String username, List<String> sapRoles) {
        // 1) resolver roles Revvo via mapeamento
        List<String> revvoRoles = sapRoles.stream()
                .map(mappingProvider::mapSapToRevvo)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .toList();

        // 2) chamar Keycloak para aplicar roles (PRÓXIMO PASSO: implementar)
        keycloakService.applyRevvoRoles(username, revvoRoles);

        // 3) retornar um objeto representando o permissionamento atual
        // (futuramente, ler do Keycloak após introspecção / refresh)
        return UserPermissions.builder()
                .username(username)
                .sapRoles(sapRoles)
                .revvoRoles(revvoRoles)
                .build();
    }

}
