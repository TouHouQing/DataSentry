package com.touhouqing.datasentry.cleaning.mapper;

import com.touhouqing.datasentry.cleaning.model.CleaningBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CleaningBindingMapper {

	@Select("""
			SELECT * FROM datasentry_cleaning_binding
			WHERE agent_id = #{agentId}
			AND binding_type = #{bindingType}
			AND enabled = 1
			AND scene = #{scene}
			ORDER BY id DESC
			LIMIT 1
			""")
	CleaningBinding findByAgentAndScene(@Param("agentId") Long agentId, @Param("bindingType") String bindingType,
			@Param("scene") String scene);

	@Select("""
			SELECT * FROM datasentry_cleaning_binding
			WHERE agent_id = #{agentId}
			AND binding_type = #{bindingType}
			AND enabled = 1
			AND scene IS NULL
			ORDER BY id DESC
			LIMIT 1
			""")
	CleaningBinding findDefaultByAgent(@Param("agentId") Long agentId, @Param("bindingType") String bindingType);

}
