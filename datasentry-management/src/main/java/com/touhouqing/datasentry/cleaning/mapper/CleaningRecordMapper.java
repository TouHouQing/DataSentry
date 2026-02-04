package com.touhouqing.datasentry.cleaning.mapper;

import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface CleaningRecordMapper {

	@Insert("""
			INSERT INTO datasentry_cleaning_record (agent_id, trace_id, policy_snapshot_json, verdict, categories_json,
			sanitized_preview, metrics_json, execution_time_ms, detector_source, created_time)
			VALUES (#{agentId}, #{traceId}, #{policySnapshotJson}, #{verdict}, #{categoriesJson}, #{sanitizedPreview},
			#{metricsJson}, #{executionTimeMs}, #{detectorSource}, #{createdTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(CleaningRecord record);

}
