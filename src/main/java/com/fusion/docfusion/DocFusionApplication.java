package com.fusion.docfusion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocFusionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocFusionApplication.class, args);
    }

}
