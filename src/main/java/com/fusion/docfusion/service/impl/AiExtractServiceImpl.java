package com.fusion.docfusion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusion.docfusion.dto.ExtractFieldResult;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.service.AiExtractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiExtractServiceImpl implements AiExtractService {

    private final RestTemplate restTemplate;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, ExtractFieldResult> analyze(File file, String instruction) {
        if (file == null || !file.exists()) {
            throw new BusinessException("待分析的文件不存在");
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(file));
            body.add("instruction", instruction);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            String url = aiBaseUrl + "/analyze";
            log.info("调用 AI 抽取服务, url={}, file={}, instructionLen={}",
                    url, file.getName(), instruction == null ? 0 : instruction.length());

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException("AI 抽取服务调用失败，HTTP 状态码：" + response.getStatusCode());
            }

            return parseResult(response.getBody());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 抽取服务异常", e);
            throw new BusinessException("调用 AI 抽取服务异常：" + e.getMessage());
        }
    }

    private Map<String, ExtractFieldResult> parseResult(String json) throws Exception {
        Map<String, ExtractFieldResult> result = new HashMap<>();
        JsonNode root = objectMapper.readTree(json);

        // 优先按最新约定解析：顶层 data 字段
        JsonNode dataNode = root.path("data");
        // 兼容旧格式：payload.data
        if (!dataNode.isObject()) {
            dataNode = root.path("payload").path("data");
        }

        // 解析可选的 confidence 字段：顶层 confidence 对象
        JsonNode confidenceNode = root.path("confidence");
        if (!confidenceNode.isObject()) {
            confidenceNode = null;
        }

        if (dataNode.isObject()) {
            Iterator<String> fieldNames = dataNode.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                JsonNode valueNode = dataNode.get(field);
                String value;
                if (valueNode == null || valueNode.isNull()) {
                    value = "";
                } else if (valueNode.isValueNode()) {
                    value = valueNode.asText();
                } else {
                    value = objectMapper.writeValueAsString(valueNode);
                }
                BigDecimal confidence = null;
                if (confidenceNode != null) {
                    JsonNode confNode = confidenceNode.get(field);
                    if (confNode != null && !confNode.isNull()) {
                        if (confNode.isNumber()) {
                            confidence = confNode.decimalValue();
                        } else {
                            try {
                                confidence = new BigDecimal(confNode.asText());
                            } catch (NumberFormatException ignore) {
                                confidence = null;
                            }
                        }
                    }
                }
                ExtractFieldResult fieldResult = new ExtractFieldResult();
                fieldResult.setValue(value);
                fieldResult.setConfidence(confidence);
                result.put(field, fieldResult);
            }
        } else {
            log.warn("AI 返回中未找到 data 或 payload.data 字段（已省略原始返回，len={}）", json == null ? 0 : json.length());
        }
        return result;
    }
}

