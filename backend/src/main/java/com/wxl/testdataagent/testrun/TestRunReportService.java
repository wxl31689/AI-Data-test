package com.wxl.testdataagent.testrun;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TestRunReportService {

    private final TestRunService testRunService;

    public TestRunReportService(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    public TestRunReportResponse getReport(String runId) {
        return buildReport(testRunService.get(runId));
    }

    TestRunReportResponse buildReport(TestRunResponse run) {
        List<Long> durations = run.results().stream()
                .map(TestResultResponse::durationMs)
                .filter(value -> value != null)
                .sorted()
                .toList();
        long totalDuration = durations.stream().mapToLong(Long::longValue).sum();
        double averageDuration = durations.isEmpty() ? 0 : (double) totalDuration / durations.size();
        long p95Duration = percentile95(durations);
        long maxDuration = durations.isEmpty() ? 0 : durations.getLast();
        Map<ResultStatus, Long> distribution = new EnumMap<>(ResultStatus.class);
        for (ResultStatus status : ResultStatus.values()) distribution.put(status, 0L);
        for (TestResultResponse result : run.results()) {
            distribution.compute(result.resultStatus(), (key, value) -> value == null ? 1 : value + 1);
        }
        List<TestResultResponse> failures = run.results().stream()
                .filter(result -> result.resultStatus() == ResultStatus.FAIL
                        || result.resultStatus() == ResultStatus.ERROR)
                .toList();
        double passRate = run.totalCount() == 0 ? 0 : run.passCount() * 100.0 / run.totalCount();
        return new TestRunReportResponse(
                run,
                passRate,
                totalDuration,
                averageDuration,
                p95Duration,
                maxDuration,
                Map.copyOf(distribution),
                failures,
                Instant.now()
        );
    }

    public byte[] exportHtml(String runId) {
        TestRunReportResponse report = getReport(runId);
        return buildHtml(report).getBytes(StandardCharsets.UTF_8);
    }

    private long percentile95(List<Long> sortedDurations) {
        if (sortedDurations.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(sortedDurations.size() * 0.95) - 1);
        return sortedDurations.get(index);
    }

    String buildHtml(TestRunReportResponse report) {
        TestRunResponse run = report.run();
        StringBuilder resultRows = new StringBuilder();
        for (TestResultResponse result : run.results()) {
            resultRows.append("<tr><td>")
                    .append(escape(result.caseName()))
                    .append("</td><td><span class=\"status ")
                    .append(result.resultStatus().name().toLowerCase(Locale.ROOT))
                    .append("\">")
                    .append(result.resultStatus())
                    .append("</span></td><td>")
                    .append(result.statusCode() == null ? "--" : result.statusCode())
                    .append("</td><td>")
                    .append(result.durationMs() == null ? "--" : result.durationMs() + " ms")
                    .append("</td><td>")
                    .append(escape(failureMessage(result)))
                    .append("</td></tr>");
        }
        return """
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                body{font-family:"Microsoft YaHei",Arial,sans-serif;margin:0;background:#f5f8f7;color:#17342e}main{max-width:1100px;margin:36px auto;padding:0 24px}header{background:#123e35;color:white;padding:28px 32px;border-radius:16px}h1{margin:6px 0 8px;font-size:28px}.meta{color:#c9ddd7}.metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:20px 0}.metric{background:white;border:1px solid #dfe9e5;border-radius:12px;padding:16px}.metric span{display:block;color:#6f8881;font-size:12px}.metric strong{display:block;margin-top:5px;font-size:24px}section{background:white;border:1px solid #dfe9e5;border-radius:14px;padding:22px;margin-top:18px}table{width:100%%;border-collapse:collapse}th,td{padding:11px 10px;border-bottom:1px solid #e5ece9;text-align:left;font-size:13px}th{color:#5d746e;background:#f2f6f4}.status{font-weight:700}.pass{color:#087864}.fail{color:#c0392b}.error{color:#b66a00}.skipped{color:#75827f}@media print{body{background:white}main{margin:0;max-width:none}.metric,section,header{break-inside:avoid}}
                </style></head><body><main>
                <header><div>TestData Agent 运行报告</div><h1>%s</h1><div class="meta">任务 ID：%s　状态：%s　生成时间：%s</div></header>
                <div class="metrics"><div class="metric"><span>总用例</span><strong>%d</strong></div><div class="metric"><span>通过率</span><strong>%.1f%%</strong></div><div class="metric"><span>PASS</span><strong>%d</strong></div><div class="metric"><span>FAIL / ERROR</span><strong>%d</strong></div><div class="metric"><span>P95 耗时</span><strong>%d ms</strong></div></div>
                <section><h2>执行明细</h2><table><thead><tr><th>用例</th><th>结果</th><th>HTTP</th><th>耗时</th><th>失败证据</th></tr></thead><tbody>%s</tbody></table></section>
                </main></body></html>
                """.formatted(
                escape(run.runName()),
                escape(run.runName()),
                escape(run.id()),
                run.status(),
                report.generatedAt(),
                run.totalCount(),
                report.passRate(),
                run.passCount(),
                run.failCount() + run.errorCount(),
                report.p95DurationMs(),
                resultRows
        );
    }

    private String failureMessage(TestResultResponse result) {
        if (result.errorMessage() != null) return result.errorMessage();
        List<String> failedAssertions = new ArrayList<>();
        for (AssertionResultResponse assertion : result.assertions()) {
            if (!assertion.passed()) failedAssertions.add(assertion.message());
        }
        return String.join("；", failedAssertions);
    }

    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
