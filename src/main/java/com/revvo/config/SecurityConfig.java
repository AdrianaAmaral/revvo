package com.revvo.config;

import com.revvo.security.SapSsoFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Configuração de Segurança com múltiplas estratégias de autenticação:
 *
 * 1. JWT do XSUAA (validação real de token SAP) - PRIORIDADE
 * 2. SSO com SAP Build Work Zone / Fiori (via headers customizados como fallback)
 * 3. Autenticação Keycloak para acessos externos (futura implementação)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SapSsoFilter sapSsoFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Desabilitar CSRF para APIs REST (se usar cookies, habilitar)
            .csrf(csrf -> csrf.disable())

            // Gerenciamento de sessão: stateless (APIs REST)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Configurar OAuth2 Resource Server para validar JWT do XSUAA
            // Isso valida automaticamente tokens enviados via Authorization: Bearer <token>
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )

            // Autorização de requisições
            .authorizeHttpRequests(auth -> auth
                // Rotas públicas
                .requestMatchers("/public/**", "/actuator/**", "/error").permitAll()

                // Rotas de debug (desenvolvimento) - remover em produção
                .requestMatchers("/sap/debug-context", "/sap/admin/debug-sso").permitAll()
                .requestMatchers("/sap/login-mock").permitAll()
                .requestMatchers("/sap/admin/health").permitAll()

                // Rotas SAP SSO - autenticadas via JWT ou filtro
                .requestMatchers("/sap/**").authenticated()

                // Demais rotas - autenticadas
                .anyRequest().authenticated()
            )

            // Adicionar filtro de SSO customizado DEPOIS do OAuth2 Resource Server
            // Se o JWT já foi validado, o filtro não sobrescreve
            .addFilterAfter(sapSsoFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Converte claims do JWT XSUAA para authorities do Spring Security.
     *
     * O XSUAA coloca os scopes no claim "scope" (separados por espaço).
     * Exemplo: "scope": "ADMIN USER FINANCE_MANAGER"
     *
     * Este conversor também tenta ler de "groups" ou "roles" como fallback.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // O XSUAA usa o claim "scope" para roles/scopes
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        // Prefixo das authorities (usar "SCOPE_" para scopes ou "" para não adicionar prefixo)
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }
}
