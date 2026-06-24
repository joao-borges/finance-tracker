package ca.joaoborges.finance.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Shared {@link RestTemplate} for outbound API calls, backed by Apache
 * HttpClient 5 with sane connect/response timeouts so a slow upstream can't
 * stall a request thread indefinitely.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(15))
                .setResponseTimeout(Timeout.ofSeconds(60))
                .build();
        final CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                // Don't advertise brotli/gzip: avoids a brotli4j native-lib dependency
                // and keeps responses plain for the small payloads we fetch.
                .disableContentCompression()
                .build();
        final HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }

}
