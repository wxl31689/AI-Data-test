package com.wxl.testdataagent.testcase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxl.testdataagent.apidefinition.ApiDefinitionEntity;
import com.wxl.testdataagent.apidefinition.ApiDefinitionRepository;
import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.common.ResourceNotFoundException;
import com.wxl.testdataagent.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final TestRequestCredentialProtector credentialProtector;
    private final TestCaseReviewRepository reviewRepository;

    public TestCaseService(
            TestCaseRepository testCaseRepository,
            ProjectRepository projectRepository,
            ApiDefinitionRepository apiDefinitionRepository,
            ObjectMapper objectMapper,
            TestRequestCredentialProtector credentialProtector,
            TestCaseReviewRepository reviewRepository
    ) {
        this.testCaseRepository = testCaseRepository;
        this.projectRepository = projectRepository;
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.objectMapper = objectMapper;
        this.credentialProtector = credentialProtector;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public TestCaseResponse create(CreateTestCaseRequest request) {
        return createInternal(request, CaseSource.MANUAL);
    }

    @Transactional
    public TestCaseResponse createAgentDraft(CreateTestCaseRequest request) {
        return createInternal(request, CaseSource.AGENT);
    }

    private TestCaseResponse createInternal(CreateTestCaseRequest request, CaseSource source) {
        validateProjectAndApi(request.projectId(), request.apiDefinitionId());
        validateAssertions(request.assertions());
        credentialProtector.validateForStorage(request.request());
        TestCaseEntity entity = new TestCaseEntity(
                request.projectId(),
                request.apiDefinitionId(),
                request.name().trim(),
                request.scenarioType(),
                writeJson(request.request()),
                writeJson(request.assertions()),
                request.priority(),
                source,
                ReviewStatus.DRAFT
        );
        return toResponse(testCaseRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> list(
            String projectId,
            String apiDefinitionId,
            ReviewStatus reviewStatus,
            Boolean enabled
    ) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
        return testCaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(entity -> apiDefinitionId == null || apiDefinitionId.isBlank()
                        || entity.getApiDefinitionId().equals(apiDefinitionId))
                .filter(entity -> reviewStatus == null || entity.getReviewStatus() == reviewStatus)
                .filter(entity -> enabled == null || entity.isEnabled() == enabled)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestCaseResponse get(String id) {
        return toResponse(findEntity(id));
    }

    public TestCaseResponse snapshot(TestCaseEntity entity) {
        return toResponse(entity);
    }

    @Transactional
    public TestCaseResponse update(String id, UpdateTestCaseRequest request) {
        TestCaseEntity entity = findEntity(id);
        validateProjectAndApi(entity.getProjectId(), request.apiDefinitionId());
        validateAssertions(request.assertions());
        credentialProtector.validateForStorage(request.request());
        entity.update(
                request.apiDefinitionId(),
                request.name().trim(),
                request.scenarioType(),
                writeJson(request.request()),
                writeJson(request.assertions()),
                request.priority()
        );
        return toResponse(testCaseRepository.save(entity));
    }

    @Transactional
    public TestCaseResponse review(String id) {
        TestCaseEntity entity = findEntity(id);
        credentialProtector.validateForStorage(readRequest(entity));
        TestCaseReviewEntity review = new TestCaseReviewEntity(entity);
        try {
            entity.review(review.getId());
        } catch (IllegalStateException exception) {
            throw new InvalidRequestException(exception.getMessage());
        }
        TestCaseEntity saved = testCaseRepository.save(entity);
        reviewRepository.save(review);
        return toResponse(saved);
    }

    @Transactional
    public TestCaseResponse setEnabled(String id, boolean enabled) {
        TestCaseEntity entity = findEntity(id);
        entity.setEnabled(enabled);
        return toResponse(testCaseRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        TestCaseEntity entity = findEntity(id);
        testCaseRepository.delete(entity);
    }

    public TestCaseEntity findEntity(String id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("测试用例不存在"));
    }

    public TestRequestConfig readRequest(TestCaseEntity entity) {
        try {
            return objectMapper.readValue(entity.getRequestData(), TestRequestConfig.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取已保存的请求配置", exception);
        }
    }

    public List<AssertionConfig> readAssertions(TestCaseEntity entity) {
        try {
            return objectMapper.readValue(entity.getAssertions(), new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取已保存的断言配置", exception);
        }
    }

    private TestCaseResponse toResponse(TestCaseEntity entity) {
        return new TestCaseResponse(
                entity.getId(),
                entity.getProjectId(),
                entity.getApiDefinitionId(),
                entity.getName(),
                entity.getScenarioType(),
                credentialProtector.maskForResponse(readRequest(entity)),
                safeAssertions(entity),
                entity.getPriority(),
                entity.getSource(),
                entity.getReviewStatus() == ReviewStatus.REVIEWED && entity.getReviewRecordId() == null
                        ? ReviewStatus.DRAFT : entity.getReviewStatus(),
                entity.getVersion(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateProjectAndApi(String projectId, String apiDefinitionId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
        ApiDefinitionEntity definition = apiDefinitionRepository.findById(apiDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException("接口定义不存在"));
        if (!definition.getProjectId().equals(projectId)) {
            throw new InvalidRequestException("接口定义不属于当前项目");
        }
        if (!definition.isEnabled()) {
            throw new InvalidRequestException("接口定义已停用");
        }
    }

    private void validateAssertions(List<AssertionConfig> assertions) {
        for (AssertionConfig assertion : assertions) {
            if (assertion.expected() != null) {
                if (assertion.type() == AssertionType.JSON_VALUE_EQUALS
                        && new com.wxl.testdataagent.testengine.EvidenceSanitizer(objectMapper).isSensitive(assertion.path())) {
                    throw new InvalidRequestException("敏感字段不允许保存明文相等断言，请使用存在性或类型断言");
                }
                credentialProtector.validateForStorage(new TestRequestConfig(
                        java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), assertion.expected(), 1000));
            }
            if (assertion.type() == AssertionType.STATUS_CODE
                    && (assertion.expected() == null || !assertion.expected().isInt())) {
                throw new InvalidRequestException("状态码断言的 expected 必须是整数");
            }
            if ((assertion.type() == AssertionType.JSON_PATH_EXISTS
                    || assertion.type() == AssertionType.JSON_VALUE_EQUALS
                    || assertion.type() == AssertionType.JSON_TYPE
                    || assertion.type() == AssertionType.NUMBER_RANGE)
                    && (assertion.path() == null || assertion.path().isBlank())) {
                throw new InvalidRequestException("JSON 断言必须填写 path");
            }
            if (assertion.type() == AssertionType.JSON_VALUE_EQUALS && assertion.expected() == null) {
                throw new InvalidRequestException("JSON 值相等断言必须填写 expected");
            }
            if (assertion.type() == AssertionType.JSON_TYPE && assertion.expectedType() == null) {
                throw new InvalidRequestException("JSON 类型断言必须填写 expectedType");
            }
            if (assertion.type() == AssertionType.NUMBER_RANGE) {
                if (assertion.min() == null && assertion.max() == null) {
                    throw new InvalidRequestException("数值范围断言至少填写 min 或 max");
                }
                if (assertion.min() != null && assertion.max() != null
                        && assertion.min().compareTo(assertion.max()) > 0) {
                    throw new InvalidRequestException("数值范围断言的 min 不能大于 max");
                }
            }
            if (assertion.type() == AssertionType.RESPONSE_TIME
                    && (assertion.maxMillis() == null || assertion.maxMillis() <= 0)) {
                throw new InvalidRequestException("响应耗时断言必须填写大于 0 的 maxMillis");
            }
        }
    }

    private List<AssertionConfig> safeAssertions(TestCaseEntity entity) {
        var sanitizer = new com.wxl.testdataagent.testengine.EvidenceSanitizer(objectMapper);
        return readAssertions(entity).stream().map(assertion -> new AssertionConfig(
                assertion.type(), assertion.path(), assertion.expected() == null ? null :
                    (sanitizer.isSensitive(assertion.path())
                        ? objectMapper.getNodeFactory().textNode("***") : sanitizer.sanitizeJson(assertion.expected())),
                assertion.expectedType(), assertion.min(), assertion.max(), assertion.maxMillis())).toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("用例配置无法序列化", exception);
        }
    }
}

