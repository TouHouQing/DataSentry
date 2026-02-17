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
package com.touhouqing.datasentry.properties;

import com.touhouqing.datasentry.constant.Constant;
import com.touhouqing.datasentry.service.llm.LlmServiceEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX)
public class DataSentryProperties {

	private LlmServiceEnum llmServiceType = LlmServiceEnum.STREAM;

	/**
	 * spring.ai.alibaba.datasentry.embedding-batch.encoding-type=cl100k_base
	 * spring.ai.alibaba.datasentry.embedding-batch.max-token-count=2000
	 * spring.ai.alibaba.datasentry.embedding-batch.reserve-percentage=0.2
	 * spring.ai.alibaba.datasentry.embedding-batch.max-text-count=10
	 */
	private EmbeddingBatch embeddingBatch = new EmbeddingBatch();

	private VectorStoreProperties vectorStore = new VectorStoreProperties();

	private ReportTemplate reportTemplate = new ReportTemplate();

	private Cleaning cleaning = new Cleaning();

	/**
	 * sql执行失败重试次数
	 */
	private int maxSqlRetryCount = 10;

	/**
	 * sql优化最多次数
	 */
	private int maxSqlOptimizeCount = 10;

	/**
	 * sql优化分数阈值
	 */
	private double sqlScoreThreshold = 0.95;

	private TextSplitter textSplitter = new TextSplitter();

	/**
	 * 最多保留的对话轮数
	 */
	private int maxturnhistory = 5;

	/**
	 * 单次规划最大长度限制
	 */
	private int maxplanlength = 2000;

	// 每张表的最大预估列数
	private int maxColumnsPerTable = 50;

	/**
	 * 是否启用SQL执行结果图表判断，默认启用
	 */
	private boolean enableSqlResultChart = true;

	/**
	 * 执行SQL结果图表化超时时间，默认3000ms
	 */
	private Long enrichSqlResultTimeout = 3000L;

	@Getter
	@Setter
	public static class ReportTemplate {

		// Marked.js (Markdown 解析器) 南方科技大学开源软件镜像站
		private String markedUrl = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js";

		// ECharts (图表库) 南方科技大学开源软件镜像站
		private String echartsUrl = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js";

	}

	@Getter
	@Setter
	public static class Cleaning {

		/**
		 * 清理功能开关，默认开启
		 */
		private boolean enabled = true;

		private Batch batch = new Batch();

		private Backup backup = new Backup();

		private Shadow shadow = new Shadow();

		private Budget budget = new Budget();

		private Pricing pricing = new Pricing();

		private L2 l2 = new L2();

		private L3 l3 = new L3();

		private Notification notification = new Notification();

		private Permission permission = new Permission();

		private Review review = new Review();

		private Lifecycle lifecycle = new Lifecycle();

		/**
		 * 策略发布治理开关
		 */
		private boolean policyGovernanceEnabled = false;

		/**
		 * Shadow 双轨对照开关
		 */
		private boolean shadowDualTrackEnabled = false;

		/**
		 * 回滚校验开关
		 */
		private boolean rollbackVerificationEnabled = false;

		@Getter
		@Setter
		public static class Batch {

			/**
			 * 批处理开关
			 */
			private boolean enabled = true;

			/**
			 * 轮询间隔（毫秒）
			 */
			private long pollIntervalMs = 5000;

			/**
			 * 租约时长（秒）
			 */
			private int leaseSeconds = 60;

			/**
			 * 是否允许 where_sql
			 */
			private boolean allowWhereSql = false;

			/**
			 * 默认批量大小
			 */
			private int defaultBatchSize = 200;

		}

		@Getter
		@Setter
		public static class Backup {

			/**
			 * 备份存储位置：METADB / BUSINESS_DB
			 */
			private String storage = "METADB";

			/**
			 * 是否加密备份
			 */
			private boolean encrypt = true;

			/**
			 * 主密钥环境变量名
			 */
			private String masterKeyEnv = "DATASENTRY_BACKUP_MASTER_KEY";

			/**
			 * 加密提供方标识
			 */
			private String provider = "LOCAL_AES_GCM";

		}

		@Getter
		@Setter
		public static class Budget {

			private String defaultCurrency = "CNY";

			private int defaultSoftLimit = 10;

			private int defaultHardLimit = 50;

			private int onlineRequestTokenLimit = 4000;

			private boolean failClosedEnabled = true;

		}

