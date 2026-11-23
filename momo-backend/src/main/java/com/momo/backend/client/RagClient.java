package com.momo.backend.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

/** 리뷰의 커밋 후 색인 통신을 담당한다. 실패 시 재시도하거나 리뷰 결과를 변경하지 않는다. */
@Component
public class RagClient {
    private static final Logger log = LoggerFactory.getLogger(RagClient.class);
    private final boolean enabled;
    private final RestClient http;

    public RagClient(@Value("${RAG_ENABLED:false}") boolean enabled,
                     @Value("${RAG_BASE_URL:http://127.0.0.1:8000}") String baseUrl,
                     @Value("${RAG_SERVICE_TOKEN:}") String token,
                     @Value("${RAG_CONNECT_TIMEOUT_MS:500}") int connectTimeout,
                     @Value("${RAG_READ_TIMEOUT_MS:1000}") int readTimeout) {
        // 기본 비활성화로 기존 실행 환경에서 추가 서버나 토큰을 요구하지 않는다.
        this.enabled = enabled;
        if (enabled && token.isBlank()) {
            throw new IllegalArgumentException("RAG 활성화에는 RAG_SERVICE_TOKEN 설정이 필요합니다.");
        }
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException("RAG 타임아웃은 양수여야 합니다.");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-Service-Token", token).build();
    }

    /** 개인정보 없이 리뷰 값의 스냅샷을 만들어 커밋 후 전송한다. */
    public void indexAfterCommit(Long id, String movieCode, String title, String content) {
        IndexRequest snapshot = new IndexRequest(id, movieCode, title, content);
        afterCommit(id, () -> index(snapshot));
    }

    public void deleteAfterCommit(Long id) {
        afterCommit(id, () -> delete(id));
    }

    private void afterCommit(Long id, Runnable request) {
        // 단위 테스트 등 트랜잭션이 없는 호출은 커밋을 보증할 수 없어 건너뛴다.
        if (!enabled || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    request.run();
                } catch (Exception failure) {
                    // 응답 본문·리뷰 내용·토큰은 기록하지 않는다. 자동 복구는 제공하지 않는다.
                    log.warn("RAG 색인 반영 실패 reviewId={}, error={}", id, failure.getClass().getSimpleName());
                }
            }
        });
    }

    public int index(IndexRequest request) {
        IndexResponse response = http.post().uri("/index").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(IndexResponse.class);
        if (response == null) throw new IllegalStateException("RAG 색인 응답 누락");
        return response.chunks();
    }

    public int delete(Long id) {
        DeleteResponse response = http.delete().uri("/index/{id}", id)
                .retrieve().body(DeleteResponse.class);
        if (response == null) throw new IllegalStateException("RAG 삭제 응답 누락");
        return response.deleted();
    }

    // Python HTTP 계약에 맞춘 내부 DTO이며 기존 MOMO API 응답에는 사용하지 않는다.
    public record IndexRequest(Long review_id, String movie_code, String title, String content) { }
    public record IndexResponse(Long review_id, int chunks) { }
    public record DeleteResponse(Long review_id, int deleted) { }
}
