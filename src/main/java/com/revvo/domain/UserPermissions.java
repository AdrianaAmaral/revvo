package com.revvo.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserPermissions {

    private String username;
    private String name;
    private String email;
    private String source; // "sap-sso" ou "local"
    private List<String> sapRoles; // roles originais do SAP
    private List<String> revvoRoles; // roles mapeados para o sistema Revvo

}
