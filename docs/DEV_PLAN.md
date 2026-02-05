中文 | English (TBD)

# 开发计划（DataSentry 数据清理/内容安全改造）

本计划以“先固化模型与约束、再实现数据面、最后补齐运维与企业级能力”为主线，覆盖实时文本与数据库批处理两条主链路。默认备份策略为 **MetaDB**，预算控制采用 **批处理暂停 / 在线智能降级拦截** 的差异化默认策略。

## Scope
- In: 清理领域模型、MetaDB 表设计与迁移、策略版本化、级联检测（L1/L2/L3）、写回/软硬删/回滚、Pre-flight、Graceful Shutdown、DLQ、Shadow Mode、预算控制、通知、人审、API 与 UI。
- 备注：结构化字段（JSON/JSONPath）支持推迟到 P2。
- Out: 默认引入外部中间件（Kafka/Redis）、非“实时文本/数据库”输入源、完整复合主键引擎（仅先支持虚拟主键或 unique_index_column）。

## Milestones
### P0 (Core & API)
- 目标：在线文本检测跑通，策略版本化生效，核心 Pipeline 可用。
### P1 (Batch & Safety)
- 目标：数据库批处理可用，写回/回滚/预检查/安全兜底完善。
### P2 (Enterprise & Cost)
- 目标：预算防护、影子模式、DLQ、计价同步与企业级运维能力到位。

## Action items
### P0 (Core & API)
[x] **冻结架构与需求**：完善 `docs/DATA_CLEANING_ARCHITECTURE.md`，对外发布执行版架构。  
[x] **MetaDB 设计与迁移**：落地 `datasentry_cleaning_*` 表，含 `policy_snapshot_json`、`metrics_json`、`execution_time_ms`、`detector_source`、`dlq`。  
[x] **策略版本化**：JobRun 绑定 policy snapshot，运行中只读不可变。  
[x] **Pipeline Runtime**：实现 Ingest/Normalize/Detect/Decide/Act/Audit，内置 L1/L2/L3 级联检测与 allowlist 优先级。  
[x] **API & 权限**：新增实时清理 API（check/sanitize），权限分权与审计最小闭环。  
[x] **本地调试工具**：提供轻量 CLI/Local Main，快速验证正则与基础规则。  
[x] **全局特性开关**：新增 `datasentry.cleaning.enabled`，Controller/Pipeline 入口第一行校验，支持紧急关闭。  
[x] **CI 回归保护**：把清理模块基础单测接入 CI，保证不破坏原有 NL2SQL 编译与测试。  

### P1 (Batch & Safety)
[x] **DB 批处理运行时**：连接/分页/分片，租约领取与断点续跑。  
[x] **动作执行（UPDATE/软删）**：WRITEBACK 可用，软删支持；硬删/人审后写回暂缓。  
[x] **Pre-flight & Graceful Shutdown**：连接/字段/权限/MetaDB 配额校验；批次完成后退出。  
[x] **备份（MetaDB）**：默认 MetaDB 备份，密文落库。  
[x] **回滚**：基于备份记录恢复业务数据。  
[x] **密钥管理与加密**：Backup Record AES-GCM 加密存储，Master Key 通过环境变量/配置注入。  
[x] **人审链路**：Review 任务流、审核决策、写回审批闭环。  
[x] **策略配置 UI**：可视化配置规则/Prompt/白名单/动作策略。  
[x] **人审工作台 UI**：审核任务列表、详情比对、通过/拒绝与回滚入口。  

### P2 (Enterprise & Cost)
[ ] **预算控制体系**：事前预估 / 事中监控 / 事后审计；可配置软硬限制。  
[ ] **超限默认策略**：Batch=PAUSE；Online=SMART FAIL-CLOSED（停止 L3，L1/L2 兜底，SUSPICIOUS 拦截）。  
[ ] **计价来源与同步**：采用 “DB + 本地缓存 + 在线同步” 轻量方案。  
[ ] **L2 模型集成**：P1 先实现 Dummy L2（透传），并创建 Spike 任务调研 DJL + ONNX Runtime。  
[ ] **Shadow Mode & DLQ**：影子流量验证与毒药数据隔离。  
[ ] **结构化字段**：支持 JSONPath 解析与局部脱敏（不破坏 JSON 结构）。  
[ ] **通知与观测**：告警通道、指标面板、成本与性能可视化。  
[ ] **测试与验证**：单测、集成测试、回滚测试、预算熔断测试；CI 通过后进行性能与延迟基准测试。

