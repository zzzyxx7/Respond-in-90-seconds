package com.fusion.docfusion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    /**
     * HS256 密钥
     */
    private String secret = "doc-fusion-demo-secret";

    /**
     * 访问 token 过期时间（秒）
     */
    private long expirationSeconds = 3600;
}

