package com.touhouqing.datasentry.cleaning.pipeline;

import com.touhouqing.datasentry.cleaning.enums.CleaningVerdict;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.model.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DecideNode implements PipelineNode {

	@Override
	public NodeResult process(CleaningContext context) {
		if (isL3AllParseFailed(context)) {
			context.setVerdict(CleaningVerdict.REVIEW);
			log.warn(
					"Cleaning decide runId={} column={} findings={} l3AllParseFailed=true verdict=REVIEW reason=L3_STRUCTURED_OUTPUT_PARSE_FAILED",
					context.getJobRunId(), context.getColumnName(),
					context.getFindings() != null ? context.getFindings().size() : 0);
			return NodeResult.ok();
		}
		if (context.getFindings() == null || context.getFindings().isEmpty()) {
			context.setVerdict(CleaningVerdict.ALLOW);
			log.info("Cleaning decide runId={} column={} findings=0 verdict=ALLOW", context.getJobRunId(),
					context.getColumnName());
			return NodeResult.ok();
		}
		CleaningPolicyConfig config = context.getPolicySnapshot() != null ? context.getPolicySnapshot().getConfig()
				: new CleaningPolicyConfig();
		double blockThreshold = config.resolvedBlockThreshold();
		double reviewThreshold = config.resolvedReviewThreshold();
		double maxSeverity = 0.0;
		for (Finding finding : context.getFindings()) {
			double severity = finding.getSeverity() != null ? finding.getSeverity() : 0.0;
			if (severity > maxSeverity) {
				maxSeverity = severity;
			}
		}
		if (maxSeverity >= blockThreshold) {
			context.setVerdict(CleaningVerdict.BLOCK);
		}
		else if (maxSeverity >= reviewThreshold) {
			context.setVerdict(CleaningVerdict.REVIEW);
		}
		else {
			context.setVerdict(CleaningVerdict.ALLOW);
		}
		log.info(
				"Cleaning decide runId={} column={} findings={} maxSeverity={} blockThreshold={} reviewThreshold={} verdict={}",
				context.getJobRunId(), context.getColumnName(), context.getFindings().size(), maxSeverity,
				blockThreshold, reviewThreshold, context.getVerdict());
		return NodeResult.ok();
	}

	private boolean isL3AllParseFailed(CleaningContext context) {
		if (context.getFindings() != null && !context.getFindings().isEmpty()) {
			return false;
		}
		Object value = context.getMetadata().get("l3AllParseFailed");
		return value instanceof Boolean && (Boolean) value;
	}

}
