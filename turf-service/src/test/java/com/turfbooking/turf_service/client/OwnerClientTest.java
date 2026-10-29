package com.turfbooking.turf_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.turfbooking.turf_service.exception.OwnerNotVerifiedException;
import com.turfbooking.turf_service.exception.OwnerServiceUnavailableException;
import com.turfbooking.turf_service.exception.TurfAccessDeniedException;

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
    void verifiedOwnerIsAccepted() throws Exception {
        OwnerClient client = clientReturning(200, "{\"ownerId\":7,\"verificationStatus\":\"VERIFIED\"}");
        assertThat(client.resolveVerifiedOwnerId("tok")).isEqualTo(7L);
    }

    @Test
    void pendingAndRejectedOwnersAreNotVerified() throws Exception {
        for (String status : new String[] { "PENDING", "REJECTED" }) {
            OwnerClient client = clientReturning(200, "{\"ownerId\":7,\"verificationStatus\":\"" + status + "\"}");
            assertThatThrownBy(() -> client.resolveVerifiedOwnerId("tok")).isInstanceOf(OwnerNotVerifiedException.class);
            server.stop(0);
        }
    }

    @Test
    void missingStatusIsTreatedAsNotVerified() throws Exception {
        OwnerClient client = clientReturning(200, "{\"ownerId\":7}");
        assertThatThrownBy(() -> client.resolveVerifiedOwnerId("tok")).isInstanceOf(OwnerNotVerifiedException.class);
    }

    @Test
    void ownerServiceErrorFailsClosed() throws Exception {
        OwnerClient client = clientReturning(500, "{}");
        assertThatThrownBy(() -> client.resolveVerifiedOwnerId("tok")).isInstanceOf(OwnerServiceUnavailableException.class);
    }

    @Test
    void unreachableOwnerServiceFailsClosed() {
        OwnerClient client = new OwnerClient("http://127.0.0.1:1");
        assertThatThrownBy(() -> client.resolveVerifiedOwnerId("tok")).isInstanceOf(OwnerServiceUnavailableException.class);
    }

    @Test
    void noProfileIsAccessDenied() throws Exception {
        OwnerClient client = clientReturning(404, "{}");
        assertThatThrownBy(() -> client.resolveVerifiedOwnerId("tok")).isInstanceOf(TurfAccessDeniedException.class);
    }
}
