package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyRule;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.service.CleaningPolicyResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CleaningPolicyResolverOrderTest {

	@Test
	public void shouldKeepReturnedPolicyRuleOrderWhenBuildingSnapshotRules() {
		CleaningPolicyMapper policyMapper = (CleaningPolicyMapper) Proxy.newProxyInstance(
				CleaningPolicyMapper.class.getClassLoader(), new Class[] { CleaningPolicyMapper.class },
				(proxy, method, args) -> {
					if ("selectById".equals(method.getName())) {
						return CleaningPolicy.builder()
							.id(1L)
							.name("p1")
							.enabled(1)
							.defaultAction("DETECT_ONLY")
							.configJson("{}")
							.createdTime(LocalDateTime.now())
							.updatedTime(LocalDateTime.now())
							.build();
					}
					return null;
				});

		CleaningPolicyRuleMapper policyRuleMapper = (CleaningPolicyRuleMapper) Proxy.newProxyInstance(
				CleaningPolicyRuleMapper.class.getClassLoader(), new Class[] { CleaningPolicyRuleMapper.class },
				(proxy, method, args) -> {
					if ("selectList".equals(method.getName())) {
						return List.of(CleaningPolicyRule.builder().policyId(1L).ruleId(100L).priority(100).build(),
								CleaningPolicyRule.builder().policyId(1L).ruleId(50L).priority(50).build(),
								CleaningPolicyRule.builder().policyId(1L).ruleId(10L).priority(10).build());
					}
					return null;
				});

		CleaningRuleMapper ruleMapper = (CleaningRuleMapper) Proxy.newProxyInstance(
				CleaningRuleMapper.class.getClassLoader(), new Class[] { CleaningRuleMapper.class },
				(proxy, method, args) -> {
					if ("selectList".equals(method.getName())) {
						return List.of(CleaningRule.builder().id(10L).enabled(1).ruleType("LLM").name("r10").build(),
								CleaningRule.builder().id(50L).enabled(1).ruleType("LLM").name("r50").build(),
								CleaningRule.builder().id(100L).enabled(1).ruleType("LLM").name("r100").build());
					}
					return null;
				});

		CleaningPolicyResolver resolver = new CleaningPolicyResolver(policyMapper, policyRuleMapper, ruleMapper);

		var snapshot = resolver.resolveSnapshot(1L);
		assertEquals(List.of(100L, 50L, 10L), snapshot.getRules().stream().map(CleaningRule::getId).toList());
	}

}
