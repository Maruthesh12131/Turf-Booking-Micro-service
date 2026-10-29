package com.turfbooking.booking_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.turfbooking.booking_service.exception.OwnerServiceUnavailableException;
import com.turfbooking.booking_service.exception.OwnerAccessDeniedException;

class OwnerClientTest {

    HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private OwnerClient clientReturning(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/owners/me", ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        return new OwnerClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void ownerIdIsReadFromOwnerServiceForTheCallersToken() throws Exception {
        OwnerClient client = clientReturning(200, "{\"ownerId\":7,\"verificationStatus\":\"REJECTED\"}");
        assertThat(client.resolveOwnerId("tok")).isEqualTo(7L);
    }

    @Test
    void profileWithoutAnOwnerIdIsAccessDenied() throws Exception {
        OwnerClient client = clientReturning(200, "{}");
        assertThatThrownBy(() -> client.resolveOwnerId("tok")).isInstanceOf(OwnerAccessDeniedException.class);
    }

    @Test
    void ownerServiceErrorFailsClosed() throws Exception {
        OwnerClient client = clientReturning(500, "{}");
        assertThatThrownBy(() -> client.resolveOwnerId("tok")).isInstanceOf(OwnerServiceUnavailableException.class);
    }

    @Test
    void unreachableOwnerServiceFailsClosed() {
        OwnerClient client = new OwnerClient("http://127.0.0.1:1");
        assertThatThrownBy(() -> client.resolveOwnerId("tok")).isInstanceOf(OwnerServiceUnavailableException.class);
    }

    @Test
    void noProfileIsAccessDenied() throws Exception {
        OwnerClient client = clientReturning(404, "{}");
        assertThatThrownBy(() -> client.resolveOwnerId("tok")).isInstanceOf(OwnerAccessDeniedException.class);
    }
}
