package com.touhouqing.datasentry.cleaning.mapper;

import com.touhouqing.datasentry.cleaning.model.CleaningAllowlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CleaningAllowlistMapper {

	@Select("""
			SELECT * FROM datasentry_cleaning_allowlist
			WHERE enabled = 1 AND (expire_time IS NULL OR expire_time > NOW())
			""")
	List<CleaningAllowlist> findActive();

}
