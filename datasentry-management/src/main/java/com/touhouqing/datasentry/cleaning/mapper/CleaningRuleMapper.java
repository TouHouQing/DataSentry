package com.touhouqing.datasentry.cleaning.mapper;

import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CleaningRuleMapper {

	@Select("""
			SELECT r.* FROM datasentry_cleaning_rule r
			JOIN datasentry_cleaning_policy_rule pr ON pr.rule_id = r.id
			WHERE pr.policy_id = #{policyId} AND r.enabled = 1
			ORDER BY pr.priority ASC
			""")
	List<CleaningRule> findByPolicyId(Long policyId);

}
