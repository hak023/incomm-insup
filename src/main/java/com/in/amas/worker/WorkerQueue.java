package com.in.amas.worker;

import com.in.amas.dto.WorkerMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 개별 워커 스레드용 메시지 큐
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Getter
public class WorkerQueue {
    
    private final int queueId;
    private final BlockingQueue<WorkerMessage> messageQueue;
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    public WorkerQueue(int queueId, int capacity) {
        this.queueId = queueId;
        this.messageQueue = new LinkedBlockingQueue<>(capacity);
        
        log.debug("WorkerQueue {} 생성 완료 - 용량: {}", queueId, capacity);
    }
    
    /**
     * 메시지 큐에 메시지 추가
     * 
     * @param message 추가할 메시지
     * @return 추가 성공 여부
     */
    public boolean offer(WorkerMessage message) {
        if (shutdown.get()) {
            log.warn("WorkerQueue {} 종료됨 - 메시지 추가 불가: {}", queueId, message.getRequestId());
            return false;
        }
        
        boolean success = messageQueue.offer(message);
        
        if (success) {
            log.debug("WorkerQueue {}에 메시지 추가 - 요청 ID: {}, 현재 큐 크기: {}", 
                    queueId, message.getRequestId(), messageQueue.size());
        } else {
            log.warn("WorkerQueue {} 포화 상태 - 메시지 추가 실패: {}", queueId, message.getRequestId());
        }
        
        return success;
    }
    
    /**
     * 큐에서 메시지 가져오기 (블로킹)
     * 
     * @param timeout 타임아웃 (밀리초)
     * @return 메시지 (타임아웃 시 null)
     * @throws InterruptedException 인터럽트 예외
     */
    public WorkerMessage poll(long timeout) throws InterruptedException {
        WorkerMessage message = messageQueue.poll(timeout, TimeUnit.MILLISECONDS);
        
        if (message != null) {
            log.debug("WorkerQueue {}에서 메시지 가져옴 - 요청 ID: {}, 남은 큐 크기: {}", 
                    queueId, message.getRequestId(), messageQueue.size());
        }
        
        return message;
    }
    
    /**
     * 요청 ID로 메시지 검색 (큐에서 제거하지 않음)
     * 
     * @param requestId 요청 ID
     * @return 검색된 메시지 (없으면 null)
     */
    public WorkerMessage findByRequestId(String requestId) {
        return messageQueue.stream()
                .filter(msg -> requestId.equals(msg.getRequestId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 처리된 메시지 수 증가
     */
    public void incrementProcessedCount() {
        int count = processedCount.incrementAndGet();
        
        if (count % 100 == 0) {
            log.info("WorkerQueue {} 처리 완료 메시지 수: {}", queueId, count);
        }
    }
    
    /**
     * 현재 큐 크기 조회
     * 
     * @return 큐 크기
     */
    public int getQueueSize() {
        return messageQueue.size();
    }
    
    /**
     * 큐가 비어있는지 확인
     * 
     * @return 빈 큐 여부
     */
    public boolean isEmpty() {
        return messageQueue.isEmpty();
    }
    
    /**
     * 큐가 가득 찼는지 확인
     * 
     * @return 포화 상태 여부
     */
    public boolean isFull() {
        return messageQueue.remainingCapacity() == 0;
    }
    
    /**
     * 큐 종료
     */
    public void shutdown() {
        shutdown.set(true);
        log.info("WorkerQueue {} 종료 - 남은 메시지 수: {}, 처리된 메시지 수: {}", 
                queueId, messageQueue.size(), processedCount.get());
        
        // 남은 메시지들을 로그로 출력
        messageQueue.forEach(message -> 
                log.warn("WorkerQueue {} 종료 시 미처리 메시지 - 요청 ID: {}, 타입: {}", 
                        queueId, message.getRequestId(), message.getMessageType()));
        
        messageQueue.clear();
    }
    
    /**
     * 종료 상태 확인
     * 
     * @return 종료 여부
     */
    public boolean isShutdown() {
        return shutdown.get();
    }
}