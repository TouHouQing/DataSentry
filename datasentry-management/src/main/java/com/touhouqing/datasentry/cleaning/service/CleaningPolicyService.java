package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRuleItem;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRuleUpdateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyView;
import com.touhouqing.datasentry.cleaning.dto.CleaningRuleRequest;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyRule;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleaningPolicyService {

	private final CleaningPolicyMapper policyMapper;

	private final CleaningRuleMapper ruleMapper;

	private final CleaningPolicyRuleMapper policyRuleMapper;

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
			.name(required(request.getName(), "Policy name is required"))
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
			throw new InvalidInputException("Policy not found");
		}
		LambdaUpdateWrapper<CleaningPolicy> wrapper = new LambdaUpdateWrapper<CleaningPolicy>()
			.eq(CleaningPolicy::getId, policyId)
			.set(CleaningPolicy::getName, required(request.getName(), "Policy name is required"))
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
		LocalDateTime now = LocalDateTime.now();
		CleaningRule rule = CleaningRule.builder()
			.name(required(request.getName(), "Rule name is required"))
			.ruleType(required(request.getRuleType(), "Rule type is required"))
			.category(required(request.getCategory(), "Rule category is required"))
			.severity(request.getSeverity() != null ? request.getSeverity() : 0.8)
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
			throw new InvalidInputException("Rule not found");
		}
		LambdaUpdateWrapper<CleaningRule> wrapper = new LambdaUpdateWrapper<CleaningRule>()
			.eq(CleaningRule::getId, ruleId)
			.set(CleaningRule::getName, required(request.getName(), "Rule name is required"))
			.set(CleaningRule::getRuleType, required(request.getRuleType(), "Rule type is required"))
			.set(CleaningRule::getCategory, required(request.getCategory(), "Rule category is required"))
			.set(CleaningRule::getSeverity, request.getSeverity() != null ? request.getSeverity() : rule.getSeverity())
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
			throw new InvalidInputException("Policy not found");
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
					throw new InvalidInputException("Invalid rule binding");
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

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new InvalidInputException(message);
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
			throw new InvalidInputException("Invalid json payload");
		}
	}

}
