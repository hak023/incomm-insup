package com.in.amas.ingwclient.controller;

import com.in.amas.ingwclient.model.*;
import com.in.amas.ingwclient.service.ModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/module")
public class ModuleController {

    @Autowired
    private ModuleService moduleService;

    // 1. Set the current module state (Running or Blocked)
    @PostMapping("/state")
    public MessageDTO setState(@RequestParam String state) {
        moduleService.setState(state);
        return new MessageDTO("State updated to " + state);
    }

    // 1. Get the current module state
    @GetMapping("/state")
    public AppStateDTO getState() {
        return moduleService.getState();
    }

    // 2. Retrieve the list of active integration sessions
    @GetMapping("/sessions")
    public List<SessionInfoDTO> getSessions() {
        return moduleService.getSessionList();
    }

    // 3. Retrieve module name and version information
    @GetMapping("/version")
    public VersionInfoDTO getVersion() {
        return moduleService.getVersionInfo();
    }

    // 4. Retrieve service statistics
    @GetMapping("/stats")
    public ServiceStatDTO getStatistics() {
        return moduleService.getServiceStats();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageDTO> handleIllegalArgument(IllegalArgumentException ex) {
        MessageDTO error = new MessageDTO(ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}