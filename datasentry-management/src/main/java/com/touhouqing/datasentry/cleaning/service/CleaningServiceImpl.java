package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.dto.CleaningCheckRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningResponse;
import com.touhouqing.datasentry.cleaning.enums.CleaningBindingType;
import com.touhouqing.datasentry.cleaning.mapper.CleaningAllowlistMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBindingMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningAllowlist;
import com.touhouqing.datasentry.cleaning.model.CleaningBinding;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.CleaningPipeline;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningServiceImpl implements CleaningService {

	private final CleaningPolicyMapper policyMapper;

	private final CleaningRuleMapper ruleMapper;

	private final CleaningBindingMapper bindingMapper;

	private final CleaningAllowlistMapper allowlistMapper;

	private final CleaningPipeline pipeline;

	@Override
	public CleaningResponse check(Long agentId, CleaningCheckRequest request, String traceId) {
		return execute(agentId, request, traceId, false);
	}

	@Override
	public CleaningResponse sanitize(Long agentId, CleaningCheckRequest request, String traceId) {
		return execute(agentId, request, traceId, true);
	}

	private CleaningResponse execute(Long agentId, CleaningCheckRequest request, String traceId,
			boolean sanitizeRequested) {
		CleaningPolicySnapshot snapshot = resolvePolicySnapshot(agentId, request);
		List<CleaningAllowlist> allowlists = allowlistMapper.findActive();
		if (allowlists == null) {
			allowlists = List.of();
		}
		CleaningContext context = CleaningContext.builder()
			.agentId(agentId)
			.traceId(traceId)
			.originalText(request.getText())
			.policySnapshot(snapshot)
			.build();
		context.getMetadata().put("scene", request.getScene());
		context.getMetadata().put("allowlists", allowlists);
		context.getMetrics().put("startTimeMs", System.currentTimeMillis());
		CleaningContext result = pipeline.execute(context, sanitizeRequested);
		return CleaningResponse.builder()
			.verdict(result.getVerdict() != null ? result.getVerdict().name() : null)
			.categories(resolveCategories(result.getFindings()))
			.sanitizedText(sanitizeRequested ? result.getSanitizedText() : null)
			.build();
	}

	private CleaningPolicySnapshot resolvePolicySnapshot(Long agentId, CleaningCheckRequest request) {
		Long policyId = request.getPolicyId();
		if (policyId == null) {
			CleaningBinding binding = resolveBinding(agentId, request.getScene());
			policyId = binding.getPolicyId();
		}
		CleaningPolicy policy = policyMapper.findById(policyId);
		if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
			throw new InvalidInputException("清理策略不可用");
		}
		List<CleaningRule> rules = ruleMapper.findByPolicyId(policyId);
		if (rules == null) {
			rules = List.of();
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

	private CleaningBinding resolveBinding(Long agentId, String scene) {
		CleaningBinding binding = null;
		if (scene != null && !scene.isBlank()) {
			binding = bindingMapper.findByAgentAndScene(agentId, CleaningBindingType.ONLINE_TEXT.name(), scene);
		}
		if (binding == null) {
			binding = bindingMapper.findDefaultByAgent(agentId, CleaningBindingType.ONLINE_TEXT.name());
		}
		if (binding == null) {
			throw new InvalidInputException("未找到可用的清理绑定");
		}
		return binding;
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

	private List<String> resolveCategories(List<Finding> findings) {
		if (findings == null || findings.isEmpty()) {
			return List.of();
		}
		Set<String> categories = new LinkedHashSet<>();
		for (Finding finding : findings) {
			if (finding.getCategory() != null) {
				categories.add(finding.getCategory());
			}
		}
		return categories.stream().collect(Collectors.toList());
	}

}
