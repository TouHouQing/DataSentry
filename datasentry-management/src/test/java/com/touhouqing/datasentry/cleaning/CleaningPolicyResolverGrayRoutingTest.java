package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyReleaseTicketMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyVersionMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyReleaseTicket;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import com.touhouqing.datasentry.cleaning.service.CleaningPolicyResolver;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningPolicyResolverGrayRoutingTest {

	@Mock
	private CleaningPolicyMapper policyMapper;

	@Mock
	private CleaningPolicyRuleMapper policyRuleMapper;

	@Mock
	private CleaningRuleMapper ruleMapper;

	@Mock
	private CleaningPolicyVersionMapper policyVersionMapper;

	@Mock
	private CleaningPolicyReleaseTicketMapper releaseTicketMapper;

	private CleaningPolicyResolver resolver;

	@BeforeEach
	public void setUp() {
		DataSentryProperties properties = new DataSentryProperties();
		properties.getCleaning().setPolicyGovernanceEnabled(true);
		resolver = new CleaningPolicyResolver(policyMapper, policyRuleMapper, ruleMapper, policyVersionMapper,
				releaseTicketMapper, properties);
		when(policyMapper.selectById(1L)).thenReturn(CleaningPolicy.builder()
			.id(1L)
			.name("p1")
			.enabled(1)
			.defaultAction("DETECT_ONLY")
			.configJson("{}")
			.createdTime(LocalDateTime.now())
			.updatedTime(LocalDateTime.now())
			.build());
		when(policyRuleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
	}

	@Test
	public void shouldRouteToGrayWhenGrayRatioIsOne() {
		when(policyVersionMapper.findPublished(1L)).thenReturn(
				CleaningPolicyVersion.builder().id(11L).policyId(1L).versionNo(1).status("PUBLISHED").build());
		when(policyVersionMapper.findLatestGray(1L))
			.thenReturn(CleaningPolicyVersion.builder().id(12L).policyId(1L).versionNo(2).status("GRAY").build());
		when(releaseTicketMapper.findLatestGrayTicket(1L, 12L))
			.thenReturn(CleaningPolicyReleaseTicket.builder().grayRatio(BigDecimal.ONE).build());

		var snapshot = resolver.resolveSnapshot(1L, "trace-route-key");

		assertEquals(12L, snapshot.getPolicyVersionId());
		assertEquals(2, snapshot.getPolicyVersionNo());
	}

	@Test
	public void shouldFallbackToPublishedWhenRouteKeyMissing() {
		when(policyVersionMapper.findPublished(1L)).thenReturn(
				CleaningPolicyVersion.builder().id(21L).policyId(1L).versionNo(3).status("PUBLISHED").build());
		when(policyVersionMapper.findLatestGray(1L))
			.thenReturn(CleaningPolicyVersion.builder().id(22L).policyId(1L).versionNo(4).status("GRAY").build());
		when(releaseTicketMapper.findLatestGrayTicket(1L, 22L))
			.thenReturn(CleaningPolicyReleaseTicket.builder().grayRatio(BigDecimal.ONE).build());

		var snapshot = resolver.resolveSnapshot(1L, null);

		assertEquals(21L, snapshot.getPolicyVersionId());
		assertEquals(3, snapshot.getPolicyVersionNo());
	}

}
