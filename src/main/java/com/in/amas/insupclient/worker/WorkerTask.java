package com.in.amas.insupclient.worker;

import com.in.amas.insupclient.dto.WorkerMessage;
import com.in.amas.insupclient.service.MessageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 워커 스레드에서 실행되는 메시지 처리 작업
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
public class WorkerTask implements Runnable {
    
    private final WorkerQueue workerQueue;
    private MessageProcessingService messageProcessingService;
    
    public WorkerTask(WorkerQueue workerQueue) {
        this.workerQueue = workerQueue;
    }
    
    /**
     * MessageProcessingService 의존성 주입
     * 생성자 주입이 아닌 세터 주입 사용 (SpringContext에서 생성된 후 주입)
     */
    public void setMessageProcessingService(MessageProcessingService messageProcessingService) {
        this.messageProcessingService = messageProcessingService;
    }
    
    @Override
    public void run() {
        log.info("WorkerTask {} started", workerQueue.getQueueId());
        
        while (!workerQueue.isShutdown() && !Thread.currentThread().isInterrupted()) {
            try {
                // 큐에서 메시지 대기 (1초 타임아웃)
                WorkerMessage message = workerQueue.poll(1000);
                
                if (message != null) {
                    processMessage(message);
                }
                
            } catch (InterruptedException e) {
                log.info("WorkerTask {} interrupted - terminating", workerQueue.getQueueId());
                Thread.currentThread().interrupt();
                break;
                
            } catch (Exception e) {
                log.error("WorkerTask {} exception during message processing: {}", 
                        workerQueue.getQueueId(), e.getMessage(), e);
            }
        }
        
        log.info("WorkerTask {} terminated", workerQueue.getQueueId());
    }
    
    /**
     * 메시지 처리
     * 
     * @param message 처리할 메시지
     */
    private void processMessage(WorkerMessage message) {
        try {
            log.info("WorkerQueue {} - message processing started: request ID: {}, type: {}", 
                    workerQueue.getQueueId(), message.getRequestId(), message.getMessageType());
            
            // 처리 시작 시간 설정
            message.setProcessingStartTime(System.currentTimeMillis());
            
            // 메시지 타입에 따른 처리
            switch (message.getMessageType()) {
                case SIPSVC_REQUEST:
                    processSipsvcRequest(message);
                    break;
                    
                case INSUPC_RESPONSE:
                    processInsupcResponse(message);
                    break;
                    
                default:
                    log.warn("WorkerQueue {} - unknown message type: {}", 
                            workerQueue.getQueueId(), message.getMessageType());
            }
            
            // 처리 완료 카운트 증가
            workerQueue.incrementProcessedCount();
            
            log.info("WorkerQueue {} - message processing completed: request ID: {}, processing time: {}ms", 
                    workerQueue.getQueueId(), 
                    message.getRequestId(),
                    System.currentTimeMillis() - message.getProcessingStartTime());
            
        } catch (Exception e) {
            log.error("WorkerQueue {} - message processing failed: request ID: {}, error: {}", 
                    workerQueue.getQueueId(), message.getRequestId(), e.getMessage(), e);
            
            // 재시도 로직
            handleRetry(message, e);
        }
    }
    
    /**
     * sipsvc 요청 메시지 처리
     * 
     * @param message sipsvc 요청 메시지
     */
    private void processSipsvcRequest(WorkerMessage message) {
        log.debug("WorkerQueue {} - sipsvc request processing: {}", 
                workerQueue.getQueueId(), message.getSipsvcMessage().getType());
        
        if (messageProcessingService != null) {
            messageProcessingService.processSipsvcRequest(message);
        } else {
            log.error("MessageProcessingService not injected");
        }
    }
    
    /**
     * INSUPC 응답 메시지 처리
     * 
     * @param message INSUPC 응답 메시지
     */
    private void processInsupcResponse(WorkerMessage message) {
        log.debug("WorkerQueue {} - INSUPC response processing: code {}", 
                workerQueue.getQueueId(), message.getInsupcMessage().getCode());
        
        if (messageProcessingService != null) {
            messageProcessingService.processInsupcResponse(message);
        } else {
            log.error("MessageProcessingService not injected");
        }
    }
    
    /**
     * 메시지 처리 실패 시 재시도 처리
     * 
     * @param message 실패한 메시지
     * @param exception 발생한 예외
     */
    private void handleRetry(WorkerMessage message, Exception exception) {
        int maxRetryCount = 3;
        
        if (message.getRetryCount() < maxRetryCount) {
            message.setRetryCount(message.getRetryCount() + 1);
            
            log.warn("WorkerQueue {} - message retry: request ID: {}, retry count: {}/{}", 
                    workerQueue.getQueueId(), message.getRequestId(), 
                    message.getRetryCount(), maxRetryCount);
            
            // 재시도 지연 시간 (지수 백오프)
            try {
                long delayMs = 1000L * message.getRetryCount();
                Thread.sleep(delayMs);
                
                // 큐에 다시 추가
                if (!workerQueue.offer(message)) {
                    log.error("WorkerQueue {} - retry message queue add failed: {}", 
                            workerQueue.getQueueId(), message.getRequestId());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("WorkerQueue {} - interrupt during retry wait: {}", 
                        workerQueue.getQueueId(), message.getRequestId());
            }
            
        } else {
            log.error("WorkerQueue {} - message retry count exceeded: request ID: {}, final failure", 
                    workerQueue.getQueueId(), message.getRequestId());
            
            // 실패 처리 (Dead Letter Queue 등으로 이동하거나 알림 등)
            if (messageProcessingService != null) {
                messageProcessingService.handleFailedMessage(message, exception);
            }
        }
    }
}
