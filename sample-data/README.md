# 可复现的合成演示数据

本目录不含生产数据或个人信息。CSV 为 UTF-8；XLSX 由项目自身 ORIGINAL 导出接口生成，并经过重新上传、解析、规则检测和 ZIP 内容校验。

| 文件 | 用途与预期 |
| --- | --- |
| quality-dirty.csv / quality-dirty.xlsx | 3 行、5 列；按质量规则检测出 6 个问题 |
| quality-valid.csv / quality-valid.xlsx | 2 行、5 列；同样规则下 0 个问题 |
| quality-rules.json | 类型、必填、age 18–65、score 0–100，以及 id 业务键规则 |
| cleaning-plan.json | 首尾空白清理、name 空值填充 Unknown、按 id 保留首次记录 |
| expected-quality-issues.json | 独立标注的问题位置、规则及清洗预期 |

上传 dirty 文件后，将字段类型和范围配置为 quality-rules.json 对应值，再检测。表头是文件第 1 行，第一条数据是第 2 行；6 个问题分布在文件第 3、4 行。

清洗预期：修改 2 个单元格，移除 1 个重复行，输出 2 行。原文件不覆盖。清洗只处理空白、缺失和重复；保留下来的 not-number 与 101 仍有类型/范围问题，不能把该输出宣称为“全部质量问题已修复”。quality-valid 文件是另一个已正确标注的正常样例。

演示 API 契约见 `../demo-api/openapi.json`，固定合成车辆数据由 demo-api 源码提供。不要把本目录文件当成模型真实调用或人工审核已完成的证明。

