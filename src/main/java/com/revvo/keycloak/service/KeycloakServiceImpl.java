package com.revvo.keycloak.service;

import com.revvo.config.KeycloakProperties;
import com.revvo.keycloak.dto.KeycloakClient;
import com.revvo.keycloak.dto.KeycloakRole;
import com.revvo.keycloak.dto.KeycloakUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KeycloakServiceImpl implements KeycloakService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakServiceImpl.class);

    private final KeycloakProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void applyRevvoRoles(String username, List<String> revvoRolesFullNames) {
        if (revvoRolesFullNames == null || revvoRolesFullNames.isEmpty()) {
            log.info("Nenhuma role Revvo para aplicar para o usuário {}", username);
            return;
        }

        log.info("Aplicando roles Revvo no Keycloak. user={}, roles={}", username, revvoRolesFullNames);

        String adminToken = getAdminToken();

        KeycloakUser user = findUserByUsername(adminToken, username);
        if (user == null) {
            log.warn("Usuário {} não encontrado no realm {}. Abortando.", username, properties.getRealm());
            return;
        }

        KeycloakClient client = findClientByClientId(adminToken, properties.getRevvo().getClientId());
        if (client == null) {
            log.warn("Client {} não encontrado no realm {}. Abortando.",
                    properties.getRevvo().getClientId(), properties.getRealm());
            return;
        }

        List<KeycloakRole> allClientRoles = getClientRoles(adminToken, client.getId());

        // revvoRolesFullNames: ["domicilio_certo:estag", "domicilio_certo:dono"]
        String expectedClientId = properties.getRevvo().getClientId();

        List<String> roleNames = revvoRolesFullNames.stream()
                .map(full -> {
                    String[] parts = full.split(":");
                    if (parts.length == 2) {
                        String clientId = parts[0];
                        String roleName = parts[1];
                        if (expectedClientId.equals(clientId)) {
                            return roleName;
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (roleNames.isEmpty()) {
            log.info("Nenhuma role compatível com o client {} para aplicar.", expectedClientId);
            return;
        }

        List<KeycloakRole> rolesToAssign = allClientRoles.stream()
                .filter(r -> roleNames.contains(r.getName()))
                .collect(Collectors.toList());

        if (rolesToAssign.isEmpty()) {
            log.warn("Nenhuma das roles {} foi encontrada no client {}.", roleNames, expectedClientId);
            return;
        }

        assignClientRolesToUser(adminToken, user.getId(), client.getId(), rolesToAssign);

        log.info("Roles aplicadas com sucesso para o usuário {}: {}", username, roleNames);
    }

    // ========= Métodos auxiliares =========

    private String getAdminToken() {
        String tokenUrl = properties.getUrl()
                + "/realms/" + properties.getRealm() + "/protocol/openid-connect/token";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getAdmin().getClientId());
        body.add("client_secret", properties.getAdmin().getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(tokenUrl, entity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Falha ao obter token admin do Keycloak: " + response.getStatusCode());
        }

        return (String) response.getBody().get("access_token");
    }

    private KeycloakUser findUserByUsername(String token, String username) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl() + "/admin/realms/" + properties.getRealm() + "/users")
                .queryParam("username", username)
                .toUriString();

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<KeycloakUser[]> response =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), KeycloakUser[].class);

        KeycloakUser[] users = response.getBody();
        if (users == null || users.length == 0) {
            return null;
        }
        return users[0];
    }

    private KeycloakClient findClientByClientId(String token, String clientId) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl() + "/admin/realms/" + properties.getRealm() + "/clients")
                .queryParam("clientId", clientId)
                .toUriString();

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<KeycloakClient[]> response =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), KeycloakClient[].class);

        KeycloakClient[] clients = response.getBody();
        if (clients == null || clients.length == 0) {
            return null;
        }
        return clients[0];
    }

    private List<KeycloakRole> getClientRoles(String token, String clientUuid) {
        String url = properties.getUrl() + "/admin/realms/" + properties.getRealm()
                + "/clients/" + clientUuid + "/roles";

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<KeycloakRole[]> response =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole[].class);

        KeycloakRole[] roles = response.getBody();
        return roles == null ? List.of() : Arrays.asList(roles);
    }

    private void assignClientRolesToUser(String token, String userId, String clientUuid,
                                         List<KeycloakRole> rolesToAssign) {

        String url = properties.getUrl() + "/admin/realms/" + properties.getRealm()
                + "/users/" + userId
                + "/role-mappings/clients/" + clientUuid;

        HttpHeaders headers = bearerHeaders(token);
        HttpEntity<List<KeycloakRole>> entity = new HttpEntity<>(rolesToAssign, headers);

        restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

}
