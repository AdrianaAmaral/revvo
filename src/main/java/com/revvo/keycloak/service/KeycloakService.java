package com.revvo.keycloak.service;

import java.util.List;

public interface KeycloakService {

    /**
     * Aplica as roles Revvo (client roles) para o usuário no Keycloak.
     * Não removerá roles existentes, apenas adiciona.
     */
    void applyRevvoRoles(String username, List<String> revvoRoles);

}
