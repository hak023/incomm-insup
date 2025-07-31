package com.in.amas.ingwclient.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.in.amas.ingwclient.config.ApplicationProperties;
import com.in.amas.ingwclient.model.SessionStat;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Service that stores SessionStat per session
 * with 5-minute intervals, keeping up to 7 days in memory.
 */
@Service
public class SessionStatsService {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final ApplicationProperties prop;

    public SessionStatsService(ApplicationProperties applicationProperties) {
        this.prop = applicationProperties;
    }

    // Maximum number of 5-minute interval stats for 7 days: 7 days * 24 hours * 12 (5-minute intervals)
    private static final int MAX_STATS = 2 * 24 * 12;
    private static final String BACKUP_FILE = "sessionStatsBackup.json";

    // Key class combining sessionId and ipAddress
    private static class SessionKey {
        private final String sessionId;
        private final String ipAddress;

        public SessionKey(String sessionId, String ipAddress) {
            this.sessionId = sessionId;
            this.ipAddress = ipAddress;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SessionKey)) return false;
            SessionKey that = (SessionKey) o;
            return Objects.equals(sessionId, that.sessionId) &&
                    Objects.equals(ipAddress, that.ipAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId, ipAddress);
        }
    }
    private final Map<SessionKey, ConcurrentLinkedDeque<SessionStat>> sessionStats = new ConcurrentHashMap<>();

    private LocalDateTime truncateToFiveMinutes(LocalDateTime time) {
        int minute = time.getMinute();
        int truncatedMinute = (minute / 5) * 5;
        return time.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .withMinute(truncatedMinute).withSecond(0).withNano(0);
    }

    public void recordAttempt(String sessionId, String ipAddress, LocalDateTime connectAt,
                              LocalDateTime attemptTime, boolean isSuccess, int failureReasonIndex) {

        LocalDateTime statTime = truncateToFiveMinutes(attemptTime);
        SessionKey key = new SessionKey(sessionId, ipAddress);

        ConcurrentLinkedDeque<SessionStat> deque = sessionStats.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        SessionStat lastStat = deque.peekLast();

        if (lastStat == null || !lastStat.getStatTime().equals(statTime)) {
            SessionStat newStat = new SessionStat();
            newStat.setStatTime(statTime);
            newStat.setConnectAt(connectAt);
            deque.addLast(newStat);

            lastStat = newStat;
        }

        if (isSuccess) {
            lastStat.incrementSuccess();
        } else {
            lastStat.incrementFailure();
            lastStat.incrementReasonFailure(failureReasonIndex);
        }
    }

    public List<SessionStat> getSessionStats(String sessionId, String ipAddress) {
        SessionKey key = new SessionKey(sessionId, ipAddress);
        Deque<SessionStat> deque = sessionStats.get(key);
        if (deque == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(deque);
    }

    public Map<SessionKey, List<SessionStat>> getAllSessionStats() {
        Map<SessionKey, List<SessionStat>> allStats = new HashMap<>();
        sessionStats.forEach((key, deque) -> {
            allStats.put(key, new ArrayList<>(deque));
        });
        return allStats;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)  // 10분마다 실행
    public void scheduledCleanup() {
        cleanupOldStats();
    }

    public void cleanupOldStats() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        sessionStats.forEach((key, deque) -> {
            while (true) {
                SessionStat first = deque.peekFirst();
                if (first == null) break;
                if (first.getStatTime().isBefore(threshold)) {
                    deque.pollFirst();
                } else {
                    break;
                }
            }
        });
    }

    @PreDestroy
    public void saveSessionStatsToFile() {
        try {
            Map<String, List<SessionStat>> serializableMap = new HashMap<>();

            sessionStats.forEach((key, deque) -> {
                String keyString = key.sessionId + "|" + key.ipAddress;
                serializableMap.put(keyString, new ArrayList<>(deque));
            });

            objectMapper.writeValue(new File(BACKUP_FILE), serializableMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PostConstruct
    public void loadSessionStatsFromFile() {
        File file = new File(BACKUP_FILE);
        if (!file.exists()) {
            return;
        }
        try {
            Map<String, List<SessionStat>> loadedMap = objectMapper.readValue(
                    file, new TypeReference<Map<String, List<SessionStat>>>() {
                    });

            loadedMap.forEach((keyString, list) -> {
                String[] parts = keyString.split("\\|", 2);
                if (parts.length == 2) {
                    SessionKey key = new SessionKey(parts[0], parts[1]);
                    ConcurrentLinkedDeque<SessionStat> deque = new ConcurrentLinkedDeque<>(list);
                    sessionStats.put(key, deque);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
