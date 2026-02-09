package com.touhouqing.datasentry.cleaning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyRule;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningPolicyResolver {

	private final CleaningPolicyMapper policyMapper;

	private final CleaningPolicyRuleMapper policyRuleMapper;

	private final CleaningRuleMapper ruleMapper;

	public CleaningPolicySnapshot resolveSnapshot(Long policyId) {
		CleaningPolicy policy = policyMapper.selectById(policyId);
		if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
			throw new InvalidInputException("清理策略不可用");
		}
		List<CleaningPolicyRule> policyRules = policyRuleMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyRule>().eq(CleaningPolicyRule::getPolicyId, policyId)
				.orderByDesc(CleaningPolicyRule::getPriority)
				.orderByAsc(CleaningPolicyRule::getRuleId));
		List<Long> ruleIds = policyRules.stream()
			.map(CleaningPolicyRule::getRuleId)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		List<CleaningRule> rules = List.of();
		if (!ruleIds.isEmpty()) {
			List<CleaningRule> fetched = ruleMapper
				.selectList(new LambdaQueryWrapper<CleaningRule>().in(CleaningRule::getId, ruleIds)
					.eq(CleaningRule::getEnabled, 1));
			Map<Long, CleaningRule> ruleMap = fetched.stream()
				.collect(Collectors.toMap(CleaningRule::getId, rule -> rule, (first, second) -> first));
			rules = policyRules.stream()
				.map(rule -> ruleMap.get(rule.getRuleId()))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		}
		CleaningPolicyConfig config = parsePolicyConfig(policy.getConfigJson());
		return CleaningPolicySnapshot.builder()
			.policyId(policy.getId())
			.policyName(policy.getName())
			.defaultAction(policy.getDefaultAction())
			.config(config)
			.rules(rules)
			.build();
	}

	private CleaningPolicyConfig parsePolicyConfig(String configJson) {
		if (configJson == null || configJson.isBlank()) {
			return new CleaningPolicyConfig();
		}
		try {
			CleaningPolicyConfig config = JsonUtil.getObjectMapper().readValue(configJson, CleaningPolicyConfig.class);
			return config != null ? config : new CleaningPolicyConfig();
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to parse cleaning policy config", e);
			return new CleaningPolicyConfig();
		}
	}

}
