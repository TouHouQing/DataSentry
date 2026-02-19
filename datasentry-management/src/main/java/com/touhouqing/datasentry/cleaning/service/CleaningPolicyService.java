package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyPublishRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyExperimentView;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyOfflineRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRollbackVersionRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyTemplateCloneRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyTemplateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyTemplateView;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRuleItem;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRuleUpdateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyVersionView;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyView;
import com.touhouqing.datasentry.cleaning.dto.CleaningRuleRequest;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyReleaseTicketMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyTemplateMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyVersionMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyReleaseTicket;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyRule;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyTemplate;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.RegexRuleConfig;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CleaningPolicyService {

	private static final Set<String> SUPPORTED_RULE_TYPES = Set.of("REGEX", "L2_DUMMY", "LLM");

	private static final Set<String> REGEX_ALLOWED_FLAGS = Set.of("CASE_INSENSITIVE", "MULTILINE", "DOTALL");

	private static final Set<String> REGEX_ALLOWED_MASK_MODES = Set.of(RegexRuleConfig.MASK_MODE_PLACEHOLDER,
			RegexRuleConfig.MASK_MODE_DELETE);

	private static final String PUBLISH_MODE_FULL = "FULL";

	private static final String PUBLISH_MODE_GRAY = "GRAY";

	private static final int DEFAULT_EXPERIMENT_LIMIT = 20;

	private static final int MAX_EXPERIMENT_LIMIT = 200;

	private final CleaningPolicyMapper policyMapper;

	private final CleaningRuleMapper ruleMapper;

	private final CleaningPolicyRuleMapper policyRuleMapper;

	private final CleaningPolicyVersionMapper policyVersionMapper;

	private final CleaningPolicyReleaseTicketMapper releaseTicketMapper;

	private final CleaningPolicyTemplateMapper policyTemplateMapper;

	private final DataSentryProperties dataSentryProperties;

	public CleaningPolicyService(CleaningPolicyMapper policyMapper, CleaningRuleMapper ruleMapper,
			CleaningPolicyRuleMapper policyRuleMapper) {
		this.policyMapper = policyMapper;
		this.ruleMapper = ruleMapper;
		this.policyRuleMapper = policyRuleMapper;
		this.policyVersionMapper = null;
		this.releaseTicketMapper = null;
		this.policyTemplateMapper = null;
		this.dataSentryProperties = new DataSentryProperties();
	}

	public List<CleaningPolicyView> listPolicies() {
		List<CleaningPolicy> policies = policyMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicy>().orderByDesc(CleaningPolicy::getId));
		if (policies.isEmpty()) {
			return List.of();
		}
		List<Long> policyIds = policies.stream().map(CleaningPolicy::getId).collect(Collectors.toList());
		List<CleaningPolicyRule> policyRules = policyRuleMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyRule>().in(CleaningPolicyRule::getPolicyId, policyIds));
		Map<Long, List<CleaningPolicyRuleItem>> ruleMap = new LinkedHashMap<>();
		for (CleaningPolicyRule policyRule : policyRules) {
			ruleMap.computeIfAbsent(policyRule.getPolicyId(), key -> new ArrayList<>())
				.add(CleaningPolicyRuleItem.builder()
					.ruleId(policyRule.getRuleId())
					.priority(policyRule.getPriority())
					.build());
		}
		return policies.stream()
			.map(policy -> CleaningPolicyView.builder()
				.id(policy.getId())
				.name(policy.getName())
				.description(policy.getDescription())
				.enabled(policy.getEnabled())
				.defaultAction(policy.getDefaultAction())
				.configJson(policy.getConfigJson())
				.createdTime(policy.getCreatedTime())
				.updatedTime(policy.getUpdatedTime())
				.rules(ruleMap.getOrDefault(policy.getId(), List.of()))
				.build())
			.collect(Collectors.toList());
	}

	public CleaningPolicy createPolicy(CleaningPolicyRequest request) {
		LocalDateTime now = LocalDateTime.now();
		CleaningPolicy policy = CleaningPolicy.builder()
			.name(required(request.getName(), "策略名称不能为空"))
			.description(request.getDescription())
			.enabled(request.getEnabled() != null ? request.getEnabled() : 1)
			.defaultAction(request.getDefaultAction() != null ? request.getDefaultAction() : "DETECT_ONLY")
			.configJson(toJson(request.getConfig()))
			.createdTime(now)
			.updatedTime(now)
			.build();
		policyMapper.insert(policy);
		return policy;
	}

	public CleaningPolicy updatePolicy(Long policyId, CleaningPolicyRequest request) {
		CleaningPolicy policy = policyMapper.selectById(policyId);
		if (policy == null) {
			throw new InvalidInputException("策略不存在");
		}
		LambdaUpdateWrapper<CleaningPolicy> wrapper = new LambdaUpdateWrapper<CleaningPolicy>()
			.eq(CleaningPolicy::getId, policyId)
			.set(CleaningPolicy::getName, required(request.getName(), "策略名称不能为空"))
			.set(CleaningPolicy::getDescription, request.getDescription())
			.set(CleaningPolicy::getEnabled, request.getEnabled() != null ? request.getEnabled() : policy.getEnabled())
			.set(CleaningPolicy::getDefaultAction,
					request.getDefaultAction() != null ? request.getDefaultAction() : policy.getDefaultAction())
			.set(CleaningPolicy::getConfigJson, toJson(request.getConfig()))
			.set(CleaningPolicy::getUpdatedTime, LocalDateTime.now());
		policyMapper.update(null, wrapper);
		return policyMapper.selectById(policyId);
	}

	public void deletePolicy(Long policyId) {
		policyMapper.deleteById(policyId);
	}

	public List<CleaningRule> listRules() {
		return ruleMapper.selectList(new LambdaQueryWrapper<CleaningRule>().orderByDesc(CleaningRule::getId));
	}

	public CleaningRule createRule(CleaningRuleRequest request) {
		String ruleType = required(request.getRuleType(), "规则类型不能为空");
		validateRuleType(ruleType);
		validateRuleConfig(ruleType, request.getConfig());
		double severity = normalizedSeverity(request.getSeverity());
		LocalDateTime now = LocalDateTime.now();
		CleaningRule rule = CleaningRule.builder()
			.name(required(request.getName(), "规则名称不能为空"))
			.ruleType(ruleType)
			.category(required(request.getCategory(), "规则类别不能为空"))
			.severity(severity)
			.enabled(request.getEnabled() != null ? request.getEnabled() : 1)
			.configJson(toJson(request.getConfig()))
			.createdTime(now)
			.updatedTime(now)
			.build();
		ruleMapper.insert(rule);
		return rule;
	}

	public CleaningRule updateRule(Long ruleId, CleaningRuleRequest request) {
		CleaningRule rule = ruleMapper.selectById(ruleId);
		if (rule == null) {
			throw new InvalidInputException("规则不存在");
		}
		String ruleType = required(request.getRuleType(), "规则类型不能为空");
		validateRuleType(ruleType);
		validateRuleConfig(ruleType, request.getConfig());
		double severity = normalizedSeverity(
				request.getSeverity() != null ? request.getSeverity() : rule.getSeverity());
		LambdaUpdateWrapper<CleaningRule> wrapper = new LambdaUpdateWrapper<CleaningRule>()
			.eq(CleaningRule::getId, ruleId)
			.set(CleaningRule::getName, required(request.getName(), "规则名称不能为空"))
			.set(CleaningRule::getRuleType, ruleType)
			.set(CleaningRule::getCategory, required(request.getCategory(), "规则类别不能为空"))
			.set(CleaningRule::getSeverity, severity)
			.set(CleaningRule::getEnabled, request.getEnabled() != null ? request.getEnabled() : rule.getEnabled())
			.set(CleaningRule::getConfigJson, toJson(request.getConfig()))
			.set(CleaningRule::getUpdatedTime, LocalDateTime.now());
		ruleMapper.update(null, wrapper);
		return ruleMapper.selectById(ruleId);
	}

	public void deleteRule(Long ruleId) {
		ruleMapper.deleteById(ruleId);
	}

	public void updatePolicyRules(Long policyId, CleaningPolicyRuleUpdateRequest request) {
		if (policyMapper.selectById(policyId) == null) {
			throw new InvalidInputException("策略不存在");
		}
		List<CleaningPolicyRuleItem> items = request.getRules() != null ? request.getRules() : List.of();
		if (!items.isEmpty()) {
			List<Long> ruleIds = items.stream()
				.map(CleaningPolicyRuleItem::getRuleId)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
			if (!ruleIds.isEmpty()) {
				List<CleaningRule> rules = ruleMapper
					.selectList(new LambdaQueryWrapper<CleaningRule>().in(CleaningRule::getId, ruleIds));
				if (rules.size() != ruleIds.size()) {
					throw new InvalidInputException("规则绑定中包含不存在的规则");
				}
			}
		}
		policyRuleMapper
			.delete(new LambdaQueryWrapper<CleaningPolicyRule>().eq(CleaningPolicyRule::getPolicyId, policyId));
		for (CleaningPolicyRuleItem item : items) {
			if (item.getRuleId() == null) {
				continue;
			}
			policyRuleMapper.insert(CleaningPolicyRule.builder()
				.policyId(policyId)
				.ruleId(item.getRuleId())
				.priority(item.getPriority() != null ? item.getPriority() : 0)
				.build());
		}
	}

	public List<CleaningPolicyTemplateView> listPolicyTemplates() {
		ensureTemplateDependency();
		return policyTemplateMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyTemplate>().orderByDesc(CleaningPolicyTemplate::getId))
			.stream()
			.map(this::toTemplateView)
			.toList();
	}

	public CleaningPolicyTemplateView createPolicyTemplate(CleaningPolicyTemplateRequest request) {
		ensureTemplateDependency();
		LocalDateTime now = LocalDateTime.now();
		CleaningPolicyTemplate template = CleaningPolicyTemplate.builder()
			.name(required(request.getName(), "模板名称不能为空"))
			.description(request.getDescription())
			.category(required(request.getCategory(), "模板分类不能为空"))
			.enabled(request.getEnabled() != null ? request.getEnabled() : 1)
			.defaultAction(request.getDefaultAction() != null ? request.getDefaultAction() : "DETECT_ONLY")
			.configJson(toJson(request.getConfig()))
			.rulesJson(toJson(normalizeTemplateRules(request.getRules())))
			.createdTime(now)
			.updatedTime(now)
			.build();
		policyTemplateMapper.insert(template);
		return toTemplateView(template);
	}

	public CleaningPolicyTemplateView updatePolicyTemplate(Long templateId, CleaningPolicyTemplateRequest request) {
		ensureTemplateDependency();
		CleaningPolicyTemplate existing = policyTemplateMapper.selectById(templateId);
		if (existing == null) {
			throw new InvalidInputException("模板不存在");
		}
		policyTemplateMapper.update(null,
				new LambdaUpdateWrapper<CleaningPolicyTemplate>().eq(CleaningPolicyTemplate::getId, templateId)
					.set(CleaningPolicyTemplate::getName, required(request.getName(), "模板名称不能为空"))
					.set(CleaningPolicyTemplate::getDescription, request.getDescription())
					.set(CleaningPolicyTemplate::getCategory, required(request.getCategory(), "模板分类不能为空"))
					.set(CleaningPolicyTemplate::getEnabled, request.getEnabled() != null ? request.getEnabled() : 1)
					.set(CleaningPolicyTemplate::getDefaultAction,
							request.getDefaultAction() != null ? request.getDefaultAction() : "DETECT_ONLY")
					.set(CleaningPolicyTemplate::getConfigJson, toJson(request.getConfig()))
					.set(CleaningPolicyTemplate::getRulesJson, toJson(normalizeTemplateRules(request.getRules())))
					.set(CleaningPolicyTemplate::getUpdatedTime, LocalDateTime.now()));
		return toTemplateView(policyTemplateMapper.selectById(templateId));
	}

	public void deletePolicyTemplate(Long templateId) {
		ensureTemplateDependency();
		policyTemplateMapper.deleteById(templateId);
	}

	@Transactional(rollbackFor = Exception.class)
	public CleaningPolicyView clonePolicyTemplate(Long templateId, CleaningPolicyTemplateCloneRequest request) {
		ensureTemplateDependency();
		CleaningPolicyTemplate template = policyTemplateMapper.selectById(templateId);
		if (template == null) {
			throw new InvalidInputException("模板不存在");
		}
		LocalDateTime now = LocalDateTime.now();
		CleaningPolicy policy = CleaningPolicy.builder()
			.name(required(request != null ? request.getName() : null, "克隆后的策略名称不能为空"))
			.description(request != null && request.getDescription() != null ? request.getDescription()
					: template.getDescription())
			.enabled(request != null && request.getEnabled() != null ? request.getEnabled()
					: (template.getEnabled() != null ? template.getEnabled() : 1))
			.defaultAction(template.getDefaultAction() != null ? template.getDefaultAction() : "DETECT_ONLY")
			.configJson(template.getConfigJson())
			.createdTime(now)
			.updatedTime(now)
			.build();
		policyMapper.insert(policy);
		List<CleaningPolicyRuleItem> rules = parseTemplateRules(template.getRulesJson());
		updatePolicyRules(policy.getId(), CleaningPolicyRuleUpdateRequest.builder().rules(rules).build());
		return CleaningPolicyView.builder()
			.id(policy.getId())
			.name(policy.getName())
			.description(policy.getDescription())
			.enabled(policy.getEnabled())
			.defaultAction(policy.getDefaultAction())
			.configJson(policy.getConfigJson())
			.createdTime(policy.getCreatedTime())
			.updatedTime(policy.getUpdatedTime())
			.rules(rules)
			.build();
	}

	public List<CleaningPolicyVersionView> listPolicyVersions(Long policyId) {
		ensureGovernanceDependencies();
		if (policyMapper.selectById(policyId) == null) {
			throw new InvalidInputException("策略不存在");
		}
		return policyVersionMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
				.orderByDesc(CleaningPolicyVersion::getVersionNo))
			.stream()
			.map(this::toVersionView)
			.toList();
	}

	public CleaningPolicyVersion findPublishedVersion(Long policyId) {
		if (policyVersionMapper == null) {
			return null;
		}
		if (policyId == null) {
			return null;
		}
		return policyVersionMapper.findPublished(policyId);
	}

	@Transactional(rollbackFor = Exception.class)
	public CleaningPolicyVersionView publishPolicy(Long policyId, CleaningPolicyPublishRequest request) {
		ensureGovernanceDependencies();
		ensurePolicyGovernanceEnabled();
		CleaningPolicy policy = policyMapper.selectById(policyId);
		if (policy == null) {
			throw new InvalidInputException("策略不存在");
		}
		if (policy.getEnabled() == null || policy.getEnabled() != 1) {
			throw new InvalidInputException("策略未启用，无法发布");
		}
		String publishMode = resolvePublishMode(request != null ? request.getPublishMode() : null);
		BigDecimal grayRatio = resolveGrayRatio(publishMode, request != null ? request.getGrayRatio() : null);
		LocalDateTime now = LocalDateTime.now();
		if (PUBLISH_MODE_FULL.equals(publishMode)) {
			policyVersionMapper.demotePublished(policyId);
		}
		CleaningPolicyVersion version = CleaningPolicyVersion.builder()
			.policyId(policyId)
			.versionNo(policyVersionMapper.nextVersionNo(policyId))
			.status(PUBLISH_MODE_FULL.equals(publishMode) ? "PUBLISHED" : "GRAY")
			.configJson(buildPolicySnapshotJson(policy))
			.defaultAction(policy.getDefaultAction())
			.createdTime(now)
			.updatedTime(now)
			.build();
		policyVersionMapper.insert(version);
		releaseTicketMapper.insert(CleaningPolicyReleaseTicket.builder()
			.policyId(policyId)
			.versionId(version.getId())
			.action(PUBLISH_MODE_FULL.equals(publishMode) ? "PUBLISH" : "PUBLISH_GRAY")
			.publishMode(publishMode)
			.grayRatio(grayRatio)
			.experimentName(resolveExperimentName(request != null ? request.getExperimentName() : null))
			.note(request != null ? request.getNote() : null)
			.operator(request != null ? request.getOperator() : null)
			.createdTime(now)
			.build());
		return toVersionView(version);
	}

	@Transactional(rollbackFor = Exception.class)
	public CleaningPolicyVersionView rollbackToVersion(Long policyId, CleaningPolicyRollbackVersionRequest request) {
		ensureGovernanceDependencies();
		ensurePolicyGovernanceEnabled();
		if (request == null || request.getVersionId() == null) {
			throw new InvalidInputException("versionId 不能为空");
		}
		if (policyMapper.selectById(policyId) == null) {
			throw new InvalidInputException("策略不存在");
		}
		CleaningPolicyVersion target = policyVersionMapper
			.selectOne(new LambdaQueryWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
				.eq(CleaningPolicyVersion::getId, request.getVersionId())
				.last("LIMIT 1"));
		if (target == null) {
			throw new InvalidInputException("目标策略版本不存在");
		}
		LocalDateTime now = LocalDateTime.now();
		policyVersionMapper.demotePublished(policyId);
		policyVersionMapper.update(null,
				new LambdaUpdateWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getId, target.getId())
					.set(CleaningPolicyVersion::getStatus, "PUBLISHED")
					.set(CleaningPolicyVersion::getUpdatedTime, now));
		target = policyVersionMapper.selectById(target.getId());
		releaseTicketMapper.insert(CleaningPolicyReleaseTicket.builder()
			.policyId(policyId)
			.versionId(target.getId())
			.action("ROLLBACK")
			.publishMode(PUBLISH_MODE_FULL)
			.note(request.getNote())
			.operator(request.getOperator())
			.createdTime(now)
			.build());
		return toVersionView(target);
	}

	@Transactional(rollbackFor = Exception.class)
	public void offlinePolicy(Long policyId, CleaningPolicyOfflineRequest request) {
		ensureGovernanceDependencies();
		ensurePolicyGovernanceEnabled();
		if (policyId == null || policyMapper.selectById(policyId) == null) {
			throw new InvalidInputException("策略不存在");
		}
		LocalDateTime now = LocalDateTime.now();
		List<CleaningPolicyVersion> activeVersions = policyVersionMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
				.in(CleaningPolicyVersion::getStatus, "PUBLISHED", "GRAY"));
		if (activeVersions.isEmpty()) {
			throw new InvalidInputException("策略当前没有可下线的发布版本");
		}
		policyVersionMapper.update(null,
				new LambdaUpdateWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
					.in(CleaningPolicyVersion::getStatus, "PUBLISHED", "GRAY")
					.set(CleaningPolicyVersion::getStatus, "OFFLINE")
					.set(CleaningPolicyVersion::getUpdatedTime, now));
		releaseTicketMapper.insert(CleaningPolicyReleaseTicket.builder()
			.policyId(policyId)
			.versionId(activeVersions.get(0).getId())
			.action("OFFLINE")
			.publishMode(PUBLISH_MODE_FULL)
			.note(request != null ? request.getNote() : null)
			.operator(request != null ? request.getOperator() : null)
			.createdTime(now)
			.build());
	}

	public List<CleaningPolicyExperimentView> listPolicyExperiments(Long policyId, Integer limit) {
		ensureGovernanceDependencies();
		if (policyId == null || policyMapper.selectById(policyId) == null) {
			throw new InvalidInputException("策略不存在");
		}
		int safeLimit = resolveExperimentLimit(limit);
		return releaseTicketMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyReleaseTicket>()
				.eq(CleaningPolicyReleaseTicket::getPolicyId, policyId)
				.in(CleaningPolicyReleaseTicket::getAction, "PUBLISH", "PUBLISH_GRAY", "ROLLBACK", "OFFLINE")
				.orderByDesc(CleaningPolicyReleaseTicket::getId)
				.last("LIMIT " + safeLimit))
			.stream()
			.map(ticket -> CleaningPolicyExperimentView.builder()
				.ticketId(ticket.getId())
				.policyId(ticket.getPolicyId())
				.versionId(ticket.getVersionId())
				.action(ticket.getAction())
				.publishMode(ticket.getPublishMode())
				.grayRatio(ticket.getGrayRatio())
				.experimentName(ticket.getExperimentName())
				.note(ticket.getNote())
				.operator(ticket.getOperator())
				.createdTime(ticket.getCreatedTime())
				.build())
			.toList();
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new InvalidInputException(message);
		}
		return value;
	}

	private List<CleaningPolicyRuleItem> normalizeTemplateRules(List<CleaningPolicyRuleItem> rules) {
		if (rules == null || rules.isEmpty()) {
			return List.of();
		}
		return rules.stream()
			.filter(item -> item != null && item.getRuleId() != null)
			.map(item -> CleaningPolicyRuleItem.builder()
				.ruleId(item.getRuleId())
				.priority(item.getPriority() != null ? item.getPriority() : 0)
				.build())
			.toList();
	}

	private List<CleaningPolicyRuleItem> parseTemplateRules(String rulesJson) {
		if (rulesJson == null || rulesJson.isBlank()) {
			return List.of();
		}
		try {
			List<?> raw = JsonUtil.getObjectMapper().readValue(rulesJson, List.class);
			if (raw == null || raw.isEmpty()) {
				return List.of();
			}
			List<CleaningPolicyRuleItem> result = new ArrayList<>();
			for (Object item : raw) {
				if (!(item instanceof Map<?, ?> map)) {
					continue;
				}
				Object ruleIdRaw = map.get("ruleId");
				if (ruleIdRaw == null) {
					continue;
				}
				long ruleId = Long.parseLong(String.valueOf(ruleIdRaw));
				if (ruleId <= 0) {
					continue;
				}
				Object priorityRaw = map.get("priority");
				int priority = priorityRaw != null ? Integer.parseInt(String.valueOf(priorityRaw)) : 0;
				result.add(CleaningPolicyRuleItem.builder().ruleId(ruleId).priority(priority).build());
			}
			return result;
		}
		catch (Exception e) {
			return List.of();
		}
	}

	private CleaningPolicyTemplateView toTemplateView(CleaningPolicyTemplate template) {
		if (template == null) {
			return null;
		}
		return CleaningPolicyTemplateView.builder()
			.id(template.getId())
			.name(template.getName())
			.description(template.getDescription())
			.category(template.getCategory())
			.enabled(template.getEnabled())
			.defaultAction(template.getDefaultAction())
			.configJson(template.getConfigJson())
			.rules(parseTemplateRules(template.getRulesJson()))
			.createdTime(template.getCreatedTime())
			.updatedTime(template.getUpdatedTime())
			.build();
	}

	private void ensurePolicyGovernanceEnabled() {
		if (!dataSentryProperties.getCleaning().isPolicyGovernanceEnabled()) {
			throw new InvalidInputException("策略治理能力未开启");
		}
	}

	private void ensureGovernanceDependencies() {
		if (policyVersionMapper == null || releaseTicketMapper == null) {
			throw new InvalidInputException("策略治理依赖未初始化");
		}
	}

	private void ensureTemplateDependency() {
		if (policyTemplateMapper == null) {
			throw new InvalidInputException("策略模板依赖未初始化");
		}
	}

	private CleaningPolicyVersionView toVersionView(CleaningPolicyVersion version) {
		if (version == null) {
			return null;
		}
		return CleaningPolicyVersionView.builder()
			.id(version.getId())
			.policyId(version.getPolicyId())
			.versionNo(version.getVersionNo())
			.status(version.getStatus())
			.defaultAction(version.getDefaultAction())
			.createdTime(version.getCreatedTime())
			.updatedTime(version.getUpdatedTime())
			.build();
	}

	private String buildPolicySnapshotJson(CleaningPolicy policy) {
		List<CleaningPolicyRule> policyRules = policyRuleMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyRule>().eq(CleaningPolicyRule::getPolicyId, policy.getId())
				.orderByDesc(CleaningPolicyRule::getPriority)
				.orderByAsc(CleaningPolicyRule::getRuleId));
		List<Map<String, Object>> ruleItems = new ArrayList<>();
		for (CleaningPolicyRule rule : policyRules) {
			ruleItems.add(Map.of("ruleId", rule.getRuleId(), "priority", rule.getPriority()));
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("policyName", policy.getName());
		snapshot.put("policyDescription", policy.getDescription());
		snapshot.put("policyConfigJson", policy.getConfigJson());
		snapshot.put("rules", ruleItems);
		return toJson(snapshot);
	}

	private void validateRuleType(String ruleType) {
		if (!SUPPORTED_RULE_TYPES.contains(ruleType)) {
			throw new InvalidInputException("规则类型不支持: " + ruleType);
		}
	}

	private void validateRuleConfig(String ruleType, Map<String, Object> config) {
		if (!"REGEX".equals(ruleType)) {
			return;
		}
		Map<String, Object> effectiveConfig = config != null ? config : Map.of();
		String pattern = asTrimmedString(effectiveConfig.get("pattern"));
		if (pattern == null || pattern.isEmpty()) {
			throw new InvalidInputException("REGEX 规则缺少 pattern，请填写正则表达式");
		}
		String maskMode = asTrimmedString(effectiveConfig.get("maskMode"));
		if (maskMode != null && !REGEX_ALLOWED_MASK_MODES.contains(maskMode.toUpperCase())) {
			throw new InvalidInputException("REGEX maskMode 不支持: " + maskMode + "，仅支持 PLACEHOLDER/DELETE");
		}
		String maskText = asTrimmedString(effectiveConfig.get("maskText"));
		if (maskMode != null && RegexRuleConfig.MASK_MODE_DELETE.equalsIgnoreCase(maskMode) && maskText != null) {
			throw new InvalidInputException("REGEX maskMode=DELETE 时不应填写 maskText");
		}
		Object flagsValue = effectiveConfig.get("flags");
		if (flagsValue == null) {
			return;
		}
		for (String flag : normalizedFlags(flagsValue)) {
			if (!REGEX_ALLOWED_FLAGS.contains(flag)) {
				throw new InvalidInputException("REGEX flags 不支持: " + flag + "，仅支持 CASE_INSENSITIVE/MULTILINE/DOTALL");
			}
		}
	}

	private List<String> normalizedFlags(Object value) {
		if (value instanceof String flagText) {
			if (flagText.isBlank()) {
				return List.of();
			}
			return Arrays.stream(flagText.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
		}
		if (value instanceof List<?> flagList) {
			return flagList.stream()
				.filter(Objects::nonNull)
				.map(String::valueOf)
				.map(String::trim)
				.filter(item -> !item.isEmpty())
				.toList();
		}
		throw new InvalidInputException("REGEX flags 必须是字符串或字符串数组");
	}

	private String asTrimmedString(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() ? null : text;
	}

	private double normalizedSeverity(Double severity) {
		double value = severity != null ? severity : 0.8;
		if (value < 0 || value > 1) {
			throw new InvalidInputException("严重度必须在 0 到 1 之间");
		}
		return value;
	}

	private String toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			throw new InvalidInputException("JSON 配置格式非法");
		}
	}

	private String resolvePublishMode(String publishMode) {
		if (publishMode == null || publishMode.isBlank()) {
			return PUBLISH_MODE_FULL;
		}
		String normalized = publishMode.trim().toUpperCase();
		if (!PUBLISH_MODE_FULL.equals(normalized) && !PUBLISH_MODE_GRAY.equals(normalized)) {
			throw new InvalidInputException("publishMode 仅支持 FULL / GRAY");
		}
		return normalized;
	}

	private BigDecimal resolveGrayRatio(String publishMode, BigDecimal grayRatio) {
		if (PUBLISH_MODE_FULL.equals(publishMode)) {
			return null;
		}
		if (grayRatio == null) {
			throw new InvalidInputException("GRAY 发布必须提供 grayRatio");
		}
		if (grayRatio.compareTo(BigDecimal.ZERO) <= 0 || grayRatio.compareTo(BigDecimal.ONE) > 0) {
			throw new InvalidInputException("grayRatio 取值范围为 (0, 1]");
		}
		return grayRatio;
	}

	private String resolveExperimentName(String experimentName) {
		if (experimentName == null || experimentName.isBlank()) {
			return null;
		}
		String trimmed = experimentName.trim();
		return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
	}

	private int resolveExperimentLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_EXPERIMENT_LIMIT;
		}
		return Math.min(limit, MAX_EXPERIMENT_LIMIT);
	}

}
