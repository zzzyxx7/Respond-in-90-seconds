package com.fusion.docfusion.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 探测 AI HTTP 服务是否可达（GET /docs，短超时，不拖慢整体健康检查）。
 */
@Component("ai")
public class AiServiceHealthIndicator implements HealthIndicator {

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Override
    public Health health() {
        String base = aiBaseUrl == null ? "" : aiBaseUrl.replaceAll("/+$", "");
        String url = base + "/docs";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        RestTemplate rt = new RestTemplate(factory);
        try {
            var resp = rt.getForEntity(url, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                        .withDetail("baseUrl", base)
                        .withDetail("probeUrl", url)
                        .withDetail("httpStatus", resp.getStatusCode().value())
                        .build();
            }
            return Health.down()
                    .withDetail("baseUrl", base)
                    .withDetail("probeUrl", url)
                    .withDetail("httpStatus", resp.getStatusCode().value())
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("baseUrl", base)
                    .withDetail("probeUrl", url)
                    .build();
        }
    }
}
