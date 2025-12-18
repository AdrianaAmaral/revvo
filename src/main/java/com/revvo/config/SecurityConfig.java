package com.revvo.config;

import com.revvo.security.SapSsoFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * A autenticação é realizada através de headers enviados pelo SAP (via Approuter/Web Dispatcher)
 * contendo informações do usuário já autenticado no ambiente SAP.
 *
 * O filtro SapSsoFilter captura esses headers, carrega as permissões SAP e configura
 * o contexto de segurança do Spring automaticamente.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SapSsoFilter sapSsoFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()

            // Autorização de requisições
            .authorizeRequests()
                // Rotas públicas
                .antMatchers("/", "/actuator/health", "/error").permitAll()
                .antMatchers("/public/**").permitAll()
                .antMatchers("/sap/detect-environment").permitAll() // Público para detecção de ambiente
                .antMatchers("/sap/profile").permitAll() // Público para testes (remover em produção)

                // Rotas SAP protegidas - autenticadas via SSO
                .antMatchers("/sap/**").authenticated()

                // Demais rotas - autenticadas
                .anyRequest().authenticated()
                .and()

            // Adicionar filtro de SSO SAP para processar headers de autenticação
            .addFilterBefore(sapSsoFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
