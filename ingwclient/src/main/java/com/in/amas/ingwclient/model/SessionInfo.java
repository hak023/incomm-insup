package com.in.amas.ingwclient.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class SessionInfo {
    private final String sessionId;
    private final SessionType sessionType;
    private final String ipAddress;
    private final String macAddress;
    private final LocalDateTime connectedAt;
}

