package com.in.amas.ingwclient.config;

import com.in.amas.ingwclient.model.AppState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {
    private String name;
    private String version;
    private Server server;
    private Client client;

    @Getter
    @Setter
    public static class Server {
        private int port;
        private AppState state;
        private Security security;
    }

    @Getter
    @Setter
    public static class Security {
        private List<String> allowedIps;
        private List<String> allowedMacs;
        private String accessKey;
    }

    @Getter
    @Setter
    public static class Client {
        private String ip;
        private int port;
        private String sender;
    }
}