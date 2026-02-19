<template>
  <BaseLayout>
    <div class="cleaning-realtime-page">
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">清理实时调试</h1>
            <p class="content-subtitle">调用 check/sanitize 接口做在线链路联调与验收</p>
          </div>
          <div class="header-actions">
            <CleaningModeSwitch @change="handleModeChange" />
            <el-button :icon="Refresh" :loading="loadingAgents" @click="loadAgents">
              刷新 Agent
            </el-button>
          </div>
        </div>

        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="intro-alert"
          :title="
            isBeginnerMode
              ? '新手模式：默认保留最少必填参数，结果区域会解释判定原因。'
              : '专家模式：保留完整接口参数，便于精细调试。'
          "
        />

        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="API Key 仅建议在可信本机环境使用，演示时请关闭投屏或隐藏敏感信息。"
          class="security-alert"
        />

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>请求参数</span>
            </div>
          </template>

          <el-form :model="form" label-width="120px">
            <el-form-item label="智能体">
              <el-select
                v-model="form.agentId"
                filterable
                clearable
                placeholder="请选择智能体"
                style="width: 100%"
              >
                <el-option
                  v-for="agent in agents"
                  :key="agent.id"
                  :label="agent.name || `Agent-${agent.id}`"
                  :value="agent.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="API Key">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                autocomplete="on"
                placeholder="请输入 Agent API Key"
              />
            </el-form-item>

            <el-form-item label="本机记住 Key">
              <el-switch v-model="form.rememberApiKey" @change="handleRememberChange" />
            </el-form-item>

            <el-form-item label="调用类型">
              <el-radio-group v-model="form.action">
                <el-radio-button label="check">仅检测（check）</el-radio-button>
                <el-radio-button label="sanitize">检测并脱敏（sanitize）</el-radio-button>
              </el-radio-group>
              <div class="field-help">{{ getFieldHelp('realtime.action') }}</div>
            </el-form-item>

            <el-form-item label="待检测文本">
              <el-input
                v-model="form.text"
                type="textarea"
                :autosize="{ minRows: 6, maxRows: 12 }"
                placeholder="请输入待检测文本"
              />
            </el-form-item>

            <el-form-item label="快速示例">
              <el-select
                v-model="sampleText"
                placeholder="选择示例文本"
                clearable
                style="width: 100%"
                @change="applySampleText"
              >
                <el-option
                  v-for="item in sampleTextOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="高级参数">
              <el-button text type="primary" @click="toggleAdvancedParams">
                {{ showAdvancedParams ? '收起高级参数' : '展开高级参数' }}
              </el-button>
            </el-form-item>

            <template v-if="showAdvancedParams">
              <el-form-item label="业务场景">
                <el-input v-model="form.scene" placeholder="可选：业务场景标识" />
                <div class="field-help">{{ getFieldHelp('realtime.scene') }}</div>
              </el-form-item>

              <el-form-item label="清理策略">
                <el-select
                  v-model="form.policyId"
                  clearable
                  filterable
                  style="width: 100%"
                  placeholder="可选：指定策略，不填则走绑定/默认策略"
                >
                  <el-option
                    v-for="policy in policies"
                    :key="policy.id"
                    :label="`${policy.name} (#${policy.id})`"
                    :value="policy.id"
                  />
                </el-select>
                <div class="field-help">{{ getFieldHelp('realtime.policyId') }}</div>
              </el-form-item>
            </template>

            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitRequest">
                执行 {{ form.action }}
              </el-button>
              <el-button @click="clearResult">清空结果</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>响应结果</span>
            </div>
          </template>

          <el-empty v-if="!responseData && !responseError" description="暂无结果" />

          <template v-else>
            <el-alert
              v-if="responseError"
              type="error"
              show-icon
              :closable="false"
              :title="responseError"
              class="response-error"
            />

            <el-descriptions v-if="responseData" :column="1" border>
              <el-descriptions-item label="判定结果">
                {{ formatVerdict(responseData.verdict) }}
              </el-descriptions-item>
              <el-descriptions-item label="Trace ID">
                <span class="mono-value">{{ responseData.traceId || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="策略版本">
                <template v-if="responseData.policyId">
                  <span class="mono-value">
                    #{{ responseData.policyId }} / v{{ responseData.policyVersionId || '-' }}
                  </span>
                </template>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="风险类别">
                <span v-if="!responseData.categories || responseData.categories.length === 0">
                  -
                </span>
                <template v-else>
                  <el-tag
                    v-for="category in responseData.categories"
                    :key="category"
                    type="warning"
                    class="category-tag"
                  >
                    {{ formatCategory(category) }}
                  </el-tag>
                </template>
              </el-descriptions-item>
              <el-descriptions-item label="Shadow 对照">
                <template v-if="responseData.shadowCompared">
                  <el-tag :type="shadowDiffTag(responseData.shadowDiffLevel)">
                    {{ formatShadowDiffLevel(responseData.shadowDiffLevel) }}
                  </el-tag>
                </template>
                <span v-else>未执行（采样未命中或未启用）</span>
              </el-descriptions-item>
              <el-descriptions-item label="脱敏文本">
                <pre class="result-text">{{ responseData.sanitizedText || '-' }}</pre>
              </el-descriptions-item>
            </el-descriptions>

            <el-alert
              v-if="responseData?.verdict"
              class="response-guide"
              type="info"
              :closable="false"
              show-icon
              :title="verdictHint"
            />

            <el-card v-if="responseData" shadow="never" class="explain-panel">
              <template #header>
                <span>判定解释</span>
              </template>
              <ul class="explain-list">
                <li v-for="line in explainLines" :key="line">{{ line }}</li>
              </ul>
              <div class="field-help">{{ getFieldHelp('realtime.explain') }}</div>
            </el-card>

            <div v-if="responseRaw" class="raw-panel">
              <h4>Raw JSON</h4>
              <pre class="result-text">{{ responseRaw }}</pre>
            </div>
          </template>
        </el-card>
      </main>
    </div>
  </BaseLayout>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { ElMessage } from 'element-plus';
  import { Refresh } from '@element-plus/icons-vue';
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import agentService from '@/services/agent';
  import cleaningMetaService from '@/services/cleaningMeta';
  import cleaningService from '@/services/cleaning';
  import {
    buildOptionLabel,
    getOptionLabelZh,
    mergeOptionsWithFallback,
    readCleaningUiMode,
    UI_MODE_BEGINNER,
    UI_MODE_EXPERT,
  } from '@/constants/cleaningLabelMaps';
  import CleaningModeSwitch from '@/components/cleaning/CleaningModeSwitch.vue';

  const STORAGE_KEY = 'datasentry.cleaning.realtime.api_key';

  const loadingAgents = ref(false);
  const submitting = ref(false);
  const agents = ref([]);
  const policies = ref([]);

  const responseData = ref(null);
  const responseRaw = ref('');
  const responseError = ref('');

  const uiMode = ref(readCleaningUiMode());
  const optionMeta = ref(mergeOptionsWithFallback(null));
  const sampleText = ref('');
  const showAdvancedParams = ref(false);

  const form = reactive({
    agentId: undefined,
    apiKey: '',
    rememberApiKey: false,
    action: 'check',
    text: '',
    scene: '',
    policyId: undefined,
  });

  const isBeginnerMode = computed(() => uiMode.value === UI_MODE_BEGINNER);
  const verdictOptions = computed(() => optionMeta.value.verdicts || []);
  const categoryOptions = computed(() => optionMeta.value.ruleCategories || []);

  const sampleTextOptions = [
    { label: '手机号示例', value: '请联系我 13812345678 处理退款。' },
    { label: '可疑推广示例', value: '点击链接 http://xxx.example 领取大奖，拉你进群。' },
    { label: '正常文本示例', value: '今天的会议纪要我已经整理完毕，请查收。' },
  ];

  const verdictHint = computed(() => {
    const verdict = responseData.value?.verdict;
    if (!verdict) {
      return '';
    }
    const matched = verdictOptions.value.find(item => item.code === verdict);
    const defaultHelp = optionMeta.value.fieldHelp?.['realtime.verdict'] || '';
    return matched?.description || defaultHelp;
  });

  const explainLines = computed(() => {
    if (!responseData.value) {
      return [];
    }
    const verdict = responseData.value.verdict;
    const categories = responseData.value.categories || [];
    const lines = [];

    if (verdict) {
      lines.push(`系统最终判定为：${formatVerdict(verdict)}。`);
    }

    if (categories.length > 0) {
      const categoryLabels = categories.map(category => formatCategory(category));
      lines.push(`命中风险类别：${categoryLabels.join('、')}。`);
    } else {
      lines.push('未返回命中类别，通常表示当前文本未命中显著风险。');
    }

    if (verdict === 'BLOCK') {
      lines.push('该请求风险较高，已触发拦截策略。');
    } else if (verdict === 'REVIEW') {
      lines.push('该请求处于人工审核区间，建议进入人审流程。');
    } else if (verdict === 'REDACTED') {
      lines.push('该请求已执行脱敏处理，返回文本为脱敏结果。');
    } else if (verdict === 'ALLOW') {
      lines.push('该请求未触发拦截条件，可按业务策略正常放行。');
    }

    return lines;
  });

  const handleModeChange = mode => {
    uiMode.value = mode === UI_MODE_EXPERT ? UI_MODE_EXPERT : UI_MODE_BEGINNER;
    if (uiMode.value === UI_MODE_EXPERT) {
      showAdvancedParams.value = true;
    }
  };

  const toggleAdvancedParams = () => {
    showAdvancedParams.value = !showAdvancedParams.value;
  };

  const getFieldHelp = key => optionMeta.value.fieldHelp?.[key] || '';

  const formatVerdict = code => buildOptionLabel(code, verdictOptions.value);
  const formatCategory = code => getOptionLabelZh(code, categoryOptions.value);
  const formatShadowDiffLevel = level => {
    const labels = {
      NONE: '无差异',
      LOW: '低差异',
      HIGH: '高差异',
    };
    return labels[level] || level || '未知';
  };

  const shadowDiffTag = level => {
    if (level === 'HIGH') {
      return 'danger';
    }
    if (level === 'LOW') {
      return 'warning';
    }
    return 'success';
  };

  const applySampleText = value => {
    if (!value) {
      return;
    }
    form.text = value;
  };

  const loadOptionMeta = async () => {
    const remote = await cleaningMetaService.getOptions();
    optionMeta.value = mergeOptionsWithFallback(remote);
  };

  const loadAgents = async () => {
    loadingAgents.value = true;
    try {
      agents.value = await agentService.list();
    } catch (error) {
      agents.value = [];
      ElMessage.error('加载 Agent 失败');
    } finally {
      loadingAgents.value = false;
    }
  };

  const loadPolicies = async () => {
    try {
      policies.value = await cleaningService.listPolicies();
    } catch (error) {
      policies.value = [];
    }
  };

  const clearResult = () => {
    responseData.value = null;
    responseRaw.value = '';
    responseError.value = '';
  };

  const handleRememberChange = enabled => {
    if (!enabled) {
      localStorage.removeItem(STORAGE_KEY);
    } else if (form.apiKey) {
      localStorage.setItem(STORAGE_KEY, form.apiKey);
    }
  };

  const buildRequestPayload = () => {
    const payload = {
      text: form.text,
    };
    if (form.scene) {
      payload.scene = form.scene;
    }
    if (form.policyId) {
      payload.policyId = Number(form.policyId);
    }
    return payload;
  };

  const extractErrorMessage = error => {
    const message = error?.response?.data?.message || error?.message || '请求失败';
    if (error?.response?.status === 401) {
      return '鉴权失败：请检查 API Key 是否正确且 Agent 已启用 API Key。';
    }
    if (error?.response?.status === 404) {
      return 'Agent 不存在，请检查 Agent ID。';
    }
    if (error?.response?.status === 503) {
      return '清理功能已关闭，请联系管理员开启。';
    }
    return `请求失败：${message}`;
  };

  const submitRequest = async () => {
    if (!form.agentId) {
      ElMessage.error('请选择 Agent');
      return;
    }
    if (!form.apiKey) {
      ElMessage.error('请输入 API Key');
      return;
    }
    if (!form.text.trim()) {
      ElMessage.error('请输入待检测文本');
      return;
    }

    submitting.value = true;
    responseError.value = '';
    try {
      const payload = buildRequestPayload();
      const data =
        form.action === 'sanitize'
          ? await cleaningService.sanitize(Number(form.agentId), form.apiKey, payload)
          : await cleaningService.check(Number(form.agentId), form.apiKey, payload);

      responseData.value = data;
      responseRaw.value = JSON.stringify(data || {}, null, 2);
      if (form.rememberApiKey) {
        localStorage.setItem(STORAGE_KEY, form.apiKey);
      }
      ElMessage.success('调用成功');
    } catch (error) {
      responseData.value = null;
      responseRaw.value = '';
      responseError.value = extractErrorMessage(error);
      ElMessage.error('调用失败');
    } finally {
      submitting.value = false;
    }
  };

  onMounted(async () => {
    const cachedKey = localStorage.getItem(STORAGE_KEY);
    if (cachedKey) {
      form.apiKey = cachedKey;
      form.rememberApiKey = true;
    }
    showAdvancedParams.value = !isBeginnerMode.value;
    await loadOptionMeta();
    await Promise.all([loadAgents(), loadPolicies()]);
  });
</script>

<style scoped>
  .cleaning-realtime-page {
    min-height: 100vh;
    padding: 2rem;
  }

  .main-content {
    max-width: 1100px;
    margin: 0 auto;
  }

  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1.25rem;
  }

  .content-title {
    margin: 0;
    font-size: 1.75rem;
    font-weight: 600;
    color: #0f172a;
  }

  .content-subtitle {
    margin-top: 0.5rem;
    color: #64748b;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .intro-alert {
    margin-bottom: 1rem;
  }

  .security-alert {
    margin-bottom: 1rem;
  }

  .panel {
    margin-bottom: 1rem;
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }

  .category-tag {
    margin-right: 0.4rem;
    margin-bottom: 0.3rem;
  }

  .raw-panel {
    margin-top: 1rem;
  }

  .result-text {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: Menlo, Consolas, Monaco, monospace;
    font-size: 13px;
    line-height: 1.5;
  }

  .mono-value {
    font-family: Menlo, Consolas, Monaco, monospace;
  }

  .response-error {
    margin-bottom: 1rem;
  }

  .field-help {
    margin-top: 6px;
    font-size: 12px;
    color: #64748b;
    line-height: 1.4;
  }

  .response-guide {
    margin-top: 1rem;
  }

  .explain-panel {
    margin-top: 12px;
    border-color: #e2e8f0;
  }

  .explain-list {
    margin: 0;
    padding-left: 18px;
    color: #334155;
    line-height: 1.7;
  }
</style>
