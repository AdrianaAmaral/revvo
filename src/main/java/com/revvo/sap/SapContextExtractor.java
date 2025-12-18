package com.revvo.sap;

import com.revvo.security.JwtClaimsExtractor;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SapContextExtractor {

    private final JwtClaimsExtractor jwtClaimsExtractor;

    public SapContextExtractor(JwtClaimsExtractor jwtClaimsExtractor) {
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // -------- Username --------

    /**
     * Estratégia (ordem):
     * 1) Headers típicos de proxy/approuter/SAP
     * 2) Claims do JWT (preferred_username, user_name, email, sub)
     */
    public String extractUsername(HttpServletRequest request) {
        // Headers (varia por ambiente — aqui é onde você "descobre" via debug)
        String[] headerCandidates = new String[] {
                "X-SAP-USER",
                "X-Authenticated-User",
                "X-User",
                "X-Forwarded-User",
                "x-sap-user",
                "x-authenticated-user",
                "x-user",
                "x-forwarded-user"
        };

        for (String h : headerCandidates) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank()) return v.trim();
        }

        // JWT fallback
        Map<String, Object> claims = jwtClaimsExtractor.extractClaims(request);
        String[] claimCandidates = new String[] {
                "preferred_username",
                "user_name",
                "email",
                "sub"
        };

        for (String c : claimCandidates) {
            Object v = claims.get(c);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v).trim();
        }

        return null;
    }

    // -------- Nome do usuário --------

    /**
     * Extrai o nome completo do usuário dos headers SAP ou JWT claims
     */
    public String extractUserName(HttpServletRequest request) {
        // Headers
        String[] headerCandidates = new String[] {
                "X-User-Name",
                "X-SAP-USER-NAME",
                "x-user-name",
                "x-sap-user-name"
        };

        for (String h : headerCandidates) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank()) return v.trim();
        }

        // JWT fallback
        Map<String, Object> claims = jwtClaimsExtractor.extractClaims(request);
        String[] claimCandidates = new String[] {
                "name",
                "given_name",
                "family_name"
        };

        for (String c : claimCandidates) {
            Object v = claims.get(c);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v).trim();
        }

        return null;
    }

    // -------- Email do usuário --------

    /**
     * Extrai o email do usuário dos headers SAP ou JWT claims
     */
    public String extractUserEmail(HttpServletRequest request) {
        // Headers
        String[] headerCandidates = new String[] {
                "X-User-Email",
                "X-SAP-USER-EMAIL",
                "x-user-email",
                "x-sap-user-email"
        };

        for (String h : headerCandidates) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank()) return v.trim();
        }

        // JWT fallback
        Map<String, Object> claims = jwtClaimsExtractor.extractClaims(request);
        Object email = claims.get("email");
        if (email != null && !String.valueOf(email).isBlank()) {
            return String.valueOf(email).trim();
        }

        return null;
    }

    // -------- Roles --------

    /**
     * Estratégia (ordem):
     * 1) Header "direto" com roles (ex.: X-SAP-ROLES: A,B,C)
     * 2) Header "groups/roles" (se existir no proxy)
     * 3) JWT claims comuns: groups, roles, authorities, scope
     */
    public List<String> extractSapRoles(HttpServletRequest request) {
        // 1) Header direto
        String roles = firstNonBlankHeader(request,
                "X-SAP-ROLES", "x-sap-roles",
                "X-SAP-GROUPS", "x-sap-groups",
                "X-User-Roles", "x-user-roles",
                "X-Groups", "x-groups"
        );

        if (roles != null) {
            return splitCsv(roles);
        }

        // 2) JWT fallback
        Map<String, Object> claims = jwtClaimsExtractor.extractClaims(request);

        // 2.0) XSUAA role collections (BTP) - vem dentro de xs.system.attributes.xs.rolecollections
        Object xsSystemAttrsObj = claims.get("xs.system.attributes");
        if (xsSystemAttrsObj instanceof Map) {
            Map<?, ?> xsSystemAttrs = (Map<?, ?>) xsSystemAttrsObj;
            Object roleCollectionsObj = xsSystemAttrs.get("xs.rolecollections");
            List<String> roleCollections = asStringList(roleCollectionsObj);
            if (!roleCollections.isEmpty()) {
                return normalize(roleCollections);
            }
        }

        // groups / roles / authorities podem ser List ou String
        List<String> fromGroups = asStringList(claims.get("groups"));
        if (!fromGroups.isEmpty()) return normalize(fromGroups);

        List<String> fromRoles = asStringList(claims.get("roles"));
        if (!fromRoles.isEmpty()) return normalize(fromRoles);

        List<String> fromAuth = asStringList(claims.get("authorities"));
        if (!fromAuth.isEmpty()) return normalize(fromAuth);

        // scope geralmente é "a b c"
        Object scope = claims.get("scope");
        if (scope != null) {
            String s = String.valueOf(scope);
            if (!s.isBlank()) {
                return Arrays.stream(s.split("\\s+"))
                        .map(String::trim)
                        .filter(v -> !v.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    // -------- Helpers --------

    private String firstNonBlankHeader(HttpServletRequest request, String... names) {
        for (String n : names) {
            String v = request.getHeader(n);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private List<String> splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> asStringList(Object value) {
        if (value == null) return Collections.emptyList();

        if (value instanceof Collection<?>) {
            Collection<?> col = (Collection<?>) value;
            return col.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        }

        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return Collections.emptyList();

        // se vier "A,B,C"
        if (s.contains(",")) return splitCsv(s);

        // se vier "A B C"
        if (s.contains(" ")) {
            return Arrays.stream(s.split("\\s+"))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        }

        return Collections.singletonList(s);
    }

    private List<String> normalize(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

}
