package com.touhouqing.datasentry.cleaning.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CleaningOpsStateService {

	private static final int LATENCY_WINDOW_SIZE = 512;

	private final AtomicLong pricingSyncFailureCount = new AtomicLong();

	private final AtomicLong webhookPushSuccessCount = new AtomicLong();

	private final AtomicLong webhookPushFailureCount = new AtomicLong();

	private final AtomicLong evidenceBundleExportSuccessCount = new AtomicLong();

	private final AtomicLong evidenceBundleExportFailureCount = new AtomicLong();

	private final AtomicLong onnxModelLoadSuccessCount = new AtomicLong();

	private final AtomicLong onnxModelLoadFailureCount = new AtomicLong();

	private final AtomicLong onnxInferenceSuccessCount = new AtomicLong();

	private final AtomicLong onnxInferenceFailureCount = new AtomicLong();

	private final AtomicLong onnxFallbackCount = new AtomicLong();

	private final AtomicLong onnxInferenceTotalLatencyMs = new AtomicLong();

	private final Object onnxLatencyLock = new Object();

	private final ArrayList<Long> onnxLatencyWindow = new ArrayList<>();

	private final AtomicLong cloudInferenceSuccessCount = new AtomicLong();

	private final AtomicLong cloudInferenceFailureCount = new AtomicLong();

	private final AtomicLong cloudFallbackCount = new AtomicLong();

	private final AtomicLong cloudInferenceTotalLatencyMs = new AtomicLong();

	private final Object cloudLatencyLock = new Object();

	private final ArrayList<Long> cloudLatencyWindow = new ArrayList<>();

	private volatile LocalDateTime lastPricingSyncTime;

	private volatile String l2ProviderStatus = "DUMMY/OK";

	private volatile String onnxModelSignature;

	private volatile String onnxRuntimeVersion;

	public void markPricingSyncSuccess(LocalDateTime time) {
		lastPricingSyncTime = time;
	}

	public void markPricingSyncFailure() {
		pricingSyncFailureCount.incrementAndGet();
	}

	public void markWebhookPushSuccess() {
		webhookPushSuccessCount.incrementAndGet();
	}

	public void markWebhookPushFailure() {
		webhookPushFailureCount.incrementAndGet();
	}

	public void markEvidenceBundleExportSuccess() {
		evidenceBundleExportSuccessCount.incrementAndGet();
	}

	public void markEvidenceBundleExportFailure() {
		evidenceBundleExportFailureCount.incrementAndGet();
	}

	public void setL2ProviderStatus(String status) {
		if (status != null && !status.isBlank()) {
			l2ProviderStatus = status;
		}
	}

	public void markOnnxModelLoaded(String signature, String runtimeVersion) {
		onnxModelLoadSuccessCount.incrementAndGet();
		if (signature != null && !signature.isBlank()) {
			onnxModelSignature = signature;
		}
		if (runtimeVersion != null && !runtimeVersion.isBlank()) {
			onnxRuntimeVersion = runtimeVersion;
		}
	}

	public void markOnnxModelLoadFailure() {
		onnxModelLoadFailureCount.incrementAndGet();
	}

	public void markOnnxInferenceSuccess(long latencyMs) {
		onnxInferenceSuccessCount.incrementAndGet();
		recordLatency(latencyMs, onnxInferenceTotalLatencyMs, onnxLatencyLock, onnxLatencyWindow);
	}

	public void markOnnxInferenceFailure(long latencyMs) {
		onnxInferenceFailureCount.incrementAndGet();
		recordLatency(latencyMs, onnxInferenceTotalLatencyMs, onnxLatencyLock, onnxLatencyWindow);
	}

	public void markOnnxFallback() {
		onnxFallbackCount.incrementAndGet();
	}

	public void markCloudInferenceSuccess(long latencyMs) {
		cloudInferenceSuccessCount.incrementAndGet();
		recordLatency(latencyMs, cloudInferenceTotalLatencyMs, cloudLatencyLock, cloudLatencyWindow);
	}

	public void markCloudInferenceFailure(long latencyMs) {
		cloudInferenceFailureCount.incrementAndGet();
		recordLatency(latencyMs, cloudInferenceTotalLatencyMs, cloudLatencyLock, cloudLatencyWindow);
	}

	public void markCloudFallback() {
		cloudFallbackCount.incrementAndGet();
	}

	public LocalDateTime getLastPricingSyncTime() {
		return lastPricingSyncTime;
	}

	public long getPricingSyncFailureCount() {
		return pricingSyncFailureCount.get();
	}

	public long getWebhookPushSuccessCount() {
		return webhookPushSuccessCount.get();
	}

	public long getWebhookPushFailureCount() {
		return webhookPushFailureCount.get();
	}

	public long getEvidenceBundleExportSuccessCount() {
		return evidenceBundleExportSuccessCount.get();
	}

	public long getEvidenceBundleExportFailureCount() {
		return evidenceBundleExportFailureCount.get();
	}

	public String getL2ProviderStatus() {
		return l2ProviderStatus;
	}

	public long getOnnxModelLoadSuccessCount() {
		return onnxModelLoadSuccessCount.get();
	}

	public long getOnnxModelLoadFailureCount() {
		return onnxModelLoadFailureCount.get();
	}

	public long getOnnxInferenceSuccessCount() {
		return onnxInferenceSuccessCount.get();
	}

	public long getOnnxInferenceFailureCount() {
		return onnxInferenceFailureCount.get();
	}

	public long getOnnxFallbackCount() {
		return onnxFallbackCount.get();
	}

	public double getOnnxInferenceAvgLatencyMs() {
		long totalCount = onnxInferenceSuccessCount.get() + onnxInferenceFailureCount.get();
		if (totalCount <= 0) {
			return 0.0;
		}
		return (double) onnxInferenceTotalLatencyMs.get() / (double) totalCount;
	}

	public long getOnnxInferenceP95LatencyMs() {
		return percentile(onnxLatencyLock, onnxLatencyWindow, 0.95);
	}

	public String getOnnxModelSignature() {
		return onnxModelSignature;
	}

	public String getOnnxRuntimeVersion() {
		return onnxRuntimeVersion;
	}

	public long getCloudInferenceSuccessCount() {
		return cloudInferenceSuccessCount.get();
	}

	public long getCloudInferenceFailureCount() {
		return cloudInferenceFailureCount.get();
	}

	public long getCloudFallbackCount() {
		return cloudFallbackCount.get();
	}

	public double getCloudInferenceAvgLatencyMs() {
		long totalCount = cloudInferenceSuccessCount.get() + cloudInferenceFailureCount.get();
		if (totalCount <= 0) {
			return 0.0;
		}
		return (double) cloudInferenceTotalLatencyMs.get() / (double) totalCount;
	}

	public long getCloudInferenceP95LatencyMs() {
		return percentile(cloudLatencyLock, cloudLatencyWindow, 0.95);
	}

	private void recordLatency(long latencyMs, AtomicLong totalLatency, Object lock, ArrayList<Long> window) {
		long safeLatency = Math.max(latencyMs, 0L);
		totalLatency.addAndGet(safeLatency);
		synchronized (lock) {
			window.add(safeLatency);
			if (window.size() > LATENCY_WINDOW_SIZE) {
				window.remove(0);
			}
		}
	}

	private long percentile(Object lock, ArrayList<Long> window, double ratio) {
		List<Long> snapshot;
		synchronized (lock) {
			if (window.isEmpty()) {
				return 0L;
			}
			snapshot = new ArrayList<>(window);
		}
		Collections.sort(snapshot);
		int index = (int) Math.ceil(snapshot.size() * ratio) - 1;
		if (index < 0) {
			index = 0;
		}
		return snapshot.get(index);
	}

}
