package com.revvo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    private String url;
    private String realm;
    private Admin admin = new Admin();
    private Revvo revvo = new Revvo();

    @Data
    public static class Admin {
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class Revvo {
        private String clientId; // "domicilio_certo"
    }

}
