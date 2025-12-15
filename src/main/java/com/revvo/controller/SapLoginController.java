package com.revvo.controller;

import com.revvo.domain.dto.SapDebugContext;
import com.revvo.sap.SapContextExtractor;
import com.revvo.security.JwtClaimsExtractor;
import jakarta.servlet.http.HttpServletRequest;
import com.revvo.service.PermissionService;
import com.revvo.domain.UserPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/sap")
@RequiredArgsConstructor
public class SapLoginController {

    private final  PermissionService permissionService;
    private final SapContextExtractor sapContextExtractor;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @PostMapping("/login-mock")
    public ResponseEntity<UserPermissions> loginMock(
            @RequestParam String username,
            @RequestHeader(value = "X-SAP-ROLES", required = false) String sapRolesHeader,
            @RequestParam(value = "sapRoles", required = false) String sapRolesQuery
    ) {
        String rawRoles = sapRolesHeader != null ? sapRolesHeader : sapRolesQuery;

        List<String> sapRoles = rawRoles == null || rawRoles.isBlank()
                ? List.of()
                : Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        UserPermissions userPermissions = permissionService.processUserPermissions(username, sapRoles);

        return ResponseEntity.ok(userPermissions);
    }

    @GetMapping("/login-sso")
    public ResponseEntity<UserPermissions> loginSso(
            HttpServletRequest request
    ) {
        String username = sapContextExtractor.extractUsername(request);

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }

        List<String> sapRoles = sapContextExtractor.extractSapRoles(request);

        UserPermissions userPermissions =
                permissionService.processUserPermissions(username, sapRoles);

        return ResponseEntity.ok(userPermissions);
    }

    @GetMapping("/debug-context")
    public ResponseEntity<SapDebugContext> debugContext(HttpServletRequest request) {
        String username = sapContextExtractor.extractUsername(request);
        List<String> sapRoles = sapContextExtractor.extractSapRoles(request);

        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        SapDebugContext payload = SapDebugContext.builder()
                .username(username)
                .sapRoles(sapRoles)
                .receivedHeaders(headers)
                .jwtClaims(jwtClaimsExtractor.extractClaims(request))
                .build();

        return ResponseEntity.ok(payload);
    }

}
