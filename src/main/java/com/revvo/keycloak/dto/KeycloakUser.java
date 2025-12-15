package com.revvo.keycloak.dto;

import lombok.Data;

@Data
public class KeycloakUser {

    private String id;
    private String username;
    private String email;

}
