package com.revvo.service;

import com.revvo.domain.UserPermissions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Serviço responsável por processar permissões de usuários SAP.
 *
 * Este serviço:
 * 1. Normaliza roles SAP recebidos
 * 2. Mapeia roles SAP para roles Revvo (implementar conforme REV-339)
 * 3. Monta o objeto UserPermissions completo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    public UserPermissions processUserPermissions(
            String username,
            String name,
            String email,
            List<String> sapRoles
    ) {
        Set<String> revvoRoles = mapSapRolesToRevvo(sapRoles);

        return UserPermissions.builder()
                .username(username)
                .name(name)
                .email(email)
                .sapRoles(sapRoles)
                .revvoRoles(new ArrayList<>(revvoRoles))
                .build();
    }

    private Set<String> mapSapRolesToRevvo(List<String> sapRoles) {
        Set<String> result = new HashSet<>();

        if (sapRoles == null || sapRoles.isEmpty()) {
            result.add("USER");
            return result;
        }

        for (String role : sapRoles) {
            if (role.equalsIgnoreCase("RevvoAdmin")) {
                result.add("ADMIN");
            }
            if (role.equalsIgnoreCase("RevvoUser")) {
                result.add("USER");
            }
            if (role.startsWith("Admin")) {
                result.add("ADMIN");
            }
        }
        if (result.isEmpty()) {
            result.add("USER");
        }

        return result;
    }
}
