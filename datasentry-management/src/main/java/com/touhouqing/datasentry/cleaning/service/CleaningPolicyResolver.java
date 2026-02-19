package com.touhouqing.datasentry.cleaning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyReleaseTicketMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyVersionMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyReleaseTicket;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyRule;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CleaningPolicyResolver {

	private final CleaningPolicyMapper policyMapper;

	private final CleaningPolicyRuleMapper policyRuleMapper;

	private final CleaningRuleMapper ruleMapper;

	private final CleaningPolicyVersionMapper policyVersionMapper;

	private final CleaningPolicyReleaseTicketMapper releaseTicketMapper;

	private final DataSentryProperties dataSentryProperties;

	public CleaningPolicyResolver(CleaningPolicyMapper policyMapper, CleaningPolicyRuleMapper policyRuleMapper,
			CleaningRuleMapper ruleMapper, CleaningPolicyVersionMapper policyVersionMapper,
			CleaningPolicyReleaseTicketMapper releaseTicketMapper, DataSentryProperties dataSentryProperties) {
		this.policyMapper = policyMapper;
		this.policyRuleMapper = policyRuleMapper;
		this.ruleMapper = ruleMapper;
		this.policyVersionMapper = policyVersionMapper;
		this.releaseTicketMapper = releaseTicketMapper;
		this.dataSentryProperties = dataSentryProperties;
	}

	public CleaningPolicyResolver(CleaningPolicyMapper policyMapper, CleaningPolicyRuleMapper policyRuleMapper,
			CleaningRuleMapper ruleMapper) {
		this(policyMapper, policyRuleMapper, ruleMapper, null, null, new DataSentryProperties());
	}

	public CleaningPolicySnapshot resolveSnapshot(Long policyId) {
		return resolveSnapshot(policyId, null);
	}

	public CleaningPolicySnapshot resolveSnapshot(Long policyId, String routeKey) {
		CleaningPolicyVersion resolvedVersion = resolveEffectiveVersion(policyId, routeKey);
		if (resolvedVersion != null) {
			return resolveSnapshotFromVersion(policyId, resolvedVersion);
		}
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
			.policyVersionId(null)
			.policyVersionNo(null)
			.policyName(policy.getName())
			.defaultAction(policy.getDefaultAction())
			.config(config)
			.rules(rules)
			.build();
	}

	private CleaningPolicySnapshot resolveSnapshotFromVersion(Long policyId, CleaningPolicyVersion version) {
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
			rules = policyRules.stream().map(rule -> ruleMap.get(rule.getRuleId())).filter(Objects::nonNull).toList();
		}
		CleaningPolicyConfig config = parseVersionConfig(version.getConfigJson(), policy.getConfigJson());
		String defaultAction = version.getDefaultAction() != null ? version.getDefaultAction()
				: policy.getDefaultAction();
		return CleaningPolicySnapshot.builder()
			.policyId(policy.getId())
			.policyVersionId(version.getId())
			.policyVersionNo(version.getVersionNo())
			.policyName(policy.getName())
			.defaultAction(defaultAction)
			.config(config)
			.rules(rules)
			.build();
	}

	private CleaningPolicyVersion resolveEffectiveVersion(Long policyId, String routeKey) {
		if (policyVersionMapper == null || dataSentryProperties == null) {
			return null;
		}
		if (!dataSentryProperties.getCleaning().isPolicyGovernanceEnabled()) {
			return null;
		}
		CleaningPolicyVersion publishedVersion = policyVersionMapper.findPublished(policyId);
		CleaningPolicyVersion grayVersion = policyVersionMapper.findLatestGray(policyId);
		if (grayVersion == null) {
			return publishedVersion;
		}
		if (publishedVersion == null) {
			return grayVersion;
		}
		BigDecimal grayRatio = resolveGrayRatio(policyId, grayVersion.getId());
		if (grayRatio.compareTo(BigDecimal.ZERO) <= 0) {
			return publishedVersion;
		}
		return shouldRouteToGray(policyId, routeKey, grayRatio) ? grayVersion : publishedVersion;
	}

	private BigDecimal resolveGrayRatio(Long policyId, Long versionId) {
		if (releaseTicketMapper == null || versionId == null) {
			return BigDecimal.ZERO;
		}
		CleaningPolicyReleaseTicket ticket = releaseTicketMapper.findLatestGrayTicket(policyId, versionId);
		if (ticket == null || ticket.getGrayRatio() == null) {
			return BigDecimal.ZERO;
		}
		return ticket.getGrayRatio();
	}

	private boolean shouldRouteToGray(Long policyId, String routeKey, BigDecimal grayRatio) {
		if (routeKey == null || routeKey.isBlank()) {
			return false;
		}
		int threshold = grayRatio.multiply(BigDecimal.valueOf(10000)).setScale(0, RoundingMode.HALF_UP).intValue();
		if (threshold <= 0) {
			return false;
		}
		if (threshold >= 10000) {
			return true;
		}
		int bucket = Math.floorMod(Objects.hash(policyId, routeKey), 10000);
		return bucket < threshold;
	}

	private CleaningPolicyConfig parseVersionConfig(String versionConfigJson, String fallbackConfigJson) {
		if (versionConfigJson != null && !versionConfigJson.isBlank()) {
			try {
				Map<String, Object> parsed = JsonUtil.getObjectMapper().readValue(versionConfigJson, Map.class);
				Object policyConfigJson = parsed.get("policyConfigJson");
				if (policyConfigJson instanceof String configText && !configText.isBlank()) {
					return parsePolicyConfig(configText);
				}
			}
			catch (Exception e) {
				log.warn("Failed to parse cleaning policy version config", e);
			}
		}
		return parsePolicyConfig(fallbackConfigJson);
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
