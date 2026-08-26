package vg.identity.rest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityRestClientTimeoutTest {
    private static final String TEST_API_KEY = UUID.randomUUID().toString();
    private static final String PREFIX = IdentityRestClientProperties.CONFIG_PREFIX;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityRestClientAutoConfig.class))
            .withPropertyValues(PREFIX + ".api-key=" + TEST_API_KEY);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void timeoutsDefaultToFiveSeconds() {
        contextRunner.run(context -> {
            var properties = context.getBean(IdentityRestClientProperties.class);
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void timeoutsBindFromDurationFormat() {
        contextRunner
                .withPropertyValues(
                        PREFIX + ".connect-timeout=1500ms",
                        PREFIX + ".read-timeout=2500ms")
                .run(context -> {
                    var properties = context.getBean(IdentityRestClientProperties.class);
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(1500));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofMillis(2500));
                });
    }

    @Test
    void failsStartup_whenConnectTimeoutIsNotPositive() {
        contextRunner
                .withPropertyValues(PREFIX + ".connect-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Property '" + PREFIX + ".connect-timeout' must be a positive duration");
                });
    }

    @Test
    void failsStartup_whenReadTimeoutIsNotPositive() {
        contextRunner
                .withPropertyValues(PREFIX + ".read-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Property '" + PREFIX + ".read-timeout' must be a positive duration");
                });
    }

    @Test
    void restCallAbortsWhenServerIsSlowerThanReadTimeout() throws IOException {
        // Server that accepts the connection but never responds within the read timeout window.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        contextRunner
                .withPropertyValues(
                        PREFIX + ".base-url=" + baseUrl,
                        PREFIX + ".read-timeout=200ms")
                .run(context -> {
                    var client = context.getBean(IdentityApplicationApiRestClient.class);
                    var start = System.nanoTime();
                    assertThatThrownBy(client::me)
                            .isInstanceOf(ResourceAccessException.class)
                            .hasRootCauseInstanceOf(HttpTimeoutException.class);
                    var elapsed = Duration.ofNanos(System.nanoTime() - start);
                    // Fails fast on the 200ms timeout, well before the server's 5s sleep.
                    assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
                });
    }
}
