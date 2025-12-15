package com.revvo.sap;

import com.revvo.security.JwtClaimsExtractor;
import jakarta.servlet.http.HttpServletRequest;
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
        // Headers (varia por ambiente — aqui é onde você “descobre” via debug)
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

    // -------- Roles --------

    /**
     * Estratégia (ordem):
     * 1) Header “direto” com roles (ex.: X-SAP-ROLES: A,B,C)
     * 2) Header “groups/roles” (se existir no proxy)
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
                        .toList();
            }
        }

        return List.of();
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
                .toList();
    }

    private List<String> asStringList(Object value) {
        if (value == null) return List.of();

        if (value instanceof Collection<?> col) {
            return col.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }

        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return List.of();

        // se vier "A,B,C"
        if (s.contains(",")) return splitCsv(s);

        // se vier "A B C"
        if (s.contains(" ")) {
            return Arrays.stream(s.split("\\s+"))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .distinct()
                    .toList();
        }

        return List.of(s);
    }

    private List<String> normalize(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

}
