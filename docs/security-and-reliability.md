# AI Agent 安全与可靠性验收

公开版说明：下表中的测试类和冒烟脚本是历史验证证据，已连同原POM保存在维护者本地恢复包，公开仓库不含测试源码或这些脚本。表内通过状态不表示本次发布重新执行了原测试。最新分项边界见 [正式验收记录](delivery/03_项目验收记录表.pdf)。

## 信任边界

Agent 是“建议与解释层”，不是测试裁判。`PASS / FAIL / ERROR / SKIPPED` 只来自 REST Assured 请求结果和确定性断言引擎；失败分析只能读取已保存、已脱敏的证据。

## 已实现控制

| 风险 | 控制 | 自动化证据 |
| --- | --- | --- |
| 模型返回非 JSON、拒答或截断 | Responses API 严格 JSON Schema；响应再次解析和业务校验 | `OpenAiResponsesClientTest` |
| 模型生成认证头或超大参数 | 拒绝 Authorization、token、cookie、secret 等；限制数量、长度和超时 | `AgentCandidateValidatorTest` |
| 调用任意工具 | 注册表只允许严格 Schema 的 `get_test_run_evidence` 与 `execute_test_run`；执行参数必须与未过期人工审批完全一致 | `AgentToolRegistryTest` |
| 越权读取其他运行 | 工具参数必须仅含 `runId` 且与 URL 中 runId 完全一致 | `AgentToolRegistryTest` |
| 模型伪造失败项 | 每个真实失败必须恰好映射一次；未知/重复 resultId 被拒绝 | `AgentFailureAnalysisServiceTest` |
| Agent 改写判定 | 响应状态从持久化证据补全，Schema 不提供判定字段 | `AgentFailureAnalysisServiceTest` |
| 敏感信息进入模型 | 请求头、嵌套 JSON、Bearer、OpenAI 风格密钥和常见键值对脱敏 | `EvidenceSanitizerTest` |
| 远端超时/认证/限流 | 连接与读取超时；401/403、429 和其他 HTTP 状态转为稳定错误，不返回远端正文 | `HttpOpenAiTransport` + API 实测 |
| 候选用例未经审核执行 | 接收后固定保存为 `source=AGENT, reviewStatus=DRAFT` | `agent-smoke.ps1` |
| 客户端伪造来源或审核状态 | 公共 DTO 不暴露这两个字段；创建/编辑均由服务端设为 `MANUAL/DRAFT`，编辑后重新审核 | `TestCaseRequestSecurityTest` |
| 明文凭据进入用例、快照或响应 | 敏感字段只允许 `{{env:...}}` 引用；API/快照掩码；仅执行瞬间解析 | `TestCaseRequestSecurityTest` |
| 未审核、跨项目或非白名单用例被执行 | 创建任务前统一校验，Worker 保留二次防线 | `TestRunExecutionPolicyTest` |
| Agent 重复创建或工具超时 | 审批幂等键和测试任务幂等键双重约束；工具最大等待 5 秒并记录超时 | `TestRunExecutionPolicyTest`、`AgentControlledExecutionServiceTest` |
| 审计只存在于 HTTP 响应 | 工具输入摘要、状态、耗时、结果/错误、审批和任务关联写入 `agent_tool_audit` | `AgentToolAuditPersistenceTest` |

## 文件可靠性边界

CSV/XLSX 延续确定性数据管道：10 MB、10,000 行、200 列上限；验证扩展名、MIME、UTF-8、XLSX 签名；拒绝公式、错误和合并单元格；清洗只产生派生预览和导出，不覆盖原文件。

## 手工异常场景

1. 清空 `OPENAI_API_KEY`：Agent 端点应返回 `503 AGENT_UNAVAILABLE`。
2. 使用无效密钥：应返回 `502 AI_PROVIDER_ERROR`，消息提示认证失败，响应不得包含密钥或上游正文。
3. 将读取超时临时设为极小值：应返回稳定超时错误，测试运行数据不变化。
4. 上传非 UTF-8 CSV、伪装 XLSX、超限文件：应在解析前拒绝。
5. 传入额外工具参数、未知工具名或其他 runId：注册表应拒绝且不调用证据提供器。
6. 不配置 `LOCAL_APPROVAL_TOKEN`、从非回环地址请求或提交错误令牌：审核与 Agent 执行端点应返回 `403 APPROVAL_DENIED`。

2026-10-29 的历史远端记录为 HTTP 401，不代表当前凭据已重新认证。2026-10-31 用户选择免付费检查，未发送真实模型请求；401/503/超时/非法输出均通过本机协议替身验证，错误未改变原始测试证据，模型禁用时核心回归通过。原Agent冒烟脚本已归档；授权后按正式人工演示手册完成真实模型验证，不能将替身结果算作真实模型成功。

尚有两项代码级边界需如实披露：HttpOpenAiTransport 的响应上限在 byte[] 完整读取后检查，不能当作网络流读取的硬内存上限；失败分析的首个模型请求在 auditService.start 之前，因此首请求认证/网络失败没有对应的工具审计，第二阶段分析失败时已成功的证据工具审计也不等于整次分析成功。受控执行的审计和判定隔离已实现，本轮不改动这些业务实现。
