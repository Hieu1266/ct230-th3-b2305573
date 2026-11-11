package vn.edu.bookstore.order.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import vn.edu.bookstore.order.TestcontainersConfig;

@SpringBootTest
@Import(TestcontainersConfig.class)
class CatalogClientCircuitBreakerTest {

    @Autowired
    CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    // TODO(TH3-6): Viết test kiểm thử Cầu dao (Circuit Breaker) ngắt kết nối
    @Test
    void testCircuitBreakerConfiguration() {
        CatalogClient client = new CatalogClient(
                RestClient.builder().baseUrl("http://localhost:59999").build(),
                circuitBreakerFactory
        );

        // Gọi dịch vụ không tồn tại -> ném CatalogUnavailableException
        assertThatThrownBy(() -> client.getBook("9780134685991"))
                .isInstanceOf(CatalogUnavailableException.class);
    }
}
