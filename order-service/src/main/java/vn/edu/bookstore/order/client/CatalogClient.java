package vn.edu.bookstore.order.client;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Bên tiêu thụ của catalog-service: gọi đồng bộ qua RestClient, bọc bằng cầu dao. */
@Component
public class CatalogClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CatalogClient(RestClient catalogRestClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = catalogRestClient;
        this.circuitBreaker = circuitBreakerFactory.create("catalog");
    }

//    public BookInfo getBook(String isbn) {
        // TODO(TH3-3): bọc lời gọi bằng circuitBreaker.run(supplier, fallback):
        //   - fallback ném lại UnknownBookException nguyên vẹn (lỗi nghiệp vụ)
        //   - mọi lỗi khác → CatalogUnavailableException
    public BookInfo getBook(String isbn) {
    // TH3-3: Cầu dao bao quanh lời gọi; 404 là lỗi nghiệp vụ nên được ném lại nguyên vẹn
    	return circuitBreaker.run(
        	() -> restClient.get()
                	.uri("/api/v1/books/{isbn}", isbn)
 	                .retrieve()
        	        .onStatus(s -> s.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
                 	   throw new UnknownBookException(isbn);
	                })
        	        .body(BookInfo.class),
	        failure -> {
            // Ném lại UnknownBookException nguyên vẹn để giữ nguyên HTTP status 404
        	    if (failure instanceof UnknownBookException unknown) {
                	throw unknown;
	            }
            // Tất cả lỗi khác (timeout, từ chối kết nối, cầu dao MỞ) thành CatalogUnavailableException
        	    throw new CatalogUnavailableException(
                	    "catalog-service không sẵn sàng: " + failure.getClass().getSimpleName(), failure);
        	}
	    );
	}
//  return restClient.get()
  //              .uri("/api/v1/books/{isbn}", isbn)
    //            .retrieve()
      //          .onStatus(s -> s.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
        //            throw new UnknownBookException(isbn);
          //      })
            //    .body(BookInfo.class);
//    }
}
