package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
class HttpOpenAiTransport implements OpenAiTransport {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final int maxResponseBytes;

    HttpOpenAiTransport(
            ObjectMapper objectMapper,
            @Value("${agent.openai.base-url}") String baseUrl,
            @Value("${agent.openai.api-key:}") String apiKey,
            @Value("${agent.openai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${agent.openai.read-timeout-ms}") int readTimeoutMs,
            @Value("${agent.openai.max-response-bytes}") int maxResponseBytes
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxResponseBytes = maxResponseBytes;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (!this.apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey);
        }
        this.restClient = builder.build();
    }

    @Override
    public JsonNode post(ObjectNode request) {
        if (!configured()) {
            throw new AgentUnavailableException("AI Agent 未配置 OPENAI_API_KEY");
        }
        try {
            byte[] response = restClient.post()
                    .uri("/v1/responses")
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            if (response == null || response.length == 0) {
                throw new AiProviderException("模型服务返回空响应");
            }
            if (response.length > maxResponseBytes) {
                throw new AiProviderException("模型响应超过安全大小限制");
            }
            return objectMapper.readTree(response);
        } catch (AgentUnavailableException | AiProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AiProviderException("模型服务拒绝认证，请检查 OPENAI_API_KEY", exception);
            }
            if (status == 429) {
                throw new AiProviderException("模型服务触发限流或额度不足，请稍后重试", exception);
            }
            throw new AiProviderException("模型服务返回 HTTP " + status, exception);
        } catch (RestClientException exception) {
            throw new AiProviderException("模型服务请求失败或超时", exception);
        } catch (Exception exception) {
            throw new AiProviderException("模型响应无法解析", exception);
        }
    }

    @Override
    public boolean configured() {
        return !apiKey.isBlank();
    }
}
