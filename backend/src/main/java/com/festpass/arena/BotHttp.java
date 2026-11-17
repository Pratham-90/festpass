package com.festpass.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How bots talk to FestPass: real HTTP requests to this same server's public API, like a browser.
 * One shared HttpClient; each bot is a {@link Session} with its own cookies (session and CSRF token).
 */
final class BotHttp implements AutoCloseable {

    record Reply(int status, JsonNode body, long millis) {
        boolean ok() {
            return status >= 200 && status < 300;
        }

        String code() {
            return body.path("code").asText("");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(BotHttp.class);

    private static final int CONNECT_ATTEMPTS = 4;

    private static final Set<String> WRITES = Set.of("POST", "PUT", "DELETE");

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper json;

    /** @param baseUrl e.g. http://127.0.0.1:8080 (IPv4 on purpose: "localhost" may try IPv6 first) */
    BotHttp(String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor());
        if (baseUrl.startsWith("https")) {
            builder.sslContext(trustThisServer());
        }
        this.http = builder.build();
    }

    Session newSession() {
        return new Session();
    }

    /** One bot's browser: it keeps the cookies the server sets and sends the CSRF header on writes. */
    final class Session {
        private final Map<String, String> cookies = new HashMap<>();

        Reply get(String path) {
            return send("GET", path, null, Map.of());
        }

        Reply post(String path, Object body, Map<String, String> headers) {
            return send("POST", path, body, headers);
        }

        Reply delete(String path) {
            return send("DELETE", path, null, Map.of());
        }

        Reply send(String method, String path, Object body, Map<String, String> headers) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api" + path))
                        .timeout(Duration.ofSeconds(60))
                        .header("Accept", "application/json");
                headers.forEach(request::header);
                if (!cookies.isEmpty()) {
                    request.header("Cookie", cookies.entrySet().stream()
                            .map(c -> c.getKey() + "=" + c.getValue()).collect(Collectors.joining("; ")));
                }
                if (WRITES.contains(method) && cookies.containsKey("XSRF-TOKEN")) {
                    request.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
                }
                if (body != null) {
                    request.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
                } else {
                    request.method(method, HttpRequest.BodyPublishers.noBody());
                }
                long start = System.nanoTime();
                HttpResponse<String> response = sendWithConnectRetry(request.build());
                long millis = (System.nanoTime() - start) / 1_000_000;
                rememberCookies(response);
                String text = response.body();
                JsonNode parsed = text == null || text.isBlank() ? NullNode.getInstance() : json.readTree(text);
                return new Reply(response.statusCode(), parsed, millis);
            } catch (IOException e) {
                // No answer at all: report the kind of network failure as the code, e.g. "IO-ConnectException".
                log.debug("Bot {} {} failed: {}", method, path, e.toString());
                return new Reply(-1, json.createObjectNode().put("code", "IO-" + e.getClass().getSimpleName())
                        .put("detail", String.valueOf(e.getMessage())), 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Reply(-1, NullNode.getInstance(), 0);
            }
        }

        /**
         * Retries only when the connection could not be opened, so the request never reached the server and
         * retrying cannot repeat it. With 1,000 bots connecting at the same instant, the operating system may
         * refuse a few connections (its accept backlog is full), just as a phone would retry the connect.
         */
        private HttpResponse<String> sendWithConnectRetry(HttpRequest request)
                throws IOException, InterruptedException {
            for (int attempt = 1; ; attempt++) {
                try {
                    return http.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (ConnectException | HttpConnectTimeoutException e) {
                    if (attempt == CONNECT_ATTEMPTS) {
                        throw e;
                    }
                    Thread.sleep(ThreadLocalRandom.current().nextLong(50, 250) * attempt);
                }
            }
        }

        private void rememberCookies(HttpResponse<?> response) {
            for (String header : response.headers().allValues("set-cookie")) {
                String pair = header.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                if (value.isEmpty()) {
                    cookies.remove(name); // the server cleared it
                } else {
                    cookies.put(name, value);
                }
            }
        }
    }

    /**
     * The https profile uses a self-signed certificate made on this laptop. Bots only ever call this
     * same server, over the loopback address, so they accept that certificate.
     */
    private static SSLContext trustThisServer() {
        try {
            TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAll}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        http.close();
    }
}
