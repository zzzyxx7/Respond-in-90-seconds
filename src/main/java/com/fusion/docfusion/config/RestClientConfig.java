package com.fusion.docfusion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    /**
     * AI HTTP 调用使用独立超时，避免默认无限等待拖死线程。
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${ai.client.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ai.client.read-timeout-ms:90000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}

