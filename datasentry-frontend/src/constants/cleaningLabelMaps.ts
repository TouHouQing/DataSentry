import type { CleaningOptionMetaView } from '@/services/cleaning';

export const UI_MODE_STORAGE_KEY = 'datasentry.cleaning.ui_mode';

export const UI_MODE_BEGINNER = 'beginner';

export const UI_MODE_EXPERT = 'expert';

export const FALLBACK_OPTIONS: CleaningOptionMetaView = {
  defaultActions: [
    {
      code: 'DETECT_ONLY',
      labelZh: '仅检测',
      description: '只检测不写回',
      effect: '适合新手默认',
      riskLevel: '低',
      recommendedFor: '初次上线',
    },
    {
      code: 'SANITIZE_RETURN',
      labelZh: '检测并返回脱敏文本',
      description: '返回脱敏结果，不写回',
      effect: '适合在线调用',
      riskLevel: '中',
      recommendedFor: '在线接口',
    },
    {
      code: 'SANITIZE_WRITEBACK',
      labelZh: '脱敏后写回',
      description: '将脱敏结果写回数据库',
      effect: '适合批量治理',
      riskLevel: '较高',
      recommendedFor: '离线治理',
    },
    {
      code: 'REVIEW_THEN_WRITEBACK',
      labelZh: '先人审再写回',
      description: '命中风险先进入人审',
      effect: '可降低误写回风险',
      riskLevel: '中',
      recommendedFor: '高敏业务',
    },
    {
      code: 'DELETE',
      labelZh: '删除',
      description: '删除命中风险数据',
      effect: '高风险动作',
      riskLevel: '高',
      recommendedFor: '强合规场景',
    },
  ],
  ruleTypes: [
    {
      code: 'REGEX',
      labelZh: '正则规则（L1）',
      description: '快速匹配固定模式文本',
      configSchemaHint: '{"pattern":"...","flags":"CASE_INSENSITIVE"}',
      sampleConfig: { pattern: '\\d{11}', flags: 'CASE_INSENSITIVE' },
    },
    {
      code: 'L2_DUMMY',
      labelZh: 'L2 启发式规则',
      description: '包含正则拦截、乱码检测、重复检测等多种启发式策略',
      configSchemaHint: '{"pattern":"...","threshold":0.8}',
      sampleConfig: {},
    },
    {
      code: 'LLM',
      labelZh: '大模型规则（L3）',
      description: '语义理解能力强，成本高',
      configSchemaHint: '{}',
      sampleConfig: {},
    },
  ],
  ruleCategories: [
    { code: 'PII', labelZh: '个人隐私', description: '手机号、邮箱、身份证等' },
    { code: 'SPAM', labelZh: '垃圾营销', description: '广告导流、欺诈拉群等' },
    { code: 'SECURITY', labelZh: '安全风险', description: '攻击、钓鱼、木马等' },
    { code: 'COMPLIANCE', labelZh: '合规风险', description: '监管敏感内容' },
  ],
  reviewPolicies: [
    { code: 'NEVER', labelZh: '不进入人审', description: '直接执行策略动作' },
    { code: 'ON_RISK', labelZh: '有风险时人审', description: '命中风险再进人审队列' },
    { code: 'ALWAYS', labelZh: '总是人审', description: '每条都需人工确认' },
  ],
  jobModes: [
    { code: 'DRY_RUN', labelZh: '试运行', description: '仅评估不写库' },
    { code: 'WRITEBACK', labelZh: '正式写回', description: '执行写回动作' },
  ],
  writebackModes: [
    { code: 'NONE', labelZh: '不写回', description: '只检测不修改数据' },
    { code: 'UPDATE', labelZh: '更新写回', description: '覆盖更新目标字段' },
    { code: 'SOFT_DELETE', labelZh: '软删除', description: '标记删除状态' },
    {
      code: 'HARD_DELETE',
      labelZh: '硬删除',
      description: '直接删除记录（高风险）',
      caution: '需 delete:hard 权限',
    },
  ],
  runStatuses: [
    { code: 'QUEUED', labelZh: '排队中' },
    { code: 'RUNNING', labelZh: '运行中' },
    { code: 'PAUSED', labelZh: '已暂停' },
    { code: 'SUCCEEDED', labelZh: '已成功' },
    { code: 'FAILED', labelZh: '失败' },
    { code: 'CANCELED', labelZh: '已取消' },
  ],
  verdicts: [
    { code: 'ALLOW', labelZh: '放行', description: '没有命中风险' },
    { code: 'BLOCK', labelZh: '拦截', description: '风险高，禁止通过' },
    { code: 'REVIEW', labelZh: '待人审', description: '人工确认后处理' },
    { code: 'REDACTED', labelZh: '已脱敏', description: '文本已替换敏感内容' },
  ],
  targetConfigTypes: [
    { code: 'COLUMNS', labelZh: '列模式', description: '整列清理' },
    { code: 'JSONPATH', labelZh: 'JSONPath 模式', description: '清理 JSON 子字段' },
  ],
  thresholdGuidance: [
    {
      code: 'blockThreshold',
      labelZh: 'Block 阈值',
      defaultValue: 0.7,
      description: '超过此值直接拦截',
      recommendedRange: '0.70 - 0.90',
    },
    {
      code: 'reviewThreshold',
      labelZh: 'Review 阈值',
      defaultValue: 0.4,
      description: '超过此值进入人审区间',
      recommendedRange: '0.35 - 0.65',
    },
    {
      code: 'l2Threshold',
      labelZh: 'L2 阈值',
      defaultValue: 0.6,
      description: 'L2 判定风险分阈值',
      recommendedRange: '0.50 - 0.75',
    },
    {
      code: 'shadowSampleRatio',
      labelZh: 'Shadow 采样率',
      defaultValue: 0,
      description: '影子流量采样比例',
      recommendedRange: '0.00 - 0.20',
    },
  ],
  severityGuidance: [
    {
      level: 'LOW',
      min: 0,
      max: 0.39,
      labelZh: '低风险',
      description: '越宽松，误拦截低但漏检风险更高',
    },
    {
      level: 'MEDIUM',
      min: 0.4,
      max: 0.69,
      labelZh: '中风险',
      description: '平衡误拦截与漏检，推荐默认',
    },
    {
      level: 'HIGH',
      min: 0.7,
      max: 1,
      labelZh: '高风险',
      description: '越严格，更容易进入 REVIEW/BLOCK',
    },
  ],
  riskConfirmations: {
    SANITIZE_WRITEBACK: '将直接写回原表数据，建议先试运行验证。确认继续？',
    REVIEW_THEN_WRITEBACK: '命中后会先进入人审，通过后写回。请确认审批流程已准备好。',
    DELETE: '该动作会删除或软删数据，风险极高。请确认已评估回滚方案。',
    WRITEBACK: '正式写回会修改业务数据，建议先用试运行观察结果。',
    HARD_DELETE: '硬删除会直接删除原始记录，仅在确认权限与回滚预案后执行。',
  },
  ruleTypeUiBehavior: {
    REGEX: { showStructuredConfig: true, showAdvancedJson: true },
    L2_DUMMY: { showStructuredConfig: true, showAdvancedJson: true },
    LLM: { showStructuredConfig: false, showAdvancedJson: true },
  },
  ruleTypeSchemas: {
    REGEX: {
      ruleType: 'REGEX',
      title: '正则规则配置',
      description: '适合手机号、邮箱、证件号等固定模式识别',
      fields: [
        {
          name: 'pattern',
          labelZh: '正则表达式',
          type: 'string',
          required: true,
          defaultValue: '\\d{11}',
          placeholder: '例如：\\d{11}',
          help: '用于匹配文本模式，不能为空',
        },
        {
          name: 'flags',
          labelZh: '匹配选项',
          type: 'multi_select',
          required: false,
          defaultValue: ['CASE_INSENSITIVE'],
          help: '可多选：忽略大小写、多行匹配、点号匹配换行',
          options: ['CASE_INSENSITIVE', 'MULTILINE', 'DOTALL'],
        },
      ],
    },
    L2_DUMMY: {
      ruleType: 'L2_DUMMY',
      title: '轻量模型规则',
      description: '当前版本无需额外配置，按阈值打分',
      fields: [],
    },
    LLM: {
      ruleType: 'LLM',
      title: '大模型规则',
      description: '当前版本无需额外配置，主要通过策略阈值控制',
      fields: [],
    },
  },
  regexTemplates: [
    {
      code: 'PHONE_CN',
      labelZh: '中国手机号',
      description: '匹配 11 位手机号',
      effect: '请联系我 13812345678',
      sampleConfig: { pattern: '\\b1[3-9]\\d{9}\\b', flags: 'CASE_INSENSITIVE' },
    },
    {
      code: 'EMAIL',
      labelZh: '邮箱地址',
      description: '匹配常见邮箱格式',
      effect: '请发邮件到 test@example.com',
      sampleConfig: {
        pattern: '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}',
        flags: 'CASE_INSENSITIVE',
      },
    },
    {
      code: 'ID_CARD_CN',
      labelZh: '身份证号',
      description: '匹配 18 位身份证号',
      effect: '身份证 110101199001018888',
      sampleConfig: { pattern: '\\b\\d{17}[0-9Xx]\\b', flags: 'CASE_INSENSITIVE' },
    },
  ],
  jsonConfigTemplates: {
    REGEX: { pattern: '\\d{11}', flags: 'CASE_INSENSITIVE' },
    L2_DUMMY: {},
    LLM: {},
  },
  fieldHelp: {
    'policy.defaultAction': '默认动作用于策略兜底，决定命中后如何处理',
    'policy.ruleType': '规则类型决定检测层级和成本',
    'policy.configJson': '新手建议使用结构化字段；高级 JSON 用于专家自定义参数',
    'policy.regex.pattern': '正则表达式用于匹配目标文本模式，建议先用模板再微调',
    'policy.regex.flags': '匹配选项可多选：忽略大小写、多行、点号匹配换行',
    'policy.severity': '严重度越大越严格，更容易进入 REVIEW/BLOCK；越小越宽松',
    'policy.threshold': '阈值越低越严格，命中越多',
    'policy.shadow': 'Shadow 只观测不影响线上决策',
    'job.targetConfigType': '列模式清理整列；JSONPath 模式清理 JSON 内部字段',
    'job.reviewPolicy': '决定是否进入人工审核',
    'job.mode': '试运行不落库，正式写回会修改数据',
    'job.whereSql': '只需填写筛选条件本身，不要写 WHERE 关键字与更新语句',
    'job.wizard': '建议按步骤创建任务：数据范围 -> 清理对象 -> 风险控制',
    'job.budget': '预算用于控制清理成本',
    'realtime.action': 'check 只检测；sanitize 检测并返回脱敏文本',
    'realtime.scene': '场景用于策略细分和效果分析',
    'realtime.policyId': '可指定策略进行调试',
    'realtime.verdict': '判定结果含义：放行/拦截/人审/已脱敏',
    'realtime.explain': '系统会解释判定原因：命中类别、风险分与阈值关系',
  },
};

