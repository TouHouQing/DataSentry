package com.touhouqing.datasentry.cleaning.mapper;

import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CleaningPolicyMapper {

	@Select("SELECT * FROM datasentry_cleaning_policy WHERE id = #{id}")
	CleaningPolicy findById(Long id);

}
