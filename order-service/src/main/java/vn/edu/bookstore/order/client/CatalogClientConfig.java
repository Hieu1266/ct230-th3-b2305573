package vn.edu.bookstore.order.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CatalogClientConfig {

    private static final Log LOG = LogFactory.getLog(CatalogClientConfig.class);

    /** TH3-2: RestClient có timeout kết nối + timeout đọc; tra Eureka khi load-balanced. */
    @Bean
    RestClient catalogRestClient(RestClient.Builder builder, CatalogClientProperties props,
                                 ObjectProvider<DeferringLoadBalancerInterceptor> loadBalancer) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());

        builder.baseUrl(props.baseUrl()).requestFactory(factory);

        if (props.loadBalanced()) {
            loadBalancer.ifAvailable(builder::requestInterceptor);
        }

        return builder.build();
    }

    /** TH3-3: Cấu hình cầu dao "catalog" (đọc từ bookstore.catalog.circuit-breaker.*). */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> catalogCircuitBreaker(CatalogClientProperties props) {
        CatalogClientProperties.CircuitBreaker cb = props.circuitBreaker();
        return factory -> {
            factory.configure(b -> b
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            .slidingWindowSize(cb.slidingWindowSize())
                            .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                            .failureRateThreshold(cb.failureRateThreshold())
                            .slowCallDurationThreshold(cb.slowCallDurationThreshold())
                            .slowCallRateThreshold(80)
                            .waitDurationInOpenState(cb.waitDurationInOpenState())
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .automaticTransitionFromOpenToHalfOpenEnabled(true)
                            .recordExceptions(Throwable.class)
                            .ignoreExceptions(UnknownBookException.class)
                            .build())
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(props.readTimeout())
                            .build()), "catalog");

            factory.addCircuitBreakerCustomizer(c -> c.getEventPublisher().onStateTransition(e ->
                    LOG.warn("Cầu dao '" + e.getCircuitBreakerName() + "' chuyển trạng thái " + e.getStateTransition())),
                    "catalog");
        };
    }
}
