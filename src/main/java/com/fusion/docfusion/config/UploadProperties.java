package com.fusion.docfusion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String baseDir;
    private String docsDir;
    private String templatesDir;
    private String resultsDir;
}
