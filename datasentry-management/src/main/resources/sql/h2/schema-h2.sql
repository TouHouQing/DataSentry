-- 简化的数据库初始化脚本，兼容Spring Boot SQL初始化

-- 智能体表
CREATE TABLE IF NOT EXISTS datasentry_agent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL COMMENT '智能体名称',
    description TEXT COMMENT '智能体描述',
    avatar TEXT COMMENT '头像URL',
    status VARCHAR(50) DEFAULT 'draft' COMMENT '状态：draft-待发布，published-已发布，offline-已下线',
    api_key VARCHAR(255) DEFAULT NULL COMMENT '访问 API Key，格式 sk-xxx',
    api_key_enabled TINYINT DEFAULT 0 COMMENT 'API Key 是否启用：0-禁用，1-启用',
    prompt TEXT COMMENT '自定义Prompt配置',
    category VARCHAR(100) COMMENT '分类',
    admin_id BIGINT COMMENT '管理员ID',
    tags TEXT COMMENT '标签，逗号分隔',
    agent_type VARCHAR(50) DEFAULT 'ANALYSIS' COMMENT '智能体类型：ANALYSIS/CLEANING',
    agent_config TEXT COMMENT '智能体配置JSON',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_agent_name (name),
    INDEX idx_agent_status (status),
    INDEX idx_agent_category (category),
    INDEX idx_agent_admin_id (admin_id)
    ) ENGINE = InnoDB COMMENT = '智能体表';

