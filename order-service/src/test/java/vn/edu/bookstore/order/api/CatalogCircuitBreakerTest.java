package vn.edu.bookstore.order.api;

/**
 * TODO(TH3-6): viết kiểm thử cầu dao:
 *   - WireMock trả chậm 1500 ms cho /api/v1/books/.*, read-timeout = 300ms
 *   - circuit-breaker: sliding-window-size=4, minimum-number-of-calls=2, failure-rate-threshold=50
 *   - gửi 5 POST /api/v1/orders: mọi lần 503 upstream-unavailable, mỗi lần < 1400 ms
 *   - WireMock chỉ nhận đúng 2 yêu cầu (3 lần sau bị cầu dao chặn)
 */
class CatalogCircuitBreakerTest {
}
