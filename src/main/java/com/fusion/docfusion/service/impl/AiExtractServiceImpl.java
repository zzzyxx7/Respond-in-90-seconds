package com.fusion.docfusion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public Map<String, String> analyze(File file, String instruction) {
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

    private Map<String, String> parseResult(String json) throws Exception {
        Map<String, String> result = new HashMap<>();
        JsonNode root = objectMapper.readTree(json);

        // 按队友仓库 README 的示例结构解析：payload.data 里是字段名 -> 值
        JsonNode dataNode = root.path("payload").path("data");
        if (dataNode.isObject()) {
            Iterator<String> fieldNames = dataNode.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                String value = dataNode.get(field).asText();
                result.put(field, value);
            }
        } else {
            log.warn("AI 返回中未找到 payload.data 字段（已省略原始返回，len={}）", json == null ? 0 : json.length());
        }
        return result;
    }
}

