package com.turfbooking.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

/** Starts the real gateway and one small fake server per service, and checks where each request ends up. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingTest {

    private static final String[] SERVICES = { "auth", "turf", "owner", "booking" };
    private static final HttpServer[] FAKES = new HttpServer[SERVICES.length];
    private static int deadPort;

    @LocalServerPort
    int port;

    final HttpClient client = HttpClient.newHttpClient();

    static {
        try {
            startFakes();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void startFakes() throws IOException {
        for (int i = 0; i < SERVICES.length; i++) {
            String name = SERVICES[i];
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                byte[] body = (name + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI()
                        + " auth=" + auth + " origin=" + origin).getBytes(StandardCharsets.UTF_8);
                // Same as a service answering 401/403 to a protected endpoint: the gateway must pass it through untouched.
                int status = "Bearer deny".equals(auth) ? 403 : 200;
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            FAKES[i] = server;
        }
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
    }

    @AfterAll
    static void stopFakes() {
        for (HttpServer s : FAKES) {
            s.stop(0);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + FAKES[0].getAddress().getPort());
        registry.add("TURF_SERVICE_URL", () -> "http://127.0.0.1:" + FAKES[1].getAddress().getPort());
        registry.add("OWNER_SERVICE_URL", () -> "http://127.0.0.1:" + FAKES[2].getAddress().getPort());
        // booking is deliberately unreachable
        registry.add("BOOKING_SERVICE_URL", () -> "http://127.0.0.1:" + deadPort);
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:5173");
    }

    private HttpResponse<String> send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, "GET".equals(method) || "OPTIONS".equals(method) ? BodyPublishers.noBody() : BodyPublishers.ofString("{}"));
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return client.send(b.build(), BodyHandlers.ofString());
    }

    @Test
    void authPathsGoToAuthService() throws Exception {
        HttpResponse<String> r = send("POST", "/users/login");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).startsWith("auth POST /users/login");
        assertThat(send("GET", "/users/a@b.com").body()).startsWith("auth GET /users/a@b.com");
    }

    @Test
    void turfAndSportsPathsGoToTurfService() throws Exception {
        assertThat(send("GET", "/turfs?page=0&size=5").body()).startsWith("turf GET /turfs?page=0&size=5");
        assertThat(send("GET", "/turfs/7/timeslots").body()).startsWith("turf GET /turfs/7/timeslots");
        assertThat(send("POST", "/turfs/courts/2/timeslots").body()).startsWith("turf POST /turfs/courts/2/timeslots");
        assertThat(send("GET", "/sports").body()).startsWith("turf GET /sports");
    }

    @Test
    void ownerPathsGoToOwnerService() throws Exception {
        assertThat(send("GET", "/owners/me").body()).startsWith("owner GET /owners/me");
        assertThat(send("PUT", "/owners/5/verification").body()).startsWith("owner PUT /owners/5/verification");
    }

    @Test
    void authorizationHeaderIsForwardedAndServiceStatusPassesThrough() throws Exception {
        HttpResponse<String> ok = send("GET", "/owners/me", "Authorization", "Bearer abc");
        assertThat(ok.body()).contains("auth=Bearer abc");
        HttpResponse<String> denied = send("GET", "/owners/me", "Authorization", "Bearer deny");
        assertThat(denied.statusCode()).isEqualTo(403);
    }

    @Test
    void pathsThatNoControllerServesAreNotRouted() throws Exception {
        assertThat(send("GET", "/actuator/env").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/internal/anything").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/error").statusCode()).isNotEqualTo(200);
    }

    @Test
    void unreachableServiceGives503Json() throws Exception {
        HttpResponse<String> r = send("GET", "/bookings");
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(r.body()).contains("\"status\":503").contains("currently unavailable");
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        HttpResponse<String> r = send("GET", "/actuator/health");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void corsPreflightIsAnsweredOnceByTheGateway() throws Exception {
        HttpResponse<String> r = send("OPTIONS", "/bookings",
                "Origin", "http://localhost:5173",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "authorization,idempotency-key");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().allValues("Access-Control-Allow-Origin")).containsExactly("http://localhost:5173");

        HttpResponse<String> disallowed = send("OPTIONS", "/bookings",
                "Origin", "http://evil.example", "Access-Control-Request-Method", "POST");
        assertThat(disallowed.statusCode()).isEqualTo(403);
    }

    @Test
    void actualRequestGetsOneCorsHeaderAndOriginIsNotForwarded() throws Exception {
        HttpResponse<String> r = send("GET", "/users/a@b.com", "Origin", "http://localhost:5173");
        assertThat(r.headers().allValues("Access-Control-Allow-Origin")).containsExactly("http://localhost:5173");
        assertThat(r.body()).contains("origin=null");
    }
}