		@Getter
		@Setter
		public static class Shadow {

			private boolean enabled = true;

			private double sampleRatio = 0.1;

		}

		@Getter
		@Setter
		public static class Pricing {

			private boolean syncEnabled = true;

			private long syncIntervalMs = 1800000;

			private String sourceType = "LOCAL_CONFIG";

			private Http http = new Http();

			private List<PriceItem> localCatalog = defaultCatalog();

			private static List<PriceItem> defaultCatalog() {
				List<PriceItem> defaults = new ArrayList<>();
				defaults.add(new PriceItem("LOCAL_DEFAULT", "L3_LLM", "default", new BigDecimal("0.002000"),
						new BigDecimal("0.004000"), "CNY"));
				return defaults;
			}

			@Getter
			@Setter
			public static class Http {

				private String url;

				private long timeoutMs = 3000;

			}

			@Getter
			@Setter
			public static class PriceItem {

				private String provider;

				private String model;

				private String version = "default";

				private BigDecimal inputPricePer1k = BigDecimal.ZERO;

				private BigDecimal outputPricePer1k = BigDecimal.ZERO;

				private String currency = "CNY";

				public PriceItem() {
				}

				public PriceItem(String provider, String model, String version, BigDecimal inputPricePer1k,
						BigDecimal outputPricePer1k, String currency) {
					this.provider = provider;
					this.model = model;
					this.version = version;
					this.inputPricePer1k = inputPricePer1k;
					this.outputPricePer1k = outputPricePer1k;
					this.currency = currency;
				}

			}

		}

		@Getter
		@Setter
		public static class L2 {

			private String provider = "DUMMY";

			private String onnxModelPath;

			private double threshold = 0.6;

			private CloudApi cloudApi = new CloudApi();

			@Getter
			@Setter
			public static class CloudApi {

				private String url;

				private String apiKey;

				private String authHeader = "Authorization";

				private String authPrefix = "Bearer";

				private String model;

				private int timeoutMs = 3000;

				private int connectTimeoutMs = 1000;

			}

		}

		@Getter
		@Setter
		public static class L3 {

			private String strategy = "BALANCED";

			private int attemptTimeoutMs = 4500;

			private int maxRuleConcurrency = 2;

			private boolean enableAgentOutputtype = true;

			private boolean enableChatEntity = true;

			private boolean enableRawJson = true;

			private long providerCapabilityCacheTtlMs = 300000;

			private boolean batchEnabled = true;

			private int batchSize = 12;

			private int maxBatchConcurrency = 3;

			private int batchTimeoutMs = 2800;

			private int batchMaxTextLength = 256;

			private int batchMaxPromptChars = 6000;

			private String batchFailPolicy = "REVIEW_ALL";

		}

		@Getter
		@Setter
		public static class Notification {

			private long dlqBacklogThreshold = 100;

			private Webhook webhook = new Webhook();

			@Getter
			@Setter
			public static class Webhook {

				private boolean enabled = false;

				private String url;

				private String secret;

				private int timeoutMs = 3000;

				private int retryTimes = 1;

				private int rateLimitPerMinute = 30;

			}

		}

		@Getter
		@Setter
		public static class Permission {

			private boolean enabled = false;

			private String headerName = "X-Cleaning-Permissions";

			private boolean allowSuperToken = true;

			private String superToken = "*";

		}

		@Getter
		@Setter
		public static class Review {

			private boolean autoEscalationEnabled = false;

			private int overdueHours = 24;

			private int batchLimit = 200;

			private String reviewer = "sla-bot";

		}

		@Getter
		@Setter
		public static class Lifecycle {

			private boolean enabled = false;

			private long pollIntervalMs = 3600000;

			private int batchLimit = 500;

			private int backupRetentionDays = 30;

			private int auditRetentionDays = 90;

			private int reviewRetentionDays = 90;

			private int reviewFeedbackRetentionDays = 180;

			private int shadowRetentionDays = 30;

			private int rollbackRetentionDays = 180;

			private int dlqRetentionDays = 30;

		}

	}

	@Getter
	@Setter
	public static class TextSplitter {

		/**
		 * 默认分块大小，基于token数量 默认值：1000
		 */
		private int chunkSize = 1000;

		/**
		 * TokenTextSplitter 策略配置
		 */
		private TokenTextSplitterConfig token = new TokenTextSplitterConfig();

