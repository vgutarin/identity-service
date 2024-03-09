package vg.template;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class BaseRestControllerTest {
    protected static Clock clock = Clock.fixed(
            Instant.now(), ZoneOffset.UTC
    );

    @Configuration
    static class Cfg {
        @Bean
        public Clock clock() {
            return clock;
        }
    }
}
