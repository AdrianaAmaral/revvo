package com.revvo.security;

import com.revvo.sap.SapContextExtractor;
import com.revvo.service.PermissionService;
import com.revvo.domain.UserPermissions;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
 * Quando a aplicação é acessada através do Fiori/Launchpad, o SAP envia headers
 * com informações do usuário já autenticado (ex: X-Authenticated-User, X-SAP-ROLES).
 *
 * Este filtro:
 * 1. Extrai o usuário e roles dos headers SAP
 * 2. Mapeia roles SAP para roles Revvo
 * 3. Cacheia as permissões para otimizar performance
 * 4. Configura o SecurityContext do Spring Security automaticamente
 *
 * Resultado: O usuário já vem "logado" sem necessidade de tela de login.
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

        // Se já está autenticado, não sobrescrever
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. Extrair usuário do contexto SAP (headers)
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
                    // 3. Extrair informações completas do usuário dos headers
                    String name = sapContextExtractor.extractUserName(request);
                    String email = sapContextExtractor.extractUserEmail(request);
                    List<String> sapRoles = sapContextExtractor.extractSapRoles(request);

                    log.debug("Informações SAP extraídas - Nome: {}, Email: {}, Roles: {}", name, email, sapRoles);

                    // 4. Processar permissões (mapear SAP → Revvo)
                    userPermissions = permissionService.processUserPermissions(username, name, email, sapRoles);

                    // 5. Cachear
                    sapSsoCache.put(username, userPermissions);
                }

                // 6. Montar authorities do Spring Security
                List<GrantedAuthority> authorities = userPermissions.getRevvoRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());

                // Adicionar roles SAP também (para auditoria/debug)
                userPermissions.getSapRoles().forEach(sapRole ->
                    authorities.add(new SimpleGrantedAuthority("SAP_" + sapRole))
                );

                // 7. Criar UserDetails e Authentication
                UserDetails userDetails = User.withUsername(username)
                        .password("N/A") // não usado em SSO
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 8. Configurar SecurityContext
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.info("Usuário {} autenticado via SAP SSO com {} roles SAP",
                         username, userPermissions.getSapRoles().size());
            }
        } catch (Exception e) {
            log.error("Erro ao processar SSO SAP", e);
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
