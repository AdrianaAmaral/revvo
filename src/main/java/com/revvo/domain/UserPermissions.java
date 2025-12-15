package com.revvo.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserPermissions {

    private String username;
    private List<String> sapRoles; // nomes puros
    private List<String> revvoRoles; // "domicilio_certo:estag" etc.

}
