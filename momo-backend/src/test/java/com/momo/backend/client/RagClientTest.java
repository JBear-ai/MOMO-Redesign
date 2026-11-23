package com.momo.backend.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** 로컬 HTTP와 실제 Spring commit/rollback 콜백을 검증한다. DB 접속은 없다. */
class RagClientTest {
    HttpServer server;
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> header = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger(200);
    RagClient client;
    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/index", exchange -> {
            calls.incrementAndGet();
            header.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = (exchange.getRequestMethod().equals("DELETE")
                    ? "{\"review_id\":91,\"deleted\":1}" : "{\"review_id\":91,\"chunks\":2}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new RagClient(true, "http://127.0.0.1:" + server.getAddress().getPort(), "test-only", 200, 200);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void httpContractAndToken() {
        assertEquals(2, client.index(new RagClient.IndexRequest(91L, "20990001", "제목", "내용")));
        assertEquals("test-only", header.get());
        assertTrue(body.get().contains("\"review_id\":91"));
        assertFalse(body.get().contains("email"));
        assertEquals(1, client.delete(91L));
    }
    @Test void callbackRunsOnlyAfterSuccessfulCommit() {
        transactionTemplate().executeWithoutResult(s -> {
            client.indexAfterCommit(91L, "20990001", "제목", "내용");
            assertEquals(0, calls.get());
        });
        assertEquals(1, calls.get());
        transactionTemplate().executeWithoutResult(s -> { client.deleteAfterCommit(91L); s.setRollbackOnly(); });
        assertEquals(1, calls.get());
    }
    @Test void noTransactionAndDisabledDoNotSend() {
        client.deleteAfterCommit(91L);
        RagClient disabled = new RagClient(false, "http://127.0.0.1:1", "", 200, 200);
        transactionTemplate().executeWithoutResult(s -> disabled.deleteAfterCommit(91L));
        assertEquals(0, calls.get());
    }
    @Test void committedReviewDoesNotFailOnHttpError() {
        status.set(401);
        assertDoesNotThrow(() -> transactionTemplate().executeWithoutResult(s -> client.deleteAfterCommit(91L)));
        assertEquals(1, calls.get());
    }
    @Test void connectionFailureDoesNotEscapeCallback() {
        RagClient unavailable = new RagClient(true, "http://127.0.0.1:1", "test-only", 100, 100);
        assertDoesNotThrow(() -> transactionTemplate().executeWithoutResult(s -> unavailable.deleteAfterCommit(91L)));
    }
    @Test void readTimeoutIsBoundedAndDoesNotEscapeCallback() {
        server.removeContext("/index");
        server.createContext("/index", exchange -> {
            try { Thread.sleep(700); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        long start = System.nanoTime();
        assertDoesNotThrow(() -> transactionTemplate().executeWithoutResult(s -> client.deleteAfterCommit(91L)));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 650);
    }
    private TransactionTemplate transactionTemplate() {
        // 자원 없는 매니저로 DB 없이 실제 동기화 순서를 확인한다.
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object tx, TransactionDefinition definition) { }
            protected void doCommit(DefaultTransactionStatus status) { }
            protected void doRollback(DefaultTransactionStatus status) { }
        });
    }
}
