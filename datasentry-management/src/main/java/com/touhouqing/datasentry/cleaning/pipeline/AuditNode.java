package com.touhouqing.datasentry.cleaning.pipeline;

import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.model.NodeResult;
import com.touhouqing.datasentry.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditNode implements PipelineNode {

	private final CleaningRecordMapper recordMapper;

	@Override
	public NodeResult process(CleaningContext context) {
		try {
			CleaningRecord record = CleaningRecord.builder()
				.agentId(context.getAgentId())
				.traceId(context.getTraceId())
				.policySnapshotJson(toJsonSafe(context.getPolicySnapshot()))
				.verdict(context.getVerdict() != null ? context.getVerdict().name() : null)
				.categoriesJson(toJsonSafe(resolveCategories(context.getFindings())))
				.sanitizedPreview(context.getSanitizedText())
				.metricsJson(toJsonSafe(context.getMetrics()))
				.executionTimeMs(resolveExecutionTime(context))
				.detectorSource(resolveDetectorSource(context.getFindings()))
				.createdTime(LocalDateTime.now())
				.build();
			recordMapper.insert(record);
			return NodeResult.ok();
		}
		catch (Exception e) {
			log.warn("Failed to write cleaning record", e);
			return NodeResult.failed(List.of(e.getMessage()));
		}
	}

	private Long resolveExecutionTime(CleaningContext context) {
		Object start = context.getMetrics().get("startTimeMs");
		if (start instanceof Long) {
			return System.currentTimeMillis() - (Long) start;
		}
		return null;
	}

	private Set<String> resolveCategories(List<Finding> findings) {
		if (findings == null) {
			return Set.of();
		}
		Set<String> categories = new LinkedHashSet<>();
		for (Finding finding : findings) {
			if (finding.getCategory() != null) {
				categories.add(finding.getCategory());
			}
		}
		return categories;
	}

	private String resolveDetectorSource(List<Finding> findings) {
		if (findings == null || findings.isEmpty()) {
			return null;
		}
		return findings.stream()
			.map(Finding::getDetectorSource)
			.filter(source -> source != null && !source.isBlank())
			.distinct()
			.collect(Collectors.joining(","));
	}

	private String toJsonSafe(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to serialize json", e);
			return null;
		}
	}

}
