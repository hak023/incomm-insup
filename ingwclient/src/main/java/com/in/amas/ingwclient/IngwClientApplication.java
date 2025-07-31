package com.in.amas.ingwclient;

import com.in.amas.ingwclient.config.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class IngwClientApplication {
    @Autowired
    private ApplicationProperties prop;

    private static final long startTimeMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final LocalDateTime startAt = Instant.ofEpochMilli(startTimeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime();

    public static LocalDateTime getStartAt() {
        return startAt;
    }

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(IngwClientApplication.class, args);
        ApplicationProperties prop = context.getBean(ApplicationProperties.class);
        log.info("Started {} {}", prop.getName(), prop.getServer().getState());
    }
}