## Defaults / Decisions
- **Batch 默认预算**：Soft Limit 10 RMB，Hard Limit 50 RMB。  
- **Online 默认限制**：按 QPS + 单次请求 Token 上限（建议 4k）控制，不设总额上限。  
- **L2 阈值**：默认 0.6 ~ 0.7（偏 Recall）。  
- **模板库**：Phase 1 内置通用 PII 模板（手机号/身份证/邮箱/银行卡/IPv4/IPv6），Phase 2 增强内容风控模板。

## Level 2 Breakdown (Execution Details)

### P0 核心 Pipeline
**Context 设计**
- `CleaningContext` 必须包含：`originalText`、`normalizedText`、`findings[]`、`verdict`、`traceId`、`policySnapshot`、`metadata`。
- 线程安全建议：Pipeline 节点为无状态单例，Context 为请求级对象传递。

**节点接口定义**
- `PipelineNode#process(CleaningContext ctx) -> NodeResult`
- `NodeResult` 建议包含 `status`（OK/FAILED/SKIPPED）、`errors[]`、`metrics`，避免异常滥用。

**Normalize 规范**
- 全角转半角、大小写规范、去不可见字符（零宽空格）、去 BOM、规范换行。
- 可选：繁简转换作为后续扩展（避免引入重型依赖）。

**L1/L2/L3 流转**
- 采用责任链（Chain of Responsibility），支持 `ShortCircuit`。
- L1 命中可直接出结论；L2 返回 `SUSPICIOUS` 才进入 L3。
- L2 模型必须在应用启动时预加载（Warm-up），避免首批请求冷启动超时。

### P1 数据库批处理
**游标策略**
- `CursorStrategy` 按 ID 类型分支：数字 ID / UUID / 雪花 ID。
- Keyset 分页：`WHERE id > ? ORDER BY id ASC LIMIT ?`。

**事务边界**
- 默认“批次提交”：提高吞吐。
- 单行失败不回滚整批：记录 `cleaning_record` + 入 DLQ。

**租约领取**
- `UPDATE ... SET owner=?, lease_expire=? WHERE (owner IS NULL OR lease_expire < NOW()) AND status='READY' LIMIT ?`
- 处理 CAS 失败：重试少量次数，失败则放弃本轮领取。

### 写回与保护机制
**类型转换层**
- `TypeConverter` 负责从 JDBC 类型到 Java，再到写回类型的安全转换。
- 发现不兼容类型：记录 error，跳过写回。

**长度保护**
- `TruncationPolicy`: 默认 **拒绝写回并记录**（避免截断破坏脱敏效果）。

**JSON 结构化字段（P2）**
- 结构化字段解析与 JSONPath 局部脱敏推迟到 P2，本阶段不实现。

### 预算与计价
**TokenCounter**
- 本地 `AtomicLong` 计数，周期性刷 DB（例如每 10s 或每 1000 tokens）。
- 运行中触线即按策略执行（Batch=PAUSE，Online=SMART FAIL-CLOSED）。

**Token 估算**
- 引入 JTokkit，统一使用 `CL100K_BASE` 编码器进行估算。
- 后续支持模型级 Encoding 映射（GPT/Qwen/DeepSeek）。

## Tech Spec Checklist
- 清理对象与接口签名确定（Context/Node/Result/Policy Snapshot）。
- 关键依赖选型（L2 模型运行时、Token 计数器）。
- 数据库分页与租约 SQL 规范化。
- 写回安全策略（类型/长度）。
- 预算与熔断策略的明确默认值。
- L2 预加载（Warm-up）与内存占用评估。
