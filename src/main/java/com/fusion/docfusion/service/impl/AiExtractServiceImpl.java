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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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

    /** 为 true 时不发 HTTP，直接解析 mock JSON，便于无 AI 时联调全链路 */
    @Value("${ai.mock.enabled:false}")
    private boolean aiMockEnabled;

    /**
     * Mock 返回体（需含 data，可选 confidence）。为空则用内置默认 JSON。
     * 注意：只有与 field_schema.code 一致的键才会写入 extracted_value。
     */
    @Value("${ai.mock.response-json:}")
    private String aiMockResponseJson;

    @Value("${ai.client.max-attempts:3}")
    private int maxAttempts;

    @Value("${ai.client.retry-interval-ms:800}")
    private long retryIntervalMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_MOCK_JSON = """
            {"data":{"demo_title":"Mock演示标题","demo_amount":"999.99"},"confidence":{"demo_title":0.99,"demo_amount":0.95}}
            """;

    @Override
    public Map<String, ExtractFieldResult> analyze(File file, String instruction) {
        if (file == null || !file.exists()) {
            throw new BusinessException("待分析的文件不存在");
        }

        if (aiMockEnabled) {
            String json = (aiMockResponseJson != null && !aiMockResponseJson.isBlank())
                    ? aiMockResponseJson.trim()
                    : DEFAULT_MOCK_JSON.trim();
            log.warn("AI Mock 已启用，跳过真实 HTTP, file={}, instructionLen={}",
                    file.getName(), instruction == null ? 0 : instruction.length());
            try {
                return parseResult(json);
            } catch (Exception e) {
                log.error("解析 Mock AI JSON 失败", e);
                throw new BusinessException("Mock AI 返回 JSON 解析失败：" + e.getMessage());
            }
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("instruction", instruction);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        String url = aiBaseUrl + "/analyze";
        log.info("调用 AI 抽取服务, url={}, file={}, instructionLen={}",
                url, file.getName(), instruction == null ? 0 : instruction.length());

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new BusinessException("AI 抽取服务调用失败，HTTP 状态码：" + response.getStatusCode());
                }
                return parseResult(response.getBody());
            } catch (BusinessException e) {
                throw e;
            } catch (ResourceAccessException e) {
                lastFailure = e;
                log.warn("AI 调用网络/超时, attempt={}/{}, msg={}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleepRetry();
                }
            } catch (HttpServerErrorException e) {
                lastFailure = e;
                if (e.getStatusCode().is5xxServerError() && attempt < maxAttempts) {
                    log.warn("AI 返回 5xx, attempt={}/{}, status={}", attempt, maxAttempts, e.getStatusCode());
                    sleepRetry();
                } else {
                    throw new BusinessException(formatHttpClientError("AI 抽取服务", e));
                }
            } catch (HttpClientErrorException e) {
                throw new BusinessException(formatHttpClientError("AI 抽取服务", e));
            } catch (Exception e) {
                log.error("调用 AI 抽取服务异常", e);
                throw new BusinessException("调用 AI 抽取服务异常：" + humanizeException(e));
            }
        }

        String detail = lastFailure == null ? "未知原因" : humanizeException(lastFailure);
        throw new BusinessException("AI 抽取服务在 " + maxAttempts + " 次重试后仍失败：" + detail);
    }

    private void sleepRetry() {
        try {
            Thread.sleep(retryIntervalMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException("AI 调用重试被中断");
        }
    }

    private static String humanizeException(Throwable e) {
        if (e == null) {
            return "";
        }
        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("Read timed out") || msg.contains("timed out")) {
                return "读取超时（read-timeout），请检查 AI 服务是否过慢或调大 ai.client.read-timeout-ms";
            }
            if (msg.contains("Connection refused") || msg.contains("Connect timed out")) {
                return "连接失败或连接超时，请确认 ai.base-url 可达、AI 服务已启动";
            }
        }
        return e.getClass().getSimpleName() + (msg != null ? "：" + msg : "");
    }

    private static String formatHttpClientError(String service, HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        String snippet = body == null ? "" : (body.length() > 200 ? body.substring(0, 200) + "..." : body);
        return service + "返回 " + e.getStatusCode() + (snippet.isBlank() ? "" : "，响应片段：" + snippet);
    }

    private static String formatHttpClientError(String service, HttpServerErrorException e) {
        String body = e.getResponseBodyAsString();
        String snippet = body == null ? "" : (body.length() > 200 ? body.substring(0, 200) + "..." : body);
        return service + "返回 " + e.getStatusCode() + (snippet.isBlank() ? "" : "，响应片段：" + snippet);
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