-- 业务知识表
CREATE TABLE IF NOT EXISTS datasentry_business_knowledge (
  id BIGINT NOT NULL AUTO_INCREMENT,
  business_term VARCHAR(255) NOT NULL COMMENT '业务名词',
  description TEXT COMMENT '描述',
  synonyms TEXT COMMENT '同义词，逗号分隔',
  is_recall INT DEFAULT 1 COMMENT '是否召回：0-不召回，1-召回',
  agent_id BIGINT NOT NULL COMMENT '关联的智能体ID',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  embedding_status VARCHAR(20) DEFAULT NULL COMMENT '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败',
  error_msg VARCHAR(255) DEFAULT NULL COMMENT '操作失败的错误信息',
  is_deleted INT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (id),
  INDEX idx_business_knowledge_business_term (business_term),
  INDEX idx_business_knowledge_agent_id (agent_id),
  INDEX idx_business_knowledge_is_recall (is_recall),
  INDEX idx_business_knowledge_embedding_status (embedding_status),
  INDEX idx_business_knowledge_is_deleted (is_deleted),
  FOREIGN KEY (agent_id) REFERENCES datasentry_agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '业务知识表';

-- 语义模型表
CREATE TABLE IF NOT EXISTS datasentry_semantic_model (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_id BIGINT NOT NULL COMMENT '关联的智能体ID',
  datasource_id BIGINT NOT NULL COMMENT '关联的数据源ID',
  table_name VARCHAR(255) NOT NULL COMMENT '关联的表名',
  column_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '数据库中的物理字段名 (例如: csat_score)',
  business_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '业务名/别名 (例如: 客户满意度分数)',
  synonyms TEXT COMMENT '业务名的同义词 (例如: 满意度,客户评分)',
  business_description TEXT COMMENT '业务描述 (用于向LLM解释字段的业务含义)',
  column_comment VARCHAR(255) DEFAULT NULL COMMENT '数据库中的物理字段的原始注释 ',
  data_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT '物理数据类型 (例如: int, varchar(20))',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用 1 启用',
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_semantic_model_agent_id (agent_id),
  INDEX idx_semantic_model_business_name (business_name),
  INDEX idx_semantic_model_status (status),
  CONSTRAINT fk_semantic_model_agent FOREIGN KEY (agent_id) REFERENCES datasentry_agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '语义模型表';


-- 智能体知识表
CREATE TABLE IF NOT EXISTS datasentry_agent_knowledge (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID, 用于内部关联',
  agent_id BIGINT NOT NULL COMMENT '关联的智能体ID',
  title VARCHAR(255) NOT NULL COMMENT '知识的标题 (用户定义, 用于在UI上展示和识别)',
  type VARCHAR(50) NOT NULL COMMENT '知识类型: DOCUMENT-文档, QA-问答, FAQ-常见问题',
  question TEXT COMMENT '问题 (仅当type为QA或FAQ时使用)',
  content MEDIUMTEXT COMMENT '知识内容 (对于QA/FAQ是答案; 对于DOCUMENT, 此字段通常为空)',
  is_recall INT DEFAULT 1 COMMENT '业务状态: 1=召回, 0=非召回',
  embedding_status VARCHAR(20) DEFAULT NULL COMMENT '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败',
  error_msg VARCHAR(255) DEFAULT NULL COMMENT '操作失败的错误信息',
  source_filename VARCHAR(500) DEFAULT NULL COMMENT '上传时的原始文件名',
  file_path VARCHAR(500) DEFAULT NULL COMMENT '文件在服务器上的物理存储路径',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小 (字节)',
  file_type VARCHAR(255) DEFAULT NULL COMMENT '文件类型（pdf,md,markdown,doc等）',
  splitter_type VARCHAR(50) DEFAULT 'token' COMMENT '分块策略类型：token, recursive, sentence, semantic',
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted INT DEFAULT 0 COMMENT '逻辑删除字段，0=未删除, 1=已删除',
  is_resource_cleaned INT DEFAULT 0 COMMENT '0=物理资源（文件和向量）未清理, 1=物理资源已清理',
  PRIMARY KEY (id),
  INDEX idx_agent_knowledge_agent_id_status (agent_id, is_recall),
  INDEX idx_agent_knowledge_embedding_status (embedding_status),
  INDEX idx_agent_knowledge_is_deleted (is_deleted)
) ENGINE = InnoDB COMMENT = '智能体知识表';

-- 数据源表
CREATE TABLE IF NOT EXISTS datasentry_datasource (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL COMMENT '数据源名称',
  type VARCHAR(50) NOT NULL COMMENT '数据源类型：mysql, postgresql',
  host VARCHAR(255) NOT NULL COMMENT '主机地址',
  port INT NOT NULL COMMENT '端口号',
  database_name VARCHAR(255) NOT NULL COMMENT '数据库名称',
  username VARCHAR(255) NOT NULL COMMENT '用户名',
  password VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
  connection_url VARCHAR(1000) COMMENT '完整连接URL',
  status VARCHAR(50) DEFAULT 'inactive' COMMENT '状态：active-启用，inactive-禁用',
  test_status VARCHAR(50) DEFAULT 'unknown' COMMENT '连接测试状态：success-成功，failed-失败，unknown-未知',
  description TEXT COMMENT '描述',
  creator_id BIGINT COMMENT '创建者ID',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_datasource_name (name),
  INDEX idx_datasource_type (type),
  INDEX idx_datasource_status (status),
  INDEX idx_datasource_creator_id (creator_id)
) ENGINE = InnoDB COMMENT = '数据源表';

-- 逻辑外键配置表
CREATE TABLE IF NOT EXISTS datasentry_logical_relation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  datasource_id BIGINT NOT NULL COMMENT '关联的数据源ID',
  source_table_name VARCHAR(100) NOT NULL COMMENT '主表名 (例如 t_order)',
  source_column_name VARCHAR(100) NOT NULL COMMENT '主表字段名 (例如 buyer_uid)',
  target_table_name VARCHAR(100) NOT NULL COMMENT '关联表名 (例如 t_user)',
  target_column_name VARCHAR(100) NOT NULL COMMENT '关联表字段名 (例如 id)',
  relation_type VARCHAR(20) DEFAULT NULL COMMENT '关系类型: 1:1, 1:N, N:1 (辅助LLM理解数据基数，可选)',
  description VARCHAR(500) DEFAULT NULL COMMENT '业务描述: 存入Prompt中帮助LLM理解 (例如: 订单表通过buyer_uid关联用户表id)',
  is_deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_logical_relation_datasource_id (datasource_id),
  INDEX idx_logical_relation_source_table (datasource_id, source_table_name),
  FOREIGN KEY (datasource_id) REFERENCES datasentry_datasource(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '逻辑外键配置表';

-- 智能体数据源关联表
CREATE TABLE IF NOT EXISTS datasentry_agent_datasource (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  datasource_id BIGINT NOT NULL COMMENT '数据源ID',
  is_active TINYINT DEFAULT 0 COMMENT '是否启用：0-禁用，1-启用',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_datasource (agent_id, datasource_id),
  INDEX idx_agent_datasource_agent_id (agent_id),
  INDEX idx_agent_datasource_datasource_id (datasource_id),
  INDEX idx_agent_datasource_is_active (is_active),
  FOREIGN KEY (agent_id) REFERENCES datasentry_agent(id) ON DELETE CASCADE,
  FOREIGN KEY (datasource_id) REFERENCES datasentry_datasource(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '智能体数据源关联表';

-- 智能体预设问题表
CREATE TABLE IF NOT EXISTS datasentry_agent_preset_question (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  question TEXT NOT NULL COMMENT '预设问题内容',
  sort_order INT DEFAULT 0 COMMENT '排序顺序',
  is_active TINYINT DEFAULT 0 COMMENT '是否启用：0-禁用，1-启用',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_agent_preset_question_agent_id (agent_id),
  INDEX idx_agent_preset_question_sort_order (sort_order),
  INDEX idx_agent_preset_question_is_active (is_active),
  FOREIGN KEY (agent_id) REFERENCES datasentry_agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '智能体预设问题表';

-- 会话表
CREATE TABLE IF NOT EXISTS datasentry_chat_session (
  id VARCHAR(36) NOT NULL COMMENT '会话ID（UUID）',
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  title VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
  status VARCHAR(50) DEFAULT 'active' COMMENT '状态：active-活跃，archived-归档，deleted-已删除',
  is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
  user_id BIGINT COMMENT '用户ID',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_chat_session_agent_id (agent_id),
  INDEX idx_chat_session_user_id (user_id),
  INDEX idx_chat_session_status (status),
  INDEX idx_chat_session_is_pinned (is_pinned),
  INDEX idx_chat_session_create_time (create_time),
  FOREIGN KEY (agent_id) REFERENCES datasentry_agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '聊天会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS datasentry_chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  role VARCHAR(20) NOT NULL COMMENT '角色：user-用户，assistant-助手，system-系统',
  content TEXT NOT NULL COMMENT '消息内容',
  message_type VARCHAR(50) DEFAULT 'text' COMMENT '消息类型：text-文本，sql-SQL查询，result-查询结果，error-错误',
  metadata JSON COMMENT '元数据（JSON格式）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_chat_message_session_id (session_id),
  INDEX idx_chat_message_role (role),
  INDEX idx_chat_message_message_type (message_type),
  INDEX idx_chat_message_create_time (create_time),
  FOREIGN KEY (session_id) REFERENCES datasentry_chat_session(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '聊天消息表';

-- 用户Prompt配置表
CREATE TABLE IF NOT EXISTS datasentry_user_prompt_config (
  id VARCHAR(36) NOT NULL COMMENT '配置ID（UUID）',
  name VARCHAR(255) NOT NULL COMMENT '配置名称',
  prompt_type VARCHAR(100) NOT NULL COMMENT 'Prompt类型（如report-generator, planner等）',
  agent_id BIGINT COMMENT '关联的智能体ID，为空表示全局配置',
  system_prompt TEXT NOT NULL COMMENT '用户自定义系统Prompt内容',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用该配置：0-禁用，1-启用',
  description TEXT COMMENT '配置描述',
  priority INT DEFAULT 0 COMMENT '配置优先级，数字越大优先级越高',
  display_order INT DEFAULT 0 COMMENT '配置显示顺序，数字越小越靠前',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  creator VARCHAR(255) COMMENT '创建者',
  PRIMARY KEY (id),
  INDEX idx_user_prompt_config_prompt_type (prompt_type),
  INDEX idx_user_prompt_config_agent_id (agent_id),
  INDEX idx_user_prompt_config_enabled (enabled),
  INDEX idx_user_prompt_config_create_time (create_time),
  INDEX idx_user_prompt_config_type_enabled_priority (prompt_type, agent_id, enabled, priority DESC),
  INDEX idx_user_prompt_config_display_order (display_order ASC)
) ENGINE = InnoDB COMMENT = '用户Prompt配置表';

CREATE TABLE IF NOT EXISTS datasentry_agent_datasource_tables
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_datasource_id BIGINT                                 NOT NULL COMMENT '智能体数据源ID',
    table_name          VARCHAR(255)                        NOT NULL COMMENT '数据表名',
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间',
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '更新时间',
    CONSTRAINT uk_agent_datasource_tables_agent_datasource_id_table_name
        UNIQUE (agent_datasource_id, table_name),
    CONSTRAINT fk_agent_datasource_tables_agent_datasource_id
        FOREIGN KEY (agent_datasource_id) REFERENCES datasentry_agent_datasource (id)
            ON UPDATE CASCADE ON DELETE CASCADE
    ) ENGINE = InnoDB COMMENT = '某个智能体某个数据源所选中的数据表';


-- 模型配置表
CREATE TABLE IF NOT EXISTS `datasentry_model_config` (
  `id` BIGINT(11) NOT NULL AUTO_INCREMENT,
  `provider` varchar(255) NOT NULL COMMENT '厂商标识 (方便前端展示回显，实际调用主要靠 baseUrl)',
  `base_url` varchar(255) NOT NULL COMMENT '关键配置',
  `api_key` varchar(255) NOT NULL COMMENT 'API密钥',
  `model_name` varchar(255) NOT NULL COMMENT '模型名称',
  `temperature` decimal(10,2) DEFAULT '0.00' COMMENT '温度参数',
  `is_active` tinyint(1) DEFAULT '0' COMMENT '是否激活',
  `max_tokens` int(11) DEFAULT '2000' COMMENT '输出响应最大令牌数',
  `model_type` varchar(20) NOT NULL DEFAULT 'CHAT' COMMENT '模型类型 (CHAT/EMBEDDING)',
  `completions_path` varchar(255) DEFAULT NULL COMMENT 'Chat模型专用。附加到 Base URL 的路径。例如OpenAi的/v1/chat/completions',
  `embeddings_path` varchar(255) DEFAULT NULL COMMENT '嵌入模型专用。附加到 Base URL 的路径。',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int(11) DEFAULT '0' COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

-- 清理策略表
CREATE TABLE IF NOT EXISTS datasentry_cleaning_policy (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL COMMENT '策略名称',
  description TEXT COMMENT '策略描述',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  default_action VARCHAR(50) DEFAULT 'DETECT_ONLY' COMMENT '默认动作',
  config_json TEXT COMMENT '策略配置JSON',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_enabled (enabled)
) ENGINE=InnoDB COMMENT='清理策略表';

-- 清理规则表
CREATE TABLE IF NOT EXISTS datasentry_cleaning_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL COMMENT '规则名称',
  rule_type VARCHAR(50) NOT NULL COMMENT '规则类型：REGEX/LLM',
  category VARCHAR(100) NOT NULL COMMENT '类别',
  severity DECIMAL(5,2) DEFAULT 0.80 COMMENT '严重度',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  config_json TEXT COMMENT '规则配置JSON',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_rule_type (rule_type),
  INDEX idx_category (category),
  INDEX idx_enabled (enabled)
) ENGINE=InnoDB COMMENT='清理规则表';

-- 策略规则关联表
CREATE TABLE IF NOT EXISTS datasentry_cleaning_policy_rule (
  policy_id BIGINT NOT NULL,
  rule_id BIGINT NOT NULL,
  priority INT DEFAULT 0 COMMENT '优先级',
  PRIMARY KEY (policy_id, rule_id),
  INDEX idx_policy_id (policy_id),
  INDEX idx_rule_id (rule_id),
  FOREIGN KEY (policy_id) REFERENCES datasentry_cleaning_policy(id) ON DELETE CASCADE,
  FOREIGN KEY (rule_id) REFERENCES datasentry_cleaning_rule(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='策略规则关联表';

-- 清理绑定表
CREATE TABLE IF NOT EXISTS datasentry_cleaning_binding (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  binding_type VARCHAR(50) NOT NULL COMMENT '绑定类型：ONLINE_TEXT',
  scene VARCHAR(100) DEFAULT NULL COMMENT '场景',
  policy_id BIGINT NOT NULL COMMENT '策略ID',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_policy_id (policy_id),
  INDEX idx_enabled (enabled),
  FOREIGN KEY (agent_id) REFERENCES datasentry_agent(id) ON DELETE CASCADE,
  FOREIGN KEY (policy_id) REFERENCES datasentry_cleaning_policy(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='清理绑定表';

-- 清理白名单
CREATE TABLE IF NOT EXISTS datasentry_cleaning_allowlist (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL COMMENT '白名单名称',
  type VARCHAR(50) NOT NULL COMMENT '类型：EXACT/REGEX/CONTAINS/PREFIX/SUFFIX',
  value TEXT NOT NULL COMMENT '匹配值',
  category VARCHAR(100) DEFAULT NULL COMMENT '类别',
  scope_type VARCHAR(50) DEFAULT 'GLOBAL' COMMENT '作用范围',
  scope_id BIGINT DEFAULT NULL COMMENT '作用范围ID',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  expire_time TIMESTAMP NULL DEFAULT NULL COMMENT '过期时间',
  created_by VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_category (category),
  INDEX idx_scope (scope_type, scope_id),
  INDEX idx_enabled (enabled),
  INDEX idx_expire_time (expire_time)
) ENGINE=InnoDB COMMENT='清理白名单';

-- 清理任务
CREATE TABLE IF NOT EXISTS datasentry_cleaning_job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  datasource_id BIGINT NOT NULL COMMENT '数据源ID',
  table_name VARCHAR(255) NOT NULL COMMENT '目标表',
  pk_columns_json TEXT NOT NULL COMMENT '主键列JSON',
  target_columns_json TEXT NOT NULL COMMENT '目标列JSON',
  where_sql TEXT COMMENT '过滤条件',
  policy_id BIGINT NOT NULL COMMENT '策略ID',
  mode VARCHAR(50) DEFAULT 'DRY_RUN' COMMENT '运行模式',
  writeback_mode VARCHAR(50) DEFAULT 'NONE' COMMENT '写回模式',
  review_policy VARCHAR(50) DEFAULT 'NEVER' COMMENT '人审策略',
  backup_policy_json TEXT COMMENT '备份策略JSON',
  writeback_mapping_json TEXT COMMENT '写回映射JSON',
  batch_size INT DEFAULT 200 COMMENT '批量大小',
  concurrency INT DEFAULT 1 COMMENT '并发数',
  rate_limit INT DEFAULT NULL COMMENT '限速',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_datasource_id (datasource_id),
  INDEX idx_policy_id (policy_id),
  INDEX idx_enabled (enabled)
) ENGINE=InnoDB COMMENT='清理任务';

-- 清理任务运行实例
CREATE TABLE IF NOT EXISTS datasentry_cleaning_job_run (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NOT NULL COMMENT '任务ID',
  status VARCHAR(50) NOT NULL COMMENT '状态',
  lease_owner VARCHAR(100) DEFAULT NULL COMMENT '租约持有者',
  lease_until TIMESTAMP NULL DEFAULT NULL COMMENT '租约过期时间',
  heartbeat_time TIMESTAMP NULL DEFAULT NULL COMMENT '心跳时间',
  attempt INT DEFAULT 0 COMMENT '重试次数',
  checkpoint_json TEXT COMMENT '游标检查点JSON',
  policy_snapshot_json TEXT COMMENT '策略快照',
  total_scanned BIGINT DEFAULT 0 COMMENT '扫描总数',
  total_flagged BIGINT DEFAULT 0 COMMENT '命中总数',
  total_written BIGINT DEFAULT 0 COMMENT '写回总数',
  total_failed BIGINT DEFAULT 0 COMMENT '失败总数',
  started_time TIMESTAMP NULL DEFAULT NULL COMMENT '开始时间',
  ended_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_job_id (job_id),
  INDEX idx_status (status),
  INDEX idx_lease_until (lease_until)
) ENGINE=InnoDB COMMENT='清理任务运行实例';

-- 清理备份记录
CREATE TABLE IF NOT EXISTS datasentry_cleaning_backup_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_run_id BIGINT NOT NULL COMMENT '任务运行ID',
  datasource_id BIGINT NOT NULL COMMENT '数据源ID',
  table_name VARCHAR(255) NOT NULL COMMENT '表名',
  pk_json TEXT NOT NULL COMMENT '主键JSON',
  pk_hash VARCHAR(128) NOT NULL COMMENT '主键Hash',
  encryption_provider VARCHAR(50) DEFAULT NULL COMMENT '加密提供方',
  key_version VARCHAR(50) DEFAULT NULL COMMENT '密钥版本',
  before_row_ciphertext TEXT COMMENT '加密后的旧值快照',
  before_row_json TEXT COMMENT '明文旧值快照',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_job_run_id (job_run_id),
  INDEX idx_pk_hash (pk_hash)
) ENGINE=InnoDB COMMENT='清理备份记录';

-- 清理审计记录
CREATE TABLE IF NOT EXISTS datasentry_cleaning_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  trace_id VARCHAR(64) DEFAULT NULL COMMENT 'Trace ID',
  job_run_id BIGINT DEFAULT NULL COMMENT '任务运行ID',
  datasource_id BIGINT DEFAULT NULL COMMENT '数据源ID',
  table_name VARCHAR(255) DEFAULT NULL COMMENT '表名',
  pk_json TEXT COMMENT '主键JSON',
  column_name VARCHAR(255) DEFAULT NULL COMMENT '命中字段',
  action_taken VARCHAR(50) DEFAULT NULL COMMENT '执行动作',
  policy_snapshot_json TEXT COMMENT '策略快照',
  verdict VARCHAR(50) NOT NULL COMMENT '判定结果',
  categories_json TEXT COMMENT '命中类别JSON',
  sanitized_preview TEXT COMMENT '脱敏预览',
  evidence_json TEXT COMMENT '证据信息JSON',
  metrics_json TEXT COMMENT '指标JSON',
  execution_time_ms BIGINT DEFAULT NULL COMMENT '执行时间(ms)',
  detector_source VARCHAR(100) DEFAULT NULL COMMENT '检测来源',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_trace_id (trace_id),
  INDEX idx_job_run_id (job_run_id),
  INDEX idx_created_time (created_time)
) ENGINE=InnoDB COMMENT='清理审计记录';

-- 清理人审任务
CREATE TABLE IF NOT EXISTS datasentry_cleaning_review_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_run_id BIGINT NOT NULL COMMENT '任务运行ID',
  agent_id BIGINT NOT NULL COMMENT '智能体ID',
  datasource_id BIGINT NOT NULL COMMENT '数据源ID',
  table_name VARCHAR(255) NOT NULL COMMENT '表名',
  pk_json TEXT NOT NULL COMMENT '主键JSON',
  pk_hash VARCHAR(128) NOT NULL COMMENT '主键Hash',
  column_name VARCHAR(255) DEFAULT NULL COMMENT '字段',
  verdict VARCHAR(50) DEFAULT NULL COMMENT '判定结果',
  categories_json TEXT COMMENT '命中类别JSON',
  sanitized_preview TEXT COMMENT '脱敏预览',
  action_suggested VARCHAR(50) DEFAULT NULL COMMENT '建议动作',
  writeback_payload_json TEXT COMMENT '写回内容JSON',
  before_row_json TEXT COMMENT '原值JSON',
  status VARCHAR(50) NOT NULL COMMENT '状态',
  reviewer VARCHAR(100) DEFAULT NULL COMMENT '审核人',
  review_reason TEXT COMMENT '审核原因',
  version INT DEFAULT 0 COMMENT '乐观锁版本',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_job_run_id (job_run_id),
  INDEX idx_status (status),
  INDEX idx_pk_hash (pk_hash)
) ENGINE=InnoDB COMMENT='清理人审任务';

-- 清理回滚任务
CREATE TABLE IF NOT EXISTS datasentry_cleaning_rollback_run (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_run_id BIGINT NOT NULL COMMENT '任务运行ID',
  status VARCHAR(50) NOT NULL COMMENT '状态',
  checkpoint_id BIGINT DEFAULT NULL COMMENT '回滚检查点',
  total_target BIGINT DEFAULT 0 COMMENT '目标总数',
  total_success BIGINT DEFAULT 0 COMMENT '成功总数',
  total_failed BIGINT DEFAULT 0 COMMENT '失败总数',
  started_time TIMESTAMP NULL DEFAULT NULL COMMENT '开始时间',
  ended_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_job_run_id (job_run_id),
  INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='清理回滚任务';
