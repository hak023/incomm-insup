package com.in.amas.worker;

import com.in.amas.dto.WorkerMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WorkerThread Pool 관리 클래스
 * 비동기 메시지 처리를 위한 다중 워커 스레드 관리
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Component
public class WorkerThreadPool {
    
    @Value("${worker.thread-pool-size:8}")
    private int threadPoolSize;
    
    @Value("${worker.queue-capacity:1000}")
    private int queueCapacity;
    
    @Value("${worker.keep-alive-time:60}")
    private long keepAliveTime;
    
    @Value("${worker.thread-name-prefix:worker-}")
    private String threadNamePrefix;
    
    private List<WorkerQueue> workerQueues;
    private ThreadPoolExecutor executorService;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    /**
     * WorkerThread Pool 초기화
     */
    @PostConstruct
    public void initialize() {
        log.info("WorkerThreadPool 초기화 시작 - 스레드 수: {}, 큐 용량: {}", threadPoolSize, queueCapacity);
        
        // 커스텀 ThreadFactory 생성
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, threadNamePrefix + threadNumber.getAndIncrement());
                thread.setDaemon(false);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        // ThreadPoolExecutor 생성
        executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // WorkerQueue들 초기화
        workerQueues = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threadPoolSize; i++) {
            WorkerQueue workerQueue = new WorkerQueue(i, queueCapacity);
            workerQueues.add(workerQueue);
            
            // 각 큐에 대한 워커 스레드 시작
            executorService.submit(new WorkerTask(workerQueue));
        }
        
        log.info("WorkerThreadPool 초기화 완료 - {} 개 워커 스레드 시작됨", threadPoolSize);
    }
    
    /**
     * 메시지를 워커 큐에 분배
     * Round-Robin 방식으로 부하 분산
     * 
     * @param message 처리할 메시지
     * @return 큐 추가 성공 여부
     */
    public boolean submitMessage(WorkerMessage message) {
        try {
            // Round-Robin으로 워커 큐 선택
            int queueIndex = roundRobinCounter.getAndIncrement() % threadPoolSize;
            WorkerQueue selectedQueue = workerQueues.get(queueIndex);
            
            boolean success = selectedQueue.offer(message);
            
            if (success) {
                log.debug("메시지를 워커 큐 {}에 추가 - 요청 ID: {}, 타입: {}", 
                        queueIndex, message.getRequestId(), message.getMessageType());
            } else {
                log.warn("워커 큐 {} 포화 상태 - 메시지 추가 실패: {}", queueIndex, message.getRequestId());
                
                // 다른 큐에 시도
                for (int i = 0; i < threadPoolSize; i++) {
                    int alternativeIndex = (queueIndex + i + 1) % threadPoolSize;
                    WorkerQueue alternativeQueue = workerQueues.get(alternativeIndex);
                    
                    if (alternativeQueue.offer(message)) {
                        log.info("메시지를 대체 워커 큐 {}에 추가 - 요청 ID: {}", 
                                alternativeIndex, message.getRequestId());
                        return true;
                    }
                }
                
                log.error("모든 워커 큐가 포화 상태 - 메시지 처리 실패: {}", message.getRequestId());
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("워커 큐에 메시지 추가 중 오류: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 특정 요청 ID로 큐에서 메시지 검색
     * 
     * @param requestId 요청 ID
     * @return 검색된 메시지 (없으면 null)
     */
    public WorkerMessage findMessageByRequestId(String requestId) {
        for (WorkerQueue queue : workerQueues) {
            WorkerMessage message = queue.findByRequestId(requestId);
            if (message != null) {
                return message;
            }
        }
        return null;
    }
    
    /**
     * 워커 스레드 풀 상태 정보 조회
     * 
     * @return 상태 정보
     */
    public WorkerPoolStatus getStatus() {
        int totalQueueSize = 0;
        int totalProcessedCount = 0;
        
        for (WorkerQueue queue : workerQueues) {
            totalQueueSize += queue.getQueueSize();
            totalProcessedCount += queue.getProcessedCount();
        }
        
        return WorkerPoolStatus.builder()
                .threadPoolSize(threadPoolSize)
                .activeThreads(executorService.getActiveCount())
                .totalQueueSize(totalQueueSize)
                .totalProcessedCount(totalProcessedCount)
                .isShutdown(executorService.isShutdown())
                .build();
    }
    
    /**
     * WorkerThread Pool 종료
     */
    @PreDestroy
    public void shutdown() {
        log.info("WorkerThreadPool 종료 시작");
        
        // 모든 워커 큐 종료
        for (WorkerQueue queue : workerQueues) {
            queue.shutdown();
        }
        
        // ThreadPoolExecutor 종료
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("WorkerThreadPool 정상 종료 타임아웃 - 강제 종료 시작");
                executorService.shutdownNow();
                
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("WorkerThreadPool 강제 종료 실패");
                }
            }
        } catch (InterruptedException e) {
            log.warn("WorkerThreadPool 종료 중 인터럽트 발생");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("WorkerThreadPool 종료 완료");
    }
    
    /**
     * 워커 스레드 풀 상태 DTO
     */
    public static class WorkerPoolStatus {
        public final int threadPoolSize;
        public final long activeThreads;
        public final int totalQueueSize;
        public final int totalProcessedCount;
        public final boolean isShutdown;
        
        private WorkerPoolStatus(Builder builder) {
            this.threadPoolSize = builder.threadPoolSize;
            this.activeThreads = builder.activeThreads;
            this.totalQueueSize = builder.totalQueueSize;
            this.totalProcessedCount = builder.totalProcessedCount;
            this.isShutdown = builder.isShutdown;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private int threadPoolSize;
            private long activeThreads;
            private int totalQueueSize;
            private int totalProcessedCount;
            private boolean isShutdown;
            
            public Builder threadPoolSize(int threadPoolSize) {
                this.threadPoolSize = threadPoolSize;
                return this;
            }
            
            public Builder activeThreads(long activeThreads) {
                this.activeThreads = activeThreads;
                return this;
            }
            
            public Builder totalQueueSize(int totalQueueSize) {
                this.totalQueueSize = totalQueueSize;
                return this;
            }
            
            public Builder totalProcessedCount(int totalProcessedCount) {
                this.totalProcessedCount = totalProcessedCount;
                return this;
            }
            
            public Builder isShutdown(boolean isShutdown) {
                this.isShutdown = isShutdown;
                return this;
            }
            
            public WorkerPoolStatus build() {
                return new WorkerPoolStatus(this);
            }
        }
    }
}