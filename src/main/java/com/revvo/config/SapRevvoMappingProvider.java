package com.revvo.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class SapRevvoMappingProvider {

    private static final Logger log = LoggerFactory.getLogger(SapRevvoMappingProvider.class);

    @Value("${permission.mapping.sap-revvo-file}")
    private Resource mappingResource;

    private final  ObjectMapper objectMapper;

    @Getter
    private Map<String, String> mapping = Collections.emptyMap();

    @PostConstruct
    public void load() {
        try {
            log.info("Carregando mapeamento SAP->Revvo de {}", mappingResource);
            this.mapping = objectMapper.readValue(
                    mappingResource.getInputStream(),
                    new TypeReference<Map<String, String>>() {}
            );
            log.info("Mapeamento carregado: {}", mapping);
        } catch (IOException e) {
            log.error("Erro ao carregar mapeamento SAP->Revvo", e);
            this.mapping = Collections.emptyMap();
        }
    }

    public String mapSapToRevvo(String sapRole) {
        return mapping.get(sapRole);
    }

}
