package com.revvo.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SapDebugContext {

    private String username;
    private List<String> sapRoles;

    // pra você descobrir o que chega do Fiori/approuter
    private Map<String, String> receivedHeaders;

    // claims do JWT (se existir)
    private Map<String, Object> jwtClaims;
}