const getItems = (
  options: CleaningOptionMetaView | null | undefined,
  key: keyof CleaningOptionMetaView,
) => {
  const value = options?.[key];
  if (Array.isArray(value) && value.length > 0) {
    return value;
  }
  const fallback = FALLBACK_OPTIONS[key];
  return Array.isArray(fallback) ? fallback : [];
};

export const mergeOptionsWithFallback = (
  options: CleaningOptionMetaView | null,
): CleaningOptionMetaView => {
  return {
    ...FALLBACK_OPTIONS,
    ...(options || {}),
    defaultActions: getItems(options, 'defaultActions'),
    ruleTypes: getItems(options, 'ruleTypes'),
    ruleCategories: getItems(options, 'ruleCategories'),
    reviewPolicies: getItems(options, 'reviewPolicies'),
    jobModes: getItems(options, 'jobModes'),
    writebackModes: getItems(options, 'writebackModes'),
    runStatuses: getItems(options, 'runStatuses'),
    verdicts: getItems(options, 'verdicts'),
    targetConfigTypes: getItems(options, 'targetConfigTypes'),
    thresholdGuidance: getItems(options, 'thresholdGuidance'),
    severityGuidance: getItems(options, 'severityGuidance'),
    riskConfirmations: {
      ...(FALLBACK_OPTIONS.riskConfirmations || {}),
      ...(options?.riskConfirmations || {}),
    },
    ruleTypeUiBehavior: {
      ...(FALLBACK_OPTIONS.ruleTypeUiBehavior || {}),
      ...(options?.ruleTypeUiBehavior || {}),
    },
    ruleTypeSchemas: {
      ...(FALLBACK_OPTIONS.ruleTypeSchemas || {}),
      ...(options?.ruleTypeSchemas || {}),
    },
    regexTemplates: getItems(options, 'regexTemplates'),
    jsonConfigTemplates: options?.jsonConfigTemplates || FALLBACK_OPTIONS.jsonConfigTemplates || {},
    fieldHelp: {
      ...(FALLBACK_OPTIONS.fieldHelp || {}),
      ...(options?.fieldHelp || {}),
    },
  };
};

export const readCleaningUiMode = () => {
  const mode = localStorage.getItem(UI_MODE_STORAGE_KEY);
  if (mode === UI_MODE_EXPERT) {
    return UI_MODE_EXPERT;
  }
  return UI_MODE_BEGINNER;
};

export const writeCleaningUiMode = (mode: string) => {
  const target = mode === UI_MODE_EXPERT ? UI_MODE_EXPERT : UI_MODE_BEGINNER;
  localStorage.setItem(UI_MODE_STORAGE_KEY, target);
};

export const buildOptionLabel = (
  code: string | undefined,
  options: { code: string; labelZh?: string }[],
) => {
  if (!code) {
    return '-';
  }
  const matched = options.find(item => item.code === code);
  if (!matched) {
    return code;
  }
  return `${matched.labelZh || matched.code}（${matched.code}）`;
};

export const getOptionLabelZh = (
  code: string | undefined,
  options: { code: string; labelZh?: string }[],
) => {
  if (!code) {
    return '-';
  }
  return options.find(item => item.code === code)?.labelZh || code;
};
