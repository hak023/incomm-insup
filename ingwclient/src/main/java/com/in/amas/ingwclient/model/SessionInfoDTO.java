package com.in.amas.ingwclient.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
public abstract class SessionInfoDTO {
    private SessionType sessionType;
    private String ipAddress;
    private String macAddress;
    private LocalDateTime connectedAt;
}