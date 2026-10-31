package com.wxl.testdataagent.testengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wxl.testdataagent.apidefinition.ApiDefinitionEntity;
import com.wxl.testdataagent.testcase.TestRequestConfig;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpTestExecutor {

    private final RegisteredTargetPolicy targetPolicy;
    private final EvidenceSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final int maxResponseChars;

    public HttpTestExecutor(
            RegisteredTargetPolicy targetPolicy,
            EvidenceSanitizer sanitizer,
            ObjectMapper objectMapper,
            @Value("${test-engine.max-response-chars}") int maxResponseChars
    ) {
        this.targetPolicy = targetPolicy;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.maxResponseChars = maxResponseChars;
    }

    public HttpExecutionResult execute(ApiDefinitionEntity definition, TestRequestConfig request) {
        String baseUrl = targetPolicy.validate(definition.getBaseUrl(), definition.getPath());
        int timeout = request.timeoutMillis();

        RestAssuredConfig config = RestAssuredConfig.config()
                .redirect(RedirectConfig.redirectConfig().followRedirects(false)).httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", timeout)
                        .setParam("http.socket.timeout", timeout)
        );

        RequestSpecification specification = RestAssured.given()
                .config(config)
                .baseUri(baseUrl)
                .headers(request.headers())
                .pathParams(request.pathParams())
                .queryParams(request.queryParams());

        if (request.body() != null && !request.body().isNull()) {
            specification.contentType(ContentType.JSON).body(request.body().toString());
        }

        long startedAt = System.nanoTime();
        Response response = specification.request(Method.valueOf(definition.getMethod()), definition.getPath());
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String responseBody = response.getBody().asString();

        return new HttpExecutionResult(
                buildRequestSummary(definition, request, baseUrl),
                sanitizer.sanitizeResponse(responseBody, maxResponseChars),
                responseBody,
                response.statusCode(),
                durationMs
        );
    }

    private String buildRequestSummary(
            ApiDefinitionEntity definition,
            TestRequestConfig request,
            String baseUrl
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", definition.getMethod());
        node.put("url", baseUrl + definition.getPath());
        node.set("pathParams", sanitizer.sanitizeJson(objectMapper.valueToTree(request.pathParams())));
        node.set("queryParams", sanitizer.sanitizeJson(objectMapper.valueToTree(request.queryParams())));
        node.set("headers", objectMapper.valueToTree(sanitizer.sanitizeHeaders(request.headers())));
        if (request.body() != null) {
            node.set("body", sanitizer.sanitizeJson(request.body()));
        }
        node.put("timeoutMillis", request.timeoutMillis());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存请求摘要", exception);
        }
    }
}
