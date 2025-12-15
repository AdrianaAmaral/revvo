package com.revvo.security;

import com.revvo.sap.SapContextExtractor;
import com.revvo.service.PermissionService;
import com.revvo.domain.UserPermissions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filtro para autenticação SSO com SAP Build Work Zone / Fiori Launchpad.
 *
 * Captura usuário e roles SAP vindos de headers ou JWT token
 * e configura o SecurityContext do Spring automaticamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SapSsoFilter extends OncePerRequestFilter {

    private final SapContextExtractor sapContextExtractor;
    private final PermissionService permissionService;
    private final SapSsoCache sapSsoCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Se já está autenticado (ex: Keycloak), não sobrescrever
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. Extrair usuário do contexto SAP (headers ou JWT)
            String username = sapContextExtractor.extractUsername(request);

            if (username != null && !username.isBlank()) {
                log.debug("SAP SSO detectado para usuário: {}", username);

                // 2. Verificar cache
                UserPermissions cachedPermissions = sapSsoCache.get(username);

                UserPermissions userPermissions;
                if (cachedPermissions != null) {
                    log.debug("Permissões recuperadas do cache para: {}", username);
                    userPermissions = cachedPermissions;
                } else {
                    // 3. Extrair roles SAP
                    List<String> sapRoles = sapContextExtractor.extractSapRoles(request);
                    log.debug("Roles SAP extraídos: {}", sapRoles);

                    // 4. Processar permissões (mapear SAP -> Revvo, aplicar no Keycloak)
                    userPermissions = permissionService.processUserPermissions(username, sapRoles);

                    // 5. Cachear
                    sapSsoCache.put(username, userPermissions);
                }

                // 6. Montar authorities do Spring Security
                List<GrantedAuthority> authorities = userPermissions.getRevvoRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());

                // Adicionar roles SAP também (para audoria/debug)
                userPermissions.getSapRoles().forEach(sapRole ->
                    authorities.add(new SimpleGrantedAuthority("SAP_" + sapRole))
                );

                // 7. Criar UserDetails e Authentication
                UserDetails userDetails = User.withUsername(username)
                        .password("N/A") // não usado em SSO
                        .authorities(authorities)
                        .accountExpired(false)
                        .accountLocked(false)
                        .credentialsExpired(false)
                        .disabled(false)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 8. Injetar no SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.info("Autenticação SSO bem-sucedida para usuário: {} com {} roles Revvo",
                         username, userPermissions.getRevvoRoles().size());
            }
        } catch (Exception e) {
            log.error("Erro ao processar autenticação SSO: {}", e.getMessage(), e);
            // Não bloqueia a requisição, deixa seguir
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Não aplicar filtro em rotas públicas
        String path = request.getServletPath();
        return path.startsWith("/public")
            || path.startsWith("/actuator")
            || path.startsWith("/error");
    }
}
