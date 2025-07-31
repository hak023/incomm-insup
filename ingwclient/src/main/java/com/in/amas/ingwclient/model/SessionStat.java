package com.in.amas.ingwclient.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
@AllArgsConstructor
public class SessionStat {
    private LocalDateTime statTime;
    private LocalDateTime connectAt;
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger failure = new AtomicInteger();
    private final AtomicInteger[] reasonFailure;

    private static final int FAILURE_REASON_SIZE = FailureReason.values().length;

    public SessionStat() {
        this.reasonFailure = new AtomicInteger[FAILURE_REASON_SIZE];
        for (int i = 0; i < FAILURE_REASON_SIZE; i++) {
            reasonFailure[i] = new AtomicInteger();
        }
    }

    public void incrementSuccess() {
        success.incrementAndGet();
    }

    public void incrementFailure() {
        failure.incrementAndGet();
    }

    public void incrementReasonFailure(int idx) {
        if (idx >= 0 && idx < FAILURE_REASON_SIZE) {
            reasonFailure[idx].incrementAndGet();
        }
    }
}
