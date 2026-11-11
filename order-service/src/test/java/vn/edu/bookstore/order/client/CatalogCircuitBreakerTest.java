package vn.edu.bookstore.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import vn.edu.bookstore.order.TestcontainersConfig;

/**
 * TH3-6: catalog chậm hơn timeout đọc -> 503 nhanh; sau đủ số lỗi cầu dao MỞ và không gọi catalog nữa.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "bookstore.catalog.read-timeout=300ms",
                "bookstore.catalog.circuit-breaker.sliding-window-size=4",
                "bookstore.catalog.circuit-breaker.minimum-number-of-calls=2",
                "bookstore.catalog.circuit-breaker.failure-rate-threshold=50",
                "bookstore.catalog.circuit-breaker.wait-duration-in-open-state=60s"
        }
)
@Import(TestcontainersConfig.class)
class CatalogCircuitBreakerTest {

    static WireMockServer CATALOG = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        CATALOG.start();
        CATALOG.stubFor(WireMock.get(urlMatching("/api/v1/books/.*"))
                .willReturn(aResponse().withFixedDelay(1500).withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody("{}")));
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("bookstore.catalog.base-url", CATALOG::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        CATALOG.stop();
    }

    @LocalServerPort
    int port;

    @Test
    void timeoutRoiCauDaoMo() {
        // Dùng SimpleClientHttpRequestFactory để không bị Apache HttpClient auto-retry 10 giây khi nhận 503
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(requestFactory)
                .build();

        String body = "{\"customerId\":88,\"lines\":[{\"isbn\":\"9780134685991\",\"quantity\":1}]}";

        for (int i = 0; i < 5; i++) {
            long t0 = System.nanoTime();

            ResponseEntity<String> response = client.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, resp) -> ResponseEntity.status(resp.getStatusCode())
                            .headers(resp.getHeaders())
                            .body(new String(resp.getBody().readAllBytes())));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("10");

            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertThat(ms).as("không chờ hết 1,5 s của catalog chậm").isLessThan(1400);
        }

        // Chỉ 2 lời gọi đầu chạm tới catalog; 3 lời gọi sau bị cầu dao chặn ngay
        assertThat(CATALOG.findAll(getRequestedFor(urlMatching("/api/v1/books/.*")))).hasSize(2);
    }
}
