package com.in.amas.ingwclient.model;

import com.in.amas.ingwclient.config.ApplicationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class VersionInfoDTO {
    private String name;
    private String version;

    public VersionInfoDTO(ApplicationProperties prop) {
        this.name = prop.getName();
        this.version = prop.getVersion();
    }
}
