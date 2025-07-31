package com.in.amas.ingwclient.service;

import com.in.amas.ingwclient.IngwClientApplication;
import com.in.amas.ingwclient.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ModuleService {

    private AppState currentState = AppState.RUNNING;

    public void setState(String state) {
        try {
            this.currentState = AppState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
    }

    public AppStateDTO getState() {
        return AppStateDTO.builder()
                .state(currentState)
                .startAt(IngwClientApplication.getStartAt())
                .build();
    }

    public List<SessionInfoDTO> getSessionList() {
        return Arrays.asList(
                ServerSessionInfoDTO.builder()
                        .sessionId("session1")
                        .ipAddress("192.168.0.1")
                        .build(),
                ServerSessionInfoDTO.builder()
                        .sessionId("session2")
                        .ipAddress("192.168.0.1")
                        .build()
        );
    }

    @Autowired
    VersionInfoDTO versionInfoDTO;

    public VersionInfoDTO getVersionInfo() {
        return versionInfoDTO;
    }

    public ServiceStatDTO getServiceStats() {
//        return new ServiceStatsDTO(1200, 3, 250.4);
        return null;
    }
}
