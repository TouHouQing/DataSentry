/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.touhouqing.datasentry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 数据库初始化测试
 *
 * @author vlsmb
 * @since 2025/9/26
 */
@Testcontainers
public class DatabaseSchemaTest {

	private static final String DATABASE_NAME = "datasentry";

	private static final String USER_PWD = "test";

	@Container
	private static final MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0").withDatabaseName(DATABASE_NAME)
		.withUsername(USER_PWD)
		.withPassword(USER_PWD)
		.withInitScript("sql/schema.sql");

	/**
	 * 核心表列表 - 验证这些关键表是否存在
	 */
	private static final Set<String> REQUIRED_TABLES = new HashSet<>(Arrays.asList("datasentry_agent",
			"datasentry_business_knowledge", "datasentry_semantic_model", "datasentry_agent_knowledge",
			"datasentry_datasource", "datasentry_logical_relation", "datasentry_agent_datasource",
			"datasentry_agent_preset_question", "datasentry_chat_session", "datasentry_chat_message",
			"datasentry_user_prompt_config", "datasentry_agent_datasource_tables", "datasentry_model_config",
			"datasentry_cleaning_policy", "datasentry_cleaning_rule", "datasentry_cleaning_policy_rule",
			"datasentry_cleaning_binding", "datasentry_cleaning_allowlist", "datasentry_cleaning_record"));

	@Test
	public void testDatabaseSchema() {
		Assertions.assertNotNull(container);
		Assertions.assertTrue(container.isRunning());

		// 验证所有必需的表都已创建
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(container.getJdbcUrl(), USER_PWD, USER_PWD);
			DatabaseMetaData metaData = conn.getMetaData();
			ResultSet tables = metaData.getTables(DATABASE_NAME, null, "%", new String[] { "TABLE" });

			Set<String> actualTables = new HashSet<>();
			while (tables.next()) {
				String tableName = tables.getString("TABLE_NAME");
				actualTables.add(tableName);
			}

			// 验证所有必需的表都存在
			Set<String> missingTables = new HashSet<>(REQUIRED_TABLES);
			missingTables.removeAll(actualTables);

			Assertions.assertTrue(missingTables.isEmpty(), "Missing required tables: " + missingTables);

			// 确保至少有所有必需的表
			Assertions.assertTrue(actualTables.size() >= REQUIRED_TABLES.size(),
					"Expected at least " + REQUIRED_TABLES.size() + " tables, but found " + actualTables.size());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			Optional.ofNullable(conn).ifPresent(c -> {
				try {
					c.close();
				}
				catch (SQLException e) {
					throw new RuntimeException(e);
				}
			});
		}

	}

}
