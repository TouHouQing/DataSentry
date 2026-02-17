<template>
  <BaseLayout>
    <div class="cleaning-job-page">
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">清理任务管理</h1>
            <p class="content-subtitle">创建清理任务、启动运行并查看预算状态</p>
          </div>
          <div class="header-actions">
            <CleaningModeSwitch @change="handleModeChange" />
            <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建任务</el-button>
            <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
          </div>
        </div>

        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="intro-alert"
          :title="
            isBeginnerMode
              ? '新手模式：默认以中文解释任务参数，并隐藏部分高级项。'
              : '专家模式：显示完整参数，便于精细控制。'
          "
        />

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>任务列表</span>
              <el-form inline>
                <el-form-item label="智能体">
                  <el-select v-model="filters.agentId" clearable filterable style="width: 180px">
                    <el-option
                      v-for="agent in agents"
                      :key="agent.id"
                      :label="agent.name || `Agent-${agent.id}`"
                      :value="agent.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="filters.enabled" clearable style="width: 120px">
                    <el-option label="启用" :value="1" />
                    <el-option label="停用" :value="0" />
                  </el-select>
                </el-form-item>
                <el-button @click="loadJobs">查询</el-button>
              </el-form>
            </div>
          </template>

          <el-table :data="jobs" stripe v-loading="loadingJobs">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column label="智能体" min-width="160">
              <template #default="scope">
                {{ formatAgentDisplay(scope.row.agentId) }}
              </template>
            </el-table-column>
            <el-table-column label="数据源" min-width="160">
              <template #default="scope">
                {{ formatDatasourceDisplay(scope.row.datasourceId) }}
              </template>
            </el-table-column>
            <el-table-column prop="tableName" label="表" min-width="160" />
            <el-table-column label="目标模式" min-width="180">
              <template #default="scope">
                {{ formatTargetConfigType(scope.row.targetConfigType) }}
              </template>
            </el-table-column>
            <el-table-column label="预算" min-width="220">
              <template #default="scope">
                {{ formatJobBudget(scope.row) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="360" fixed="right">
              <template #default="scope">
                <el-button size="small" type="primary" @click="createRun(scope.row)">
                  启动运行
                </el-button>
                <el-button size="small" @click="selectJob(scope.row)">查看运行</el-button>
                <el-button size="small" @click="openEditDialog(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="removeJob(scope.row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="panel" v-if="selectedJob">
          <template #header>
            <div class="panel-header">
              <span>运行列表（任务 #{{ selectedJob.id }}）</span>
              <el-form inline>
                <el-form-item label="状态">
                  <el-select v-model="runStatusFilter" clearable style="width: 140px">
                    <el-option
                      v-for="item in runStatusOptions"
                      :key="item.code"
                      :label="formatOptionLabel(item)"
                      :value="item.code"
                    />
                  </el-select>
                </el-form-item>
                <el-button @click="loadRuns">查询</el-button>
              </el-form>
            </div>
          </template>

          <el-table :data="runs" stripe v-loading="loadingRuns">
            <el-table-column prop="id" label="运行 ID" width="110" />
            <el-table-column label="状态" width="130">
              <template #default="scope">
                {{ formatRunStatusShort(scope.row.status) }}
              </template>
            </el-table-column>
            <el-table-column label="策略版本" width="120">
              <template #default="scope">
                {{ scope.row.policyVersionId || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="totalScanned" label="扫描" width="100" />
            <el-table-column prop="totalFlagged" label="命中" width="100" />
            <el-table-column prop="actualCost" label="成本" width="120" />
            <el-table-column label="预算状态" width="140">
              <template #default="scope">
                {{ formatBudgetStatus(scope.row.budgetStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="预算信息" min-width="180">
              <template #default="scope">
                {{ formatBudgetMessage(scope.row.budgetStatus, scope.row.budgetMessage) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="430" fixed="right">
              <template #default="scope">
                <el-button size="small" @click="loadBudget(scope.row)">预算</el-button>
                <el-button size="small" type="primary" @click="createRollback(scope.row)">
                  回滚
                </el-button>
                <el-button size="small" type="info" @click="exportEvidenceBundle(scope.row)">
                  证据包
                </el-button>
                <el-button size="small" type="warning" @click="pauseRun(scope.row)">暂停</el-button>
                <el-button size="small" type="success" @click="resumeRun(scope.row)">
                  恢复
                </el-button>
                <el-button size="small" type="danger" @click="cancelRun(scope.row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-dialog
          v-model="createDialogVisible"
          :title="isEditMode ? '编辑清理任务' : '新建清理任务'"
          width="860px"
        >
          <el-steps :active="createStep" finish-status="success" simple class="create-steps">
            <el-step title="1. 选择数据范围" />
            <el-step title="2. 选择清理对象" />
            <el-step title="3. 设置风险控制" />
            <el-step title="4. 确认并创建" />
          </el-steps>

          <el-form :model="createForm" label-width="130px" class="step-form">
            <template v-if="createStep === 0">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                class="inline-alert"
                title="先确定 Agent、数据源、表和策略，后续步骤会自动联动字段。"
              />

              <el-form-item label="智能体">
                <el-select
                  v-model="createForm.agentId"
                  filterable
                  style="width: 100%"
                  placeholder="请选择智能体"
                >
                  <el-option
                    v-for="agent in agents"
                    :key="agent.id"
                    :label="agent.name || `Agent-${agent.id}`"
                    :value="agent.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="数据源">
                <el-select
                  v-model="createForm.datasourceId"
                  filterable
                  style="width: 100%"
                  placeholder="请选择数据源"
                >
                  <el-option
                    v-for="ds in datasources"
                    :key="ds.id"
                    :label="ds.name || `Datasource-${ds.id}`"
                    :value="ds.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="表名">
                <el-select
                  v-model="createForm.tableName"
                  filterable
                  clearable
                  style="width: 100%"
                  :loading="loadingTables"
                  placeholder="请选择表"
                >
                  <el-option
                    v-for="table in tableOptions"
                    :key="table"
                    :label="table"
                    :value="table"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="策略">
                <el-select
                  v-model="createForm.policyId"
                  filterable
                  clearable
                  style="width: 100%"
                  placeholder="请选择策略"
                >
                  <el-option
                    v-for="policy in policies"
                    :key="policy.id"
                    :label="`${policy.name} (#${policy.id})`"
                    :value="policy.id"
                  />
                </el-select>
              </el-form-item>
            </template>

            <template v-if="createStep === 1">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                class="inline-alert"
                title="选择主键列和目标列，并决定按整列清理还是 JSONPath 定位清理。"
              />

              <el-form-item label="主键列">
                <el-select
                  v-model="createForm.pkColumns"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  style="width: 100%"
                  :loading="loadingColumns"
                  placeholder="请选择主键列"
                >
                  <el-option
                    v-for="column in tableColumns"
                    :key="`pk-${column}`"
                    :label="column"
                    :value="column"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="目标列">
                <el-select
                  v-model="createForm.targetColumns"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  style="width: 100%"
                  :loading="loadingColumns"
                  placeholder="请选择目标列"
                >
                  <el-option
                    v-for="column in tableColumns"
                    :key="`target-${column}`"
                    :label="column"
                    :value="column"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="目标模式">
                <el-select v-model="createForm.targetConfigType" style="width: 220px">
                  <el-option
                    v-for="item in targetConfigTypeOptions"
                    :key="item.code"
                    :label="formatOptionLabel(item)"
                    :value="item.code"
                  />
                </el-select>
                <div class="field-help">{{ getFieldHelp('job.targetConfigType') }}</div>
              </el-form-item>

              <el-form-item v-if="createForm.targetConfigType === 'JSONPATH'" label="JSONPath 配置">
                <div class="jsonpath-panel">
                  <div class="jsonpath-toolbar">
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="createForm.targetColumns.length === 0"
                      @click="fillJsonPathTemplates"
                    >
                      模板填充
                    </el-button>
                  </div>
                  <div v-if="createForm.targetColumns.length === 0" class="jsonpath-empty">
                    请先选择目标列
                  </div>
                  <div v-else class="jsonpath-list">
                    <div
                      v-for="column in createForm.targetColumns"
                      :key="column"
                      class="jsonpath-item"
                    >
                      <span class="jsonpath-column">{{ column }}</span>
                      <el-input
                        v-model="jsonPathMappings[column]"
                        placeholder="例如：$.text.body"
                      />
                    </div>
                  </div>
                </div>
              </el-form-item>
            </template>

            <template v-if="createStep === 2">
              <el-alert
                v-if="isBeginnerMode"
                type="info"
                :closable="false"
                show-icon
                class="inline-alert"
                title="新手建议先用：试运行 + 不写回 + 有风险时人审。"
              />

              <el-form-item label="运行模式">
                <el-select v-model="createForm.mode" style="width: 240px">
                  <el-option
                    v-for="item in jobModeOptions"
                    :key="item.code"
                    :label="formatOptionLabel(item)"
                    :value="item.code"
                  />
                </el-select>
                <div class="field-help">{{ getFieldHelp('job.mode') }}</div>
              </el-form-item>

              <el-form-item label="写回模式">
                <el-select v-model="createForm.writebackMode" style="width: 240px">
                  <el-option
                    v-for="item in writebackModeOptions"
                    :key="item.code"
                    :label="formatOptionLabel(item)"
                    :value="item.code"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="人审策略">
                <el-select v-model="createForm.reviewPolicy" style="width: 240px">
                  <el-option
                    v-for="item in reviewPolicyOptions"
                    :key="item.code"
                    :label="formatOptionLabel(item)"
                    :value="item.code"
                  />
                </el-select>
                <div class="field-help">{{ getFieldHelp('job.reviewPolicy') }}</div>
              </el-form-item>

              <el-form-item v-if="createForm.reviewPolicy !== 'NEVER'" label="BLOCK 进人审">
                <el-switch v-model="createForm.reviewBlockOnRisk" />
                <div class="field-help">
                  当判定为 BLOCK 时，是否也创建人审任务；ALWAYS 默认开启。
                </div>
              </el-form-item>

              <el-form-item label="筛选条件">
                <el-input
                  v-model="createForm.whereSql"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  placeholder="仅填写条件本身，例如：status = 'ACTIVE'（不要写 WHERE）"
                />
                <div class="field-help">{{ getFieldHelp('job.whereSql') }}</div>
              </el-form-item>

              <el-alert
                v-if="createForm.whereSql && isRiskyWhereSql(createForm.whereSql)"
                type="warning"
                :closable="false"
                show-icon
                class="where-sql-alert"
                title="检测到高风险关键词（DROP/DELETE/TRUNCATE/UPDATE...），请仅保留筛选条件。"
              />

              <el-form-item label="在线 Fail-Closed">
                <el-switch v-model="createForm.onlineFailClosedEnabled" />
              </el-form-item>

              <el-form-item label="在线 Token 上限">
                <el-input-number
                  v-model="createForm.onlineRequestTokenLimit"
                  :min="1"
                  :step="100"
                />
              </el-form-item>

              <el-form-item label="写回映射(JSON)">
                <el-input
                  v-model="createForm.writebackMappingJson"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 8 }"
                  placeholder='例如：{"masked_phone":"phone"}'
                />
              </el-form-item>
            </template>

            <template v-if="createStep === 3">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                class="inline-alert"
                :title="getFieldHelp('job.wizard')"
              />

              <el-form-item label="预算启用">
                <el-switch v-model="createForm.budgetEnabled" />
                <div class="field-help">{{ getFieldHelp('job.budget') }}</div>
              </el-form-item>
              <el-form-item v-if="createForm.budgetEnabled" label="预算阈值">
                <div class="budget-row">
                  <el-input-number v-model="createForm.budgetSoftLimit" :min="0" :step="1" />
                  <span>-</span>
                  <el-input-number v-model="createForm.budgetHardLimit" :min="0" :step="1" />
                </div>
              </el-form-item>

              <el-descriptions :column="1" border class="summary-block">
                <el-descriptions-item label="执行对象">
                  Agent #{{ createForm.agentId || '-' }} / 数据源 #{{
                    createForm.datasourceId || '-'
                  }}
                  /
                  {{ createForm.tableName || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="策略与模式">
                  策略 #{{ createForm.policyId || '-' }} / {{ createForm.mode }} /
                  {{ createForm.writebackMode }}
                </el-descriptions-item>
                <el-descriptions-item label="目标配置">
                  {{ createForm.targetConfigType }} / 主键 {{ createForm.pkColumns.length }} 列 /
                  目标 {{ createForm.targetColumns.length }} 列
                </el-descriptions-item>
              </el-descriptions>
            </template>
          </el-form>

          <template #footer>
            <el-button @click="createDialogVisible = false">取消</el-button>
            <el-button v-if="createStep > 0" @click="prevCreateStep">上一步</el-button>
            <el-button v-if="createStep < 3" type="primary" @click="nextCreateStep">
              下一步
            </el-button>
            <el-button v-else type="primary" @click="submitCreate">
              {{ isEditMode ? '保存修改' : '创建任务' }}
            </el-button>
          </template>
        </el-dialog>

        <el-dialog v-model="budgetDialogVisible" title="预算快照" width="520px">
          <el-descriptions v-if="budgetView" :column="1" border>
            <el-descriptions-item label="Run ID">{{ budgetView.runId }}</el-descriptions-item>
            <el-descriptions-item label="Job ID">{{ budgetView.jobId }}</el-descriptions-item>
            <el-descriptions-item label="预算阈值">
              {{ budgetView.budgetSoftLimit }} - {{ budgetView.budgetHardLimit }}
              {{ budgetView.budgetCurrency }}
            </el-descriptions-item>
            <el-descriptions-item label="预估成本">
              {{ budgetView.estimatedCost }}
            </el-descriptions-item>
            <el-descriptions-item label="实际成本">
              {{ budgetView.actualCost }}
            </el-descriptions-item>
            <el-descriptions-item label="预算状态">
              {{ formatBudgetStatus(budgetView.budgetStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="预算信息">
              {{ formatBudgetMessage(budgetView.budgetStatus, budgetView.budgetMessage) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-dialog>

        <el-dialog v-model="rollbackDialogVisible" title="回滚任务" width="520px">
          <el-descriptions v-if="rollbackRun" :column="1" border>
            <el-descriptions-item label="Rollback Run ID">
              {{ rollbackRun.id }}
            </el-descriptions-item>
            <el-descriptions-item label="原 Run ID">
              {{ rollbackRun.jobRunId }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ formatRunStatusShort(rollbackRun.status) }}
            </el-descriptions-item>
            <el-descriptions-item label="目标/成功/失败">
              {{ rollbackRun.totalTarget || 0 }} / {{ rollbackRun.totalSuccess || 0 }} /
              {{ rollbackRun.totalFailed || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="校验状态">
              {{ formatRollbackVerifyStatus(rollbackRun.verifyStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="冲突汇总">
              {{ rollbackRun.conflictLevelSummary || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDateTime(rollbackRun.createdTime) }}
            </el-descriptions-item>
          </el-descriptions>
          <template #footer>
            <el-button @click="rollbackDialogVisible = false">关闭</el-button>
            <el-button type="primary" :loading="rollbackLoading" @click="refreshRollback">
              刷新状态
            </el-button>
          </template>
        </el-dialog>
      </main>
    </div>
  </BaseLayout>
</template>

<script setup>
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { Plus, Refresh } from '@element-plus/icons-vue';
  import BaseLayout from '@/layouts/BaseLayout.vue';
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
  import agentService from '@/services/agent';
  import datasourceService from '@/services/datasource';

  const loadingJobs = ref(false);
  const loadingRuns = ref(false);
  const loadingTables = ref(false);
  const loadingColumns = ref(false);

  const jobs = ref([]);
  const runs = ref([]);
  const agents = ref([]);
  const datasources = ref([]);
  const policies = ref([]);
  const tableOptions = ref([]);
  const tableColumns = ref([]);

  const selectedJob = ref(null);
  const runStatusFilter = ref('');

  const filters = reactive({
    agentId: undefined,
    enabled: undefined,
  });

  const createDialogVisible = ref(false);
  const createStep = ref(0);
  const editingJobId = ref(null);

  const defaultCreateForm = () => ({
    agentId: undefined,
    datasourceId: undefined,
    tableName: '',
    policyId: undefined,
    pkColumns: [],
    targetColumns: [],
    targetConfigType: 'COLUMNS',
    mode: 'DRY_RUN',
    writebackMode: 'NONE',
    reviewPolicy: 'NEVER',
    reviewBlockOnRisk: false,
    whereSql: '',
    onlineFailClosedEnabled: true,
    onlineRequestTokenLimit: 4000,
    writebackMappingJson: '{}',
    budgetEnabled: true,
    budgetSoftLimit: 10,
    budgetHardLimit: 50,
  });

  const createForm = reactive(defaultCreateForm());

  const uiMode = ref(readCleaningUiMode());
  const optionMeta = ref(mergeOptionsWithFallback(null));

  const isBeginnerMode = computed(() => uiMode.value === UI_MODE_BEGINNER);
  const isEditMode = computed(() => editingJobId.value !== null);
  const targetConfigTypeOptions = computed(() => optionMeta.value.targetConfigTypes || []);
  const reviewPolicyOptions = computed(() => optionMeta.value.reviewPolicies || []);
  const jobModeOptions = computed(() => optionMeta.value.jobModes || []);
  const writebackModeOptions = computed(() => optionMeta.value.writebackModes || []);
  const runStatusOptions = computed(() => optionMeta.value.runStatuses || []);

  const handleModeChange = mode => {
    uiMode.value = mode === UI_MODE_EXPERT ? UI_MODE_EXPERT : UI_MODE_BEGINNER;
  };

  const formatOptionLabel = item => {
    if (!item) {
      return '-';
    }
    if (isBeginnerMode.value) {
      return item.labelZh || item.code;
    }
    return `${item.labelZh || item.code}（${item.code}）`;
  };

  const formatAgentDisplay = agentId => {
    if (!agentId) {
      return '-';
    }
    const matched = agents.value.find(agent => Number(agent.id) === Number(agentId));
    return matched?.name || `Agent-${agentId}`;
  };

  const formatDatasourceDisplay = datasourceId => {
    if (!datasourceId) {
      return '-';
    }
    const matched = datasources.value.find(ds => Number(ds.id) === Number(datasourceId));
    return matched?.name || `数据源-${datasourceId}`;
  };

  const formatTargetConfigType = code => buildOptionLabel(code, targetConfigTypeOptions.value);
  const formatRunStatusShort = code => getOptionLabelZh(code, runStatusOptions.value);
  const formatDateTime = value => {
    if (!value) {
      return '-';
    }
    return String(value).replace('T', ' ');
  };
  const formatBudgetStatus = status => {
    const labels = {
      NORMAL: '正常',
      SOFT_EXCEEDED: '软超限',
      HARD_EXCEEDED: '硬超限',
    };
    return labels[status] || status || '-';
  };

  const formatBudgetMessage = (status, message) => {
    if (message && String(message).trim()) {
      return message;
    }
    if (status === 'NORMAL') {
      return '预算正常';
    }
    if (status === 'SOFT_EXCEEDED') {
      return '预算达到软阈值';
    }
    if (status === 'HARD_EXCEEDED') {
      return '预算超过硬阈值';
    }
    return '-';
  };

  const formatRollbackVerifyStatus = status => {
    const labels = {
      PENDING: '待校验',
      PASSED: '通过',
      PARTIAL: '部分通过',
      FAILED: '失败',
    };
    return labels[status] || status || '-';
  };

  const formatJobBudget = job => {
    const budgetEnabled = Number(job?.budgetEnabled ?? 1) === 1;
    if (!budgetEnabled) {
      return '关闭';
    }
    const softLimit = job?.budgetSoftLimit ?? '--';
    const hardLimit = job?.budgetHardLimit ?? '--';
    return `启用 / ${softLimit} - ${hardLimit}`;
  };

  const getFieldHelp = key => optionMeta.value.fieldHelp?.[key] || '';

  const jsonPathMappings = reactive({});

  const budgetDialogVisible = ref(false);
  const budgetView = ref(null);

  const rollbackDialogVisible = ref(false);
  const rollbackRun = ref(null);
  const rollbackLoading = ref(false);

  const resetJsonPathMappings = () => {
    Object.keys(jsonPathMappings).forEach(key => {
      delete jsonPathMappings[key];
    });
  };

  const resetCreateForm = () => {
    Object.assign(createForm, defaultCreateForm());
    createStep.value = 0;
    editingJobId.value = null;
    resetJsonPathMappings();
  };

  const parseJsonObject = (text, fallback = {}) => {
    if (!text || typeof text !== 'string') {
      return { ...fallback };
    }
    try {
      const parsed = JSON.parse(text);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed;
      }
    } catch (error) {
      return { ...fallback };
    }
    return { ...fallback };
  };

  const parseJsonStringArray = text => {
    if (!text || typeof text !== 'string') {
      return [];
    }
    try {
      const parsed = JSON.parse(text);
      if (Array.isArray(parsed)) {
        return parsed.map(item => String(item || '').trim()).filter(item => item.length > 0);
      }
    } catch (error) {
      return [];
    }
    return [];
  };

  const buildDefaultJsonPath = column => `$.${column}`;

  const detectDefaultPkColumns = (columns, tableName) => {
    if (!columns || columns.length === 0) {
      return [];
    }
    const lowerMap = new Map(columns.map(column => [column.toLowerCase(), column]));

    if (lowerMap.has('id')) {
      return [lowerMap.get('id')];
    }

    const normalizedTable = (tableName || '').toLowerCase();
    if (normalizedTable) {
      const tableIdKey = `${normalizedTable}_id`;
      if (lowerMap.has(tableIdKey)) {
        return [lowerMap.get(tableIdKey)];
      }
    }

    if (lowerMap.has('uuid')) {
      return [lowerMap.get('uuid')];
    }

    return [];
  };

  const syncJsonPathMappings = () => {
    const nextMappings = {};
    createForm.targetColumns.forEach(column => {
      nextMappings[column] = jsonPathMappings[column] || buildDefaultJsonPath(column);
    });
    resetJsonPathMappings();
    Object.assign(jsonPathMappings, nextMappings);
  };

  const fillJsonPathTemplates = () => {
    if (createForm.targetColumns.length === 0) {
      ElMessage.warning('请先选择目标列');
      return;
    }
    const nextMappings = {};
    createForm.targetColumns.forEach(column => {
      nextMappings[column] = buildDefaultJsonPath(column);
    });
    resetJsonPathMappings();
    Object.assign(jsonPathMappings, nextMappings);
    ElMessage.success('已按模板填充 JSONPath');
  };

  const loadAgents = async () => {
    try {
      agents.value = await agentService.list();
    } catch (error) {
      agents.value = [];
    }
  };

  const loadDatasources = async () => {
    try {
      datasources.value = await datasourceService.getAllDatasource();
    } catch (error) {
      datasources.value = [];
    }
  };

  const loadPolicies = async () => {
    try {
      policies.value = await cleaningService.listPolicies();
      if (!createForm.policyId && policies.value.length > 0) {
        createForm.policyId = policies.value[0].id;
      }
    } catch (error) {
      policies.value = [];
    }
  };

  const resetTableAndColumns = () => {
    createForm.tableName = '';
    tableOptions.value = [];
    tableColumns.value = [];
    createForm.pkColumns = [];
    createForm.targetColumns = [];
    resetJsonPathMappings();
  };

  const loadTables = async datasourceId => {
    if (!datasourceId) {
      tableOptions.value = [];
      return;
    }
    loadingTables.value = true;
    try {
      tableOptions.value = await datasourceService.getDatasourceTables(Number(datasourceId));
    } catch (error) {
      tableOptions.value = [];
      ElMessage.error('加载数据表失败');
    } finally {
      loadingTables.value = false;
    }
  };

  const loadColumns = async (datasourceId, tableName) => {
    if (!datasourceId || !tableName) {
      tableColumns.value = [];
      return;
    }
    loadingColumns.value = true;
    try {
      const columns = await datasourceService.getTableColumns(Number(datasourceId), tableName);
      tableColumns.value = columns;
      const existingPkColumns = createForm.pkColumns.filter(column => columns.includes(column));
      const autoDetectedPkColumns =
        existingPkColumns.length > 0 ? [] : detectDefaultPkColumns(columns, tableName);
      createForm.pkColumns =
        existingPkColumns.length > 0 ? existingPkColumns : autoDetectedPkColumns;
      if (existingPkColumns.length === 0 && autoDetectedPkColumns.length > 0) {
        ElMessage.info(`已为你自动预选主键列：${autoDetectedPkColumns.join(', ')}`);
      }
      createForm.targetColumns = createForm.targetColumns.filter(column =>
        columns.includes(column),
      );
      syncJsonPathMappings();
    } catch (error) {
      tableColumns.value = [];
      ElMessage.error('加载字段失败');
    } finally {
      loadingColumns.value = false;
    }
  };

  const loadJobs = async () => {
    loadingJobs.value = true;
    try {
      jobs.value = await cleaningService.listJobs({
        agentId: filters.agentId,
        enabled: filters.enabled,
      });
      if (selectedJob.value?.id) {
        const refreshedSelected = jobs.value.find(item => item.id === selectedJob.value.id);
        if (refreshedSelected) {
          selectedJob.value = refreshedSelected;
        } else {
          selectedJob.value = null;
          runs.value = [];
        }
      }
    } catch (error) {
      ElMessage.error('加载任务失败');
    } finally {
      loadingJobs.value = false;
    }
  };

  const loadRuns = async () => {
    if (!selectedJob.value) {
      return;
    }
    loadingRuns.value = true;
    try {
      runs.value = await cleaningService.listRuns({
        jobId: selectedJob.value.id,
        status: runStatusFilter.value || undefined,
      });
    } catch (error) {
      ElMessage.error('加载运行实例失败');
    } finally {
      loadingRuns.value = false;
    }
  };

  const loadAll = async () => {
    await Promise.all([loadAgents(), loadDatasources(), loadPolicies(), loadJobs()]);
    if (selectedJob.value) {
      await loadRuns();
    }
  };

  const loadOptionMeta = async () => {
    const remote = await cleaningMetaService.getOptions();
    optionMeta.value = mergeOptionsWithFallback(remote);
  };

  watch(
    () => createForm.datasourceId,
    async datasourceId => {
      resetTableAndColumns();
      if (datasourceId) {
        await loadTables(datasourceId);
      }
    },
  );

  watch(
    () => createForm.tableName,
    async tableName => {
      tableColumns.value = [];
      createForm.pkColumns = [];
      createForm.targetColumns = [];
      resetJsonPathMappings();
      if (createForm.datasourceId && tableName) {
        await loadColumns(createForm.datasourceId, tableName);
      }
    },
  );

  watch(
    () => createForm.targetColumns,
    () => {
      if (createForm.targetConfigType === 'JSONPATH') {
        syncJsonPathMappings();
      }
    },
    { deep: true },
  );

  watch(
    () => createForm.targetConfigType,
    targetConfigType => {
      if (targetConfigType === 'JSONPATH') {
        syncJsonPathMappings();
      }
    },
  );

  watch(
    () => createForm.reviewPolicy,
    reviewPolicy => {
      if (reviewPolicy === 'ALWAYS') {
        createForm.reviewBlockOnRisk = true;
      } else if (reviewPolicy === 'NEVER') {
        createForm.reviewBlockOnRisk = false;
      }
    },
  );

  const validateStep0 = () => {
    if (
      !createForm.agentId ||
      !createForm.datasourceId ||
      !createForm.tableName ||
      !createForm.policyId
    ) {
      ElMessage.error('请先填写 Agent、数据源、表和策略');
      return false;
    }
    return true;
  };

  const validateStep1 = () => {
    if (createForm.pkColumns.length === 0) {
      ElMessage.error('请至少选择一个主键列');
      return false;
    }
    if (createForm.targetColumns.length === 0) {
      ElMessage.error('请至少选择一个目标列');
      return false;
    }
    if (createForm.targetConfigType === 'JSONPATH' && !buildJsonPathTargetConfig()) {
      return false;
    }
    return true;
  };

  const nextCreateStep = () => {
    if (createStep.value === 0 && !validateStep0()) {
      return;
    }
    if (createStep.value === 1 && !validateStep1()) {
      return;
    }
    createStep.value = Math.min(createStep.value + 1, 3);
  };

  const prevCreateStep = () => {
    createStep.value = Math.max(createStep.value - 1, 0);
  };

  const openCreateDialog = async () => {
    resetCreateForm();
    createDialogVisible.value = true;
    if (policies.value.length === 0) {
      await loadPolicies();
    }
  };

  const openEditDialog = async job => {
    resetCreateForm();
    editingJobId.value = job.id;
    createDialogVisible.value = true;

    if (policies.value.length === 0) {
      await loadPolicies();
    }

    createForm.agentId = job.agentId;
    createForm.datasourceId = job.datasourceId;
    await loadTables(job.datasourceId);

    createForm.tableName = job.tableName || '';
    await loadColumns(job.datasourceId, createForm.tableName);

    const normalizeColumns = columns => {
      if (!Array.isArray(columns)) {
        return [];
      }
      if (tableColumns.value.length === 0) {
        return columns;
      }
      return columns.filter(column => tableColumns.value.includes(column));
    };

    const pkColumns = normalizeColumns(parseJsonStringArray(job.pkColumnsJson));
    const targetColumns = normalizeColumns(parseJsonStringArray(job.targetColumnsJson));

    createForm.policyId = job.policyId;
    createForm.pkColumns = pkColumns;
    createForm.targetColumns = targetColumns;
    createForm.targetConfigType = job.targetConfigType || 'COLUMNS';
    createForm.mode = job.mode || 'DRY_RUN';
    createForm.writebackMode = job.writebackMode || 'NONE';
    createForm.reviewPolicy = job.reviewPolicy || 'NEVER';
    createForm.whereSql = job.whereSql || '';

    const targetConfig = parseJsonObject(job.targetConfigJson);
    const backupPolicy = parseJsonObject(job.backupPolicyJson);
    const writebackMapping = parseJsonObject(job.writebackMappingJson);

    resetJsonPathMappings();
    if (createForm.targetConfigType === 'JSONPATH') {
      createForm.targetColumns.forEach(column => {
        const rawPath = targetConfig?.[column];
        const path =
          typeof rawPath === 'string' && rawPath.trim()
            ? rawPath.trim()
            : buildDefaultJsonPath(column);
        jsonPathMappings[column] = path;
      });
    }

    createForm.reviewBlockOnRisk =
      createForm.reviewPolicy === 'ALWAYS' || Boolean(backupPolicy.reviewBlockOnRisk);
    createForm.onlineFailClosedEnabled = Number(job.onlineFailClosedEnabled ?? 1) === 1;
    createForm.onlineRequestTokenLimit =
      Number.isFinite(Number(job.onlineRequestTokenLimit)) &&
      Number(job.onlineRequestTokenLimit) > 0
        ? Number(job.onlineRequestTokenLimit)
        : 4000;
    createForm.writebackMappingJson = JSON.stringify(writebackMapping, null, 2);
    createForm.budgetEnabled = Number(job.budgetEnabled ?? 1) === 1;
    createForm.budgetSoftLimit = Number.isFinite(Number(job.budgetSoftLimit))
      ? Number(job.budgetSoftLimit)
      : 10;
    createForm.budgetHardLimit = Number.isFinite(Number(job.budgetHardLimit))
      ? Number(job.budgetHardLimit)
      : 50;
  };

  const removeJob = async job => {
    try {
      await ElMessageBox.confirm(`确认删除任务 #${job.id} 吗？`, '删除确认', {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      });
    } catch (error) {
      return;
    }

    try {
      await cleaningService.deleteJob(job.id);
      if (selectedJob.value?.id === job.id) {
        selectedJob.value = null;
        runs.value = [];
      }
      await loadJobs();
      ElMessage.success('任务已删除');
    } catch (error) {
      ElMessage.error('删除任务失败');
    }
  };

  const buildJsonPathTargetConfig = () => {
    const mapping = {};
    for (const column of createForm.targetColumns) {
      const path = (jsonPathMappings[column] || '').trim();
      if (!path) {
        ElMessage.error(`目标列 ${column} 的 JSONPath 不能为空`);
        return null;
      }
      mapping[column] = path;
    }
    return mapping;
  };

  const isRiskyWhereSql = whereSql => {
    if (!whereSql) {
      return false;
    }
    return /\b(drop|delete|truncate|update|alter|create)\b/i.test(whereSql);
  };

  const parseWritebackMapping = () => {
    const raw = (createForm.writebackMappingJson || '').trim();
    if (!raw) {
      return undefined;
    }
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed;
      }
      ElMessage.error('写回映射必须是 JSON 对象');
      return null;
    } catch (error) {
      ElMessage.error('写回映射 JSON 格式不合法');
      return null;
    }
  };

  const submitCreate = async () => {
    if (!validateStep0() || !validateStep1()) {
      return;
    }

    let targetConfig;
    if (createForm.targetConfigType === 'JSONPATH') {
      targetConfig = buildJsonPathTargetConfig();
      if (!targetConfig) {
        return;
      }
    }

    const writebackMapping = parseWritebackMapping();
    if (writebackMapping === null) {
      return;
    }

    if (isRiskyWhereSql(createForm.whereSql)) {
      ElMessage.warning('whereSql 包含高风险关键词，请确认仅用于筛选条件。');
    }

    if (createForm.mode === 'WRITEBACK' || createForm.writebackMode !== 'NONE') {
      try {
        const riskMessage =
          optionMeta.value.riskConfirmations?.WRITEBACK ||
          '正式写回会修改业务数据，建议先试运行验证。确认继续创建？';
        await ElMessageBox.confirm(riskMessage, '写回风险确认', {
          type: 'warning',
          confirmButtonText: '继续创建',
          cancelButtonText: '返回修改',
        });
      } catch (error) {
        return;
      }
    }

    try {
      const payload = {
        agentId: Number(createForm.agentId),
        datasourceId: Number(createForm.datasourceId),
        tableName: createForm.tableName,
        policyId: Number(createForm.policyId),
        pkColumns: createForm.pkColumns,
        targetColumns: createForm.targetColumns,
        targetConfigType: createForm.targetConfigType,
        targetConfig,
        mode: createForm.mode,
        writebackMode: createForm.writebackMode,
        reviewPolicy: createForm.reviewPolicy,
        backupPolicy: {
          reviewBlockOnRisk: createForm.reviewPolicy === 'ALWAYS' || createForm.reviewBlockOnRisk,
        },
        whereSql: createForm.whereSql || undefined,
        onlineFailClosedEnabled: createForm.onlineFailClosedEnabled ? 1 : 0,
        onlineRequestTokenLimit: Number(createForm.onlineRequestTokenLimit),
        writebackMapping,
        budgetEnabled: createForm.budgetEnabled ? 1 : 0,
        budgetSoftLimit: createForm.budgetSoftLimit,
        budgetHardLimit: createForm.budgetHardLimit,
      };

      if (isEditMode.value && editingJobId.value !== null) {
        await cleaningService.updateJob(editingJobId.value, payload);
      } else {
        await cleaningService.createJob(payload);
      }

      createDialogVisible.value = false;
      await loadJobs();
      if (selectedJob.value?.id) {
        await loadRuns();
      }
      ElMessage.success(isEditMode.value ? '任务更新成功' : '任务创建成功');
      editingJobId.value = null;
    } catch (error) {
      ElMessage.error(isEditMode.value ? '更新任务失败，请检查输入' : '创建任务失败，请检查输入');
    }
  };

  const selectJob = async job => {
    selectedJob.value = job;
    await loadRuns();
  };

  const createRun = async job => {
    try {
      await cleaningService.createRun(job.id);
      ElMessage.success('已创建运行实例');
      if (selectedJob.value?.id === job.id) {
        await loadRuns();
      }
    } catch (error) {
      ElMessage.error('创建运行实例失败');
    }
  };

  const pauseRun = async run => {
    try {
      await cleaningService.pauseRun(run.id);
      await loadRuns();
    } catch (error) {
      ElMessage.error('暂停失败');
    }
  };

  const resumeRun = async run => {
    try {
      await cleaningService.resumeRun(run.id);
      await loadRuns();
    } catch (error) {
      ElMessage.error('恢复失败（可能硬预算超限）');
    }
  };

  const cancelRun = async run => {
    try {
      await cleaningService.cancelRun(run.id);
      await loadRuns();
    } catch (error) {
      ElMessage.error('取消失败');
    }
  };

  const createRollback = async run => {
    rollbackLoading.value = true;
    try {
      const result = await cleaningService.createRollback(run.id);
      if (!result) {
        ElMessage.error('创建回滚任务失败');
        return;
      }
      rollbackRun.value = result;
      rollbackDialogVisible.value = true;
      ElMessage.success(`回滚任务已创建：#${result.id}`);
    } catch (error) {
      ElMessage.error('创建回滚任务失败');
    } finally {
      rollbackLoading.value = false;
    }
  };

  const exportEvidenceBundle = async run => {
    try {
      const bundle = await cleaningService.getEvidenceBundle(run.id);
      if (!bundle) {
        ElMessage.error('证据包导出失败');
        return;
      }
      const filename = `cleaning-evidence-run-${run.id}.json`;
      const content = JSON.stringify(bundle, null, 2);
      const blob = new Blob([content], { type: 'application/json;charset=utf-8' });
      const link = document.createElement('a');
      const objectUrl = URL.createObjectURL(blob);
      link.href = objectUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(objectUrl);
      ElMessage.success('证据包已导出');
    } catch (error) {
      ElMessage.error('证据包导出失败');
    }
  };

  const refreshRollback = async () => {
    if (!rollbackRun.value?.id) {
      return;
    }
    rollbackLoading.value = true;
    try {
      rollbackRun.value = await cleaningService.getRollback(rollbackRun.value.id);
    } catch (error) {
      ElMessage.error('刷新回滚状态失败');
    } finally {
      rollbackLoading.value = false;
    }
  };

  const loadBudget = async run => {
    try {
      budgetView.value = await cleaningService.getBudget(run.id);
      budgetDialogVisible.value = true;
    } catch (error) {
      ElMessage.error('加载预算快照失败');
    }
  };

  onMounted(() => {
    loadOptionMeta();
    loadAll();
  });
</script>

<style scoped>
  .cleaning-job-page {
    min-height: 100vh;
    padding: 2rem;
  }

  .main-content {
    max-width: 1320px;
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

  .panel {
    margin-top: 1rem;
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }

  .budget-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .jsonpath-panel {
    width: 100%;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 12px;
    background: #f8fafc;
  }

  .jsonpath-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 10px;
  }

  .jsonpath-empty {
    color: #64748b;
  }

  .jsonpath-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .jsonpath-item {
    display: grid;
    grid-template-columns: 160px 1fr;
    gap: 10px;
    align-items: center;
  }

  .jsonpath-column {
    color: #0f172a;
    font-weight: 500;
  }

  .where-sql-alert {
    margin-bottom: 12px;
  }

  .create-steps {
    margin-bottom: 14px;
  }

  .step-form {
    margin-top: 12px;
  }

  .summary-block {
    margin-top: 10px;
  }

  .field-help {
    margin-top: 6px;
    font-size: 12px;
    color: #64748b;
    line-height: 1.4;
  }

  .inline-alert {
    margin-bottom: 12px;
  }
</style>