		/**
		 * RecursiveCharacterTextSplitter 策略配置
		 */
		private RecursiveTextSplitterConfig recursive = new RecursiveTextSplitterConfig();

		/**
		 * SentenceTextSplitter 策略配置
		 */
		private SentenceTextSplitterConfig sentence = new SentenceTextSplitterConfig();

		/**
		 * SemanticTextSplitter 策略配置
		 */
		private SemanticTextSplitterConfig semantic = new SemanticTextSplitterConfig();

		/**
		 * ParagraphTextSplitter 策略配置
		 */
		private ParagraphTextSplitterConfig paragraph = new ParagraphTextSplitterConfig();

		/**
		 * TokenTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class TokenTextSplitterConfig {

			/**
			 * 最小分块字符数 默认值：400
			 */
			private int minChunkSizeChars = 400;

			/**
			 * 嵌入最小分块长度 默认值：10
			 */
			private int minChunkLengthToEmbed = 10;

			/**
			 * 最大分块数量 默认值：5000
			 */
			private int maxNumChunks = 5000;

			/**
			 * 是否保留分隔符 默认值：true
			 */
			private boolean keepSeparator = true;

		}

		/**
		 * RecursiveCharacterTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class RecursiveTextSplitterConfig {

			/**
			 * 重叠区域字符数 默认值：200
			 */
			private int chunkOverlap = 200;

			/**
			 * 分隔符列表（如果为 null，该类内部有默认的分隔符列表）
			 */
			private String[] separators = null;

		}

		/**
		 * SentenceTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class SentenceTextSplitterConfig {

			/**
			 * 句子重叠数量 默认值：1（保留前一个分块的最后1个句子）
			 */
			private int sentenceOverlap = 1;

		}

		/**
		 * SemanticTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class SemanticTextSplitterConfig {

			/**
			 * 最小分块大小 默认值：200
			 */
			private int minChunkSize = 200;

			/**
			 * 最大分块大小 默认值：1000
			 */
			private int maxChunkSize = 1000;

			/**
			 * 语义相似度阈值 默认值：0.5（0-1之间，越低越容易分块）
			 */
			private double similarityThreshold = 0.5;

		}

		/**
		 * ParagraphTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class ParagraphTextSplitterConfig {

			/**
			 * 段落重叠字符数 默认值：200（保留前一个分块的最后200个字符，而非段落数量）
			 */
			private int paragraphOverlapChars = 200;

		}

	}

	@Getter
	@Setter
	public static class EmbeddingBatch {

		/**
		 * encodingType 默认值：cl100k_base，适用于OpenAI等模型
		 */
		private String encodingType = "cl100k_base";

		/**
		 * 每批次最大令牌数 值越小，每批次文档越少，但更安全 值越大，处理效率越高，但可能超出API限制 建议值：2000-8000，根据实际API限制调整
		 */
		private int maxTokenCount = 8000;

		/**
		 * 预留百分比 用于预留缓冲空间，避免超出限制 建议值：0.1-0.2（10%-20%）
		 */
		private double reservePercentage = 0.2;

		/**
		 * 每批次最大文本数量 适用于DashScope等有文本数量限制的API DashScope限制为10
		 */
		private int maxTextCount = 10;

	}

	@Getter
	@Setter
	public static class VectorStoreProperties {

		// 专门给召回Table 用的配置
		private int tableTopkLimit = 10;

		// 设置低尽可能保证表不会召回漏掉
		private double tableSimilarityThreshold = 0.2;

		// 全局默认配置（给 BusinessTerm, AgentKnowledge 等使用）
		/**
		 * 相似度阈值配置，用于过滤相似度分数大于等于此阈值的文档
		 */
		private double defaultSimilarityThreshold = 0.4;

		/**
		 * 查询时返回的最大文档数量
		 */
		private int defaultTopkLimit = 8;

		/**
		 * 一次删除操作中，最多删除的文档数量
		 */
		private int batchDelTopkLimit = 5000;

		/**
		 * 是否启用混合搜索
		 */
		private boolean enableHybridSearch = false;

		/**
		 * Elasticsearch最小分数阈值，用于es执行关键词搜索时过滤相关性较低的文档
		 */
		private double elasticsearchMinScore = 0.5;

		/**
		 * SimpleVectorStore本地序列化文件地址
		 */
		private String filePath = "./vectorstore/vectorstore.json";

	}

}
