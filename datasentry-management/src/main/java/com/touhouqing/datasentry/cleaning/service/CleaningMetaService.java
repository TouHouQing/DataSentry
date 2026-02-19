package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.dto.CleaningOptionItemView;
import com.touhouqing.datasentry.cleaning.dto.CleaningOptionMetaView;
import com.touhouqing.datasentry.cleaning.dto.CleaningRuleTypeSchemaView;
import com.touhouqing.datasentry.cleaning.dto.CleaningSeverityGuidanceItemView;
import com.touhouqing.datasentry.cleaning.dto.CleaningThresholdItemView;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CleaningMetaService {

	public CleaningOptionMetaView getOptions() {
		CleaningPolicyConfig defaultPolicyConfig = new CleaningPolicyConfig();

		return CleaningOptionMetaView.builder()
			.defaultActions(defaultActions())
			.ruleTypes(ruleTypes())
			.ruleCategories(ruleCategories())
			.reviewPolicies(reviewPolicies())
			.jobModes(jobModes())
			.writebackModes(writebackModes())
			.runStatuses(runStatuses())
			.verdicts(verdicts())
			.targetConfigTypes(targetConfigTypes())
			.thresholdGuidance(thresholdGuidance(defaultPolicyConfig))
			.ruleTypeSchemas(ruleTypeSchemas())
			.ruleTypeUiBehavior(ruleTypeUiBehavior())
			.severityGuidance(severityGuidance())
			.riskConfirmations(riskConfirmations())
			.regexTemplates(regexTemplates())
			.jsonConfigTemplates(jsonConfigTemplates())
			.fieldHelp(fieldHelp())
			.build();
	}

	private List<CleaningOptionItemView> defaultActions() {
		return List.of(option("DETECT_ONLY", "仅检测", "只返回风险结果，不做脱敏和写回", "上线初期最稳妥", "低", "新手默认"),
				option("SANITIZE_RETURN", "检测并返回脱敏文本", "返回脱敏后的文本，不写回数据库", "适合在线接口返回", "中", "在线清理"),
				option("SANITIZE_WRITEBACK", "脱敏后写回", "将脱敏结果写回目标表", "批处理治理常用", "较高", "存量治理"),
				option("REVIEW_THEN_WRITEBACK", "先人审再写回", "命中风险先进入人审，通过后再写回", "误杀风险可控", "中", "高敏业务"),
				option("DELETE", "删除", "命中后删除或软删除数据", "需严格权限控制", "高", "合规删除场景"));
	}

	private List<CleaningOptionItemView> ruleTypes() {
		return List.of(
				CleaningOptionItemView.builder()
					.code("REGEX")
					.labelZh("正则规则（L1）")
					.description("通过正则表达式快速识别手机号、邮箱等模式")
					.configSchemaHint("{\"pattern\":\"...\",\"flags\":\"CASE_INSENSITIVE\"}")
					.sampleConfig(regexConfigTemplate())
					.build(),
				CleaningOptionItemView.builder()
					.code("L2_DUMMY")
					.labelZh("L2 启发式规则")
					.description("包含正则拦截、乱码检测、重复检测等多种启发式策略")
					.configSchemaHint("{}")
					.sampleConfig(Map.of())
					.build(),
				CleaningOptionItemView.builder()
					.code("LLM")
					.labelZh("大模型规则（L3）")
					.description("复杂语义场景使用大模型检测，成本较高")
					.configSchemaHint("{}")
					.sampleConfig(Map.of())
					.build());
	}

	private List<CleaningOptionItemView> ruleCategories() {
		return List.of(option("PII", "个人隐私", "手机号、身份证、邮箱等个人信息"), option("SPAM", "垃圾营销", "广告、拉群、导流、欺诈等"),
				option("SECURITY", "安全风险", "木马链接、钓鱼、注入等攻击内容"), option("COMPLIANCE", "合规风险", "违规用语、监管敏感内容"));
	}

	private List<CleaningOptionItemView> reviewPolicies() {
		return List.of(option("NEVER", "不进入人审", "自动执行策略动作"), option("ON_RISK", "有风险时人审", "命中风险再进入人审队列"),
				option("ALWAYS", "总是人审", "每条都需人工确认，最稳妥"));
	}

	private List<CleaningOptionItemView> jobModes() {
		return List.of(option("DRY_RUN", "试运行", "只检测与评估，不落库写回"), option("WRITEBACK", "正式写回", "按照写回模式执行更新或软删"));
	}

	private List<CleaningOptionItemView> writebackModes() {
		return List.of(optionWithCaution("NONE", "不写回", "仅生成检测结果", null),
				optionWithCaution("UPDATE", "更新写回", "将脱敏后的字段更新回原表", "建议先在试运行验证"),
				optionWithCaution("SOFT_DELETE", "软删除", "将记录标记为已删除状态", "需确认业务有软删字段"),
				optionWithCaution("HARD_DELETE", "硬删除", "直接删除命中记录", "高风险动作，需 delete:hard 权限"));
	}

	private List<CleaningOptionItemView> runStatuses() {
		return List.of(option("QUEUED", "排队中", "等待调度执行"), option("RUNNING", "运行中", "任务正在处理"),
				option("PAUSED", "已暂停", "任务暂停，可恢复"), option("SUCCEEDED", "已成功", "任务执行完成"),
				option("FAILED", "失败", "任务执行失败"), option("CANCELED", "已取消", "任务被取消"));
	}

	private List<CleaningOptionItemView> verdicts() {
		return List.of(option("ALLOW", "放行", "未命中风险规则"), option("BLOCK", "拦截", "风险分超过拦截阈值"),
				option("REVIEW", "待人审", "风险分处于人审区间"), option("REDACTED", "已脱敏", "内容已按策略脱敏"));
	}

	private List<CleaningOptionItemView> targetConfigTypes() {
		return List.of(option("COLUMNS", "列模式", "直接对目标列整列清理"), option("JSONPATH", "JSONPath 模式", "针对 JSON 字段内部路径定点清理"));
	}

	private List<CleaningThresholdItemView> thresholdGuidance(CleaningPolicyConfig defaultPolicyConfig) {
		return List.of(
				threshold("blockThreshold", "Block 阈值", defaultPolicyConfig.resolvedBlockThreshold(), "风险分大于等于该值时直接拦截",
						"0.70 - 0.90"),
				threshold("reviewThreshold", "Review 阈值", defaultPolicyConfig.resolvedReviewThreshold(),
						"风险分处于 review 与 block 之间时进入人审", "0.35 - 0.65"),
				threshold("l2Threshold", "L2 阈值", defaultPolicyConfig.resolvedL2Threshold(), "轻量模型判定为风险的最小分数",
						"0.50 - 0.75"),
				threshold("shadowSampleRatio", "Shadow 采样率", defaultPolicyConfig.resolvedShadowSampleRatio(),
						"影子流量比例，只做观测不影响主流程", "0.00 - 0.20"));
	}

	private Map<String, Object> jsonConfigTemplates() {
		Map<String, Object> templates = new LinkedHashMap<>();
		templates.put("REGEX", regexConfigTemplate());
		templates.put("L2_DUMMY", Map.of());
		templates.put("LLM", Map.of());
		return templates;
	}

	private Map<String, CleaningRuleTypeSchemaView> ruleTypeSchemas() {
		Map<String, CleaningRuleTypeSchemaView> schemas = new LinkedHashMap<>();
		schemas.put("REGEX",
				CleaningRuleTypeSchemaView.builder()
					.ruleType("REGEX")
					.title("正则规则配置")
					.description("适合手机号、邮箱、证件号等固定模式识别")
					.fields(List.of(
							CleaningRuleTypeSchemaView.CleaningRuleTypeFieldView.builder()
								.name("pattern")
								.labelZh("正则表达式")
								.type("string")
								.required(true)
								.defaultValue("\\\\d{11}")
								.placeholder("例如：\\\\d{11}")
								.help("用于匹配文本模式，不能为空")
								.build(),
							CleaningRuleTypeSchemaView.CleaningRuleTypeFieldView.builder()
								.name("flags")
								.labelZh("匹配选项")
								.type("multi_select")
								.required(false)
								.defaultValue(List.of("CASE_INSENSITIVE"))
								.help("可多选：忽略大小写、多行匹配、点号匹配换行")
								.options(List.of("CASE_INSENSITIVE", "MULTILINE", "DOTALL"))
								.build(),
							CleaningRuleTypeSchemaView.CleaningRuleTypeFieldView.builder()
								.name("maskMode")
								.labelZh("脱敏方式")
								.type("select")
								.required(false)
								.defaultValue("PLACEHOLDER")
								.help("PLACEHOLDER 用固定文本替换；DELETE 删除命中内容")
								.options(List.of("PLACEHOLDER", "DELETE"))
								.build(),
							CleaningRuleTypeSchemaView.CleaningRuleTypeFieldView.builder()
								.name("maskText")
								.labelZh("替换文本")
								.type("string")
								.required(false)
								.defaultValue("[REDACTED]")
								.placeholder("例如：*** 或 [PHONE]")
								.help("仅在脱敏方式为 PLACEHOLDER 时生效，留空默认 [REDACTED]")
								.build()))
					.build());
		schemas.put("L2_DUMMY",
				CleaningRuleTypeSchemaView.builder()
					.ruleType("L2_DUMMY")
					.title("轻量模型规则")
					.description("当前版本无需额外配置，按阈值打分")
					.fields(List.of())
					.build());
		schemas.put("LLM",
				CleaningRuleTypeSchemaView.builder()
					.ruleType("LLM")
					.title("大模型规则")
					.description("当前版本无需额外配置，主要通过策略阈值控制")
					.fields(List.of())
					.build());
		return schemas;
	}

	private Map<String, Map<String, Boolean>> ruleTypeUiBehavior() {
		Map<String, Map<String, Boolean>> behavior = new LinkedHashMap<>();
		behavior.put("REGEX", Map.of("showStructuredConfig", true, "showAdvancedJson", true));
		behavior.put("L2_DUMMY", Map.of("showStructuredConfig", true, "showAdvancedJson", true));
		behavior.put("LLM", Map.of("showStructuredConfig", false, "showAdvancedJson", false));
		return behavior;
	}

	private List<CleaningSeverityGuidanceItemView> severityGuidance() {
		return List.of(
				CleaningSeverityGuidanceItemView.builder()
					.level("LOW")
					.min(0.0)
					.max(0.39)
					.labelZh("低风险")
					.description("分值越小越宽松，误拦截更少但漏检风险更高")
					.build(),
				CleaningSeverityGuidanceItemView.builder()
					.level("MEDIUM")
					.min(0.4)
					.max(0.69)
					.labelZh("中风险")
					.description("平衡误拦截与漏检，建议默认落在此区间")
					.build(),
				CleaningSeverityGuidanceItemView.builder()
					.level("HIGH")
					.min(0.7)
					.max(1.0)
					.labelZh("高风险")
					.description("分值越大越严格，更容易进入 REVIEW/BLOCK")
					.build());
	}

	private Map<String, String> riskConfirmations() {
		Map<String, String> confirmations = new LinkedHashMap<>();
		confirmations.put("SANITIZE_WRITEBACK", "将直接写回原表数据，建议先试运行验证。确认继续？");
		confirmations.put("REVIEW_THEN_WRITEBACK", "命中后会先进入人审，通过后写回。请确认审批流程已准备好。");
		confirmations.put("DELETE", "该动作会删除或软删数据，风险极高。请确认已评估回滚方案。");
		confirmations.put("WRITEBACK", "正式写回会修改业务数据，建议先用试运行观察结果。");
		confirmations.put("HARD_DELETE", "硬删除会直接删除原始记录，仅在确认权限与回滚预案后执行。");
		return confirmations;
	}

	private List<CleaningOptionItemView> regexTemplates() {
		return List.of(regexTemplate("PHONE_CN", "中国手机号", "匹配 11 位手机号", "请联系我 13812345678", "\\\\b1[3-9]\\\\d{9}\\\\b"),
				regexTemplate("EMAIL", "邮箱地址", "匹配常见邮箱格式", "请发邮件到 test@example.com",
						"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}"),
				regexTemplate("ID_CARD_CN", "身份证号", "匹配 18 位身份证号", "身份证 110101199001018888",
						"\\\\b\\\\d{17}[0-9Xx]\\\\b"),
				regexTemplate("BANK_CARD", "银行卡号", "匹配 16-19 位银行卡号", "卡号 6222021234567890123",
						"\\\\b\\\\d{16,19}\\\\b"),
				regexTemplate("URL", "URL 链接", "匹配 http/https 链接", "访问 https://example.com", "https?://[^\\\\s]+"),
				regexTemplate("IPV4", "IPv4 地址", "匹配 IPv4 地址", "登录来源 192.168.0.1",
						"\\\\b(?:\\\\d{1,3}\\\\.){3}\\\\d{1,3}\\\\b"),
				regexTemplate("WECHAT", "微信号", "匹配常见微信号规则", "我的微信号 abc_12345",
						"\\\\b[a-zA-Z][-_a-zA-Z0-9]{5,19}\\\\b"));
	}

	private CleaningOptionItemView regexTemplate(String code, String labelZh, String description, String exampleText,
			String pattern) {
		return CleaningOptionItemView.builder()
			.code(code)
			.labelZh(labelZh)
			.description(description)
			.effect(exampleText)
			.sampleConfig(Map.of("pattern", pattern, "flags", "CASE_INSENSITIVE"))
			.build();
	}

	private Map<String, Object> regexConfigTemplate() {
		Map<String, Object> template = new LinkedHashMap<>();
		template.put("pattern", "\\\\d{11}");
		template.put("flags", "CASE_INSENSITIVE");
		template.put("maskMode", "PLACEHOLDER");
		template.put("maskText", "[REDACTED]");
		return template;
	}

	private Map<String, String> fieldHelp() {
		Map<String, String> help = new LinkedHashMap<>();
		help.put("policy.defaultAction", "默认动作是策略兜底行为，规则命中后按该动作执行");
		help.put("policy.ruleType", "规则类型决定检测层级：REGEX 快、L2 成本低、LLM 语义强");
		help.put("policy.configJson", "新手建议使用结构化字段；高级 JSON 用于专家自定义参数");
		help.put("policy.regex.pattern", "正则表达式用于匹配目标文本模式，建议先用模板再微调");
		help.put("policy.regex.flags", "匹配选项可多选：忽略大小写、多行、点号匹配换行");
		help.put("policy.regex.maskMode", "脱敏方式：固定文本替换或删除命中内容");
		help.put("policy.regex.maskText", "替换文本仅在固定文本模式下生效，留空默认 [REDACTED]");
		help.put("policy.severity", "严重度越大越严格，更容易进入 REVIEW/BLOCK；越小越宽松");
		help.put("policy.threshold", "阈值越低越严格，命中率更高但误杀风险也会提升");
		help.put("policy.highRiskSanitizationMode",
				"高风险脱敏模式：MITIGATE（默认）脱敏后由 BLOCK 转为 REDACTED 可写回；QUARANTINE 保持 BLOCK 不写回");
		help.put("policy.shadow", "Shadow 为影子模式，只观测不影响主决策");
		help.put("job.targetConfigType", "列模式直接清理列值；JSONPath 模式只清理 JSON 子字段");
		help.put("job.reviewPolicy", "人审策略决定何时进入人工审核队列");
		help.put("job.reviewBlockOnRisk", "开启后，BLOCK 判定也会进入人审工作台（ALWAYS 默认开启）");
		help.put("job.mode", "试运行仅评估，正式写回会修改业务数据");
		help.put("job.whereSql", "只需填写筛选条件本身，不要写 WHERE 关键字与更新语句");
		help.put("job.wizard", "建议按步骤创建任务：数据范围 -> 清理对象 -> 风险控制");
		help.put("job.budget", "预算用于控制成本，超硬阈值后任务会暂停或拒绝继续");
		help.put("realtime.action", "check 只检测；sanitize 会返回脱敏文本");
		help.put("realtime.scene", "业务场景可用于策略细分和效果观测");
		help.put("realtime.policyId", "可指定某个策略验证，不填则走默认策略");
		help.put("realtime.verdict", "判定结果：ALLOW 放行，REVIEW 人审，BLOCK 拦截");
		help.put("realtime.explain", "系统会解释判定原因：命中类别、风险分与阈值关系");
		return help;
	}

	private CleaningOptionItemView option(String code, String labelZh, String description) {
		return CleaningOptionItemView.builder().code(code).labelZh(labelZh).description(description).build();
	}

	private CleaningOptionItemView option(String code, String labelZh, String description, String effect,
			String riskLevel, String recommendedFor) {
		return CleaningOptionItemView.builder()
			.code(code)
			.labelZh(labelZh)
			.description(description)
			.effect(effect)
			.riskLevel(riskLevel)
			.recommendedFor(recommendedFor)
			.build();
	}

	private CleaningOptionItemView optionWithCaution(String code, String labelZh, String description, String caution) {
		return CleaningOptionItemView.builder()
			.code(code)
			.labelZh(labelZh)
			.description(description)
			.caution(caution)
			.build();
	}

	private CleaningThresholdItemView threshold(String code, String labelZh, double defaultValue, String description,
			String recommendedRange) {
		return CleaningThresholdItemView.builder()
			.code(code)
			.labelZh(labelZh)
			.defaultValue(defaultValue)
			.description(description)
			.recommendedRange(recommendedRange)
			.build();
	}

}
