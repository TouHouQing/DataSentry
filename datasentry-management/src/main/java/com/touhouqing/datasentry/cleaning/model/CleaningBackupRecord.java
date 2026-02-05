package com.touhouqing.datasentry.cleaning.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("datasentry_cleaning_backup_record")
public class CleaningBackupRecord {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long jobRunId;

	private Long datasourceId;

	private String tableName;

	private String pkJson;

	private String pkHash;

	private String encryptionProvider;

	private String keyVersion;

	private String beforeRowCiphertext;

	private String beforeRowJson;

	private LocalDateTime createdTime;

}
