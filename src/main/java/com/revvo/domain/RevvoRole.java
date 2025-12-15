package com.revvo.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RevvoRole {

    // Ex.: "domicilio_certo:estag"
    private String fullName;

    public String getClient() {
        if (fullName.contains(":")) {
            return fullName.split(":")[0];
        }
        return null;
    }

    public String getRoleName() {
        if (fullName.contains(":")) {
            return fullName.split(":")[1];
        }
        return fullName;
    }

}
