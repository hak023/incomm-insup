package com.in.amas.ingwclient.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class ServerSessionInfoDTO extends SessionInfoDTO {
    private String sessionId;
}
