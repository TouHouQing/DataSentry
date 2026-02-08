package com.touhouqing.datasentry.cleaning.pipeline;

import com.touhouqing.datasentry.cleaning.enums.CleaningVerdict;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.NodeResult;
import com.touhouqing.datasentry.cleaning.util.CleaningSanitizer;
import org.springframework.stereotype.Component;

@Component
public class SanitizeNode implements PipelineNode {

	@Override
	public NodeResult process(CleaningContext context) {
		Object flag = context.getMetadata().get("sanitizeRequested");
		if (!(flag instanceof Boolean) || !((Boolean) flag)) {
			return NodeResult.skipped();
		}
		// Allow sanitization even if verdict is BLOCK or REVIEW.
		// If we can sanitize the findings, we transform the high-risk content into redacted content.

		if (context.getFindings() == null || context.getFindings().isEmpty()) {
			context.setSanitizedText(context.getOriginalText());
			if (context.getVerdict() == null) {
				context.setVerdict(CleaningVerdict.ALLOW);
			}
			return NodeResult.ok();
		}
		String sanitized = CleaningSanitizer.sanitize(context.getOriginalText(), context.getFindings());
		context.setSanitizedText(sanitized);

		// Resolve high-risk sanitization mode
		String mode = "MITIGATE";
		if (context.getPolicySnapshot() != null && context.getPolicySnapshot().getConfig() != null) {
			mode = context.getPolicySnapshot().getConfig().resolvedHighRiskSanitizationMode();
		}

		// Check if we should quarantine high-risk content (keep BLOCK verdict)
		// Default behavior (MITIGATE) is to update verdict to REDACTED if sanitization occurred
		if ("QUARANTINE".equalsIgnoreCase(mode) && context.getVerdict() == CleaningVerdict.BLOCK) {
			// Keep BLOCK verdict - risk is contained but writeback is prevented
		} else {
			// In MITIGATE mode, or for non-BLOCK verdicts, we consider the risk mitigated
			context.setVerdict(CleaningVerdict.REDACTED);
		}
		return NodeResult.ok();
	}

}
