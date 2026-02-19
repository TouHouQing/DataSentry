<template>
  <BaseLayout>
    <div class="review-workbench">
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">人审工作台</h1>
            <p class="content-subtitle">集中处理批量清理任务的审核与写回</p>
          </div>
          <div class="header-actions">
            <el-button type="warning" @click="handleEscalateOverdue">升级超时任务</el-button>
            <el-button :icon="Refresh" size="large" @click="loadReviews">刷新</el-button>
          </div>
        </div>

        <el-card class="filter-card">
          <div class="filter-row">
            <el-select
              v-model="filters.status"
              placeholder="状态筛选"
              clearable
              style="width: 160px"
            >
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已拒绝" value="REJECTED" />
              <el-option label="冲突" value="CONFLICT" />
              <el-option label="已写回" value="WRITTEN" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-input v-model="filters.jobRunId" placeholder="Job Run ID" style="width: 200px" />
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button :loading="loadingOptimization" @click="loadOptimizationSuggestions">
              优化建议
            </el-button>
          </div>
          <div class="bulk-row">
            <el-button
              type="success"
              :disabled="selectedRows.length === 0"
              @click="handleBatchApprove"
            >
              批量通过
            </el-button>
            <el-button
              type="danger"
              :disabled="selectedRows.length === 0"
              @click="handleBatchReject"
            >
              批量拒绝
            </el-button>
            <el-divider direction="vertical" />
            <el-button
              type="success"
              plain
              :disabled="!filters.jobRunId"
              @click="handleBatchAllApprove"
            >
              全量通过
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="!filters.jobRunId"
              @click="handleBatchAllReject"
            >
              全量拒绝
            </el-button>
          </div>
        </el-card>

        <el-card class="optimization-card" v-loading="loadingOptimization">
          <template #header>
            <div class="optimization-header">
              <span>反馈优化建议</span>
              <span class="optimization-meta">
                样本数：{{ optimizationView?.totalSamples || 0 }}
              </span>
            </div>
          </template>
          <el-empty
            v-if="!optimizationView || (optimizationView.disputedRules || []).length === 0"
            description="暂无可用建议"
          />
          <template v-else>
            <el-table :data="optimizationView.disputedRules" stripe size="small">
              <el-table-column prop="category" label="类别" width="140" />
              <el-table-column prop="actionSuggested" label="建议动作" width="140" />
              <el-table-column prop="total" label="样本数" width="100" />
              <el-table-column prop="rejected" label="拒绝" width="100" />
              <el-table-column prop="conflict" label="冲突" width="100" />
              <el-table-column label="争议率" width="120">
                <template #default="scope">
                  {{ `${(Number(scope.row.disputeRate || 0) * 100).toFixed(2)}%` }}
                </template>
              </el-table-column>
            </el-table>
            <div class="suggestion-list">
              <el-tag
                v-for="item in optimizationView.thresholdSuggestions || []"
                :key="`${item.category}-${item.suggestion}`"
                type="warning"
                effect="light"
              >
                {{ item.category }}：{{ item.suggestion }}
              </el-tag>
            </div>
          </template>
        </el-card>

        <el-card>
          <el-table
            :data="reviews"
            style="width: 100%"
            stripe
            v-loading="loading"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="jobRunId" label="Run ID" width="120" />
            <el-table-column prop="tableName" label="表名" min-width="160" />
            <el-table-column prop="columnName" label="字段" min-width="140" />
            <el-table-column label="判定" width="120">
              <template #default="scope">
                {{ formatVerdict(scope.row.verdict) }}
              </template>
            </el-table-column>
            <el-table-column label="建议动作" width="140">
              <template #default="scope">
                {{ formatActionSuggested(scope.row.actionSuggested) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="scope">
                <el-tag :type="statusTag(scope.row.status)" size="small">
                  {{ formatReviewStatus(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="420" fixed="right">
              <template #default="scope">
                <el-button size="small" type="primary" @click="openDetail(scope.row)">
                  详情
                </el-button>
                <el-button
                  size="small"
                  type="warning"
                  :disabled="scope.row.status !== 'PENDING'"
                  @click="handleRejudge(scope.row)"
                >
                  改判
                </el-button>
                <el-button
                  size="small"
                  :disabled="scope.row.status !== 'PENDING'"
                  @click="handleTransfer(scope.row)"
                >
                  转交
                </el-button>
                <el-button
                  size="small"
                  type="success"
                  :disabled="scope.row.status !== 'PENDING'"
                  @click="handleApprove(scope.row)"
                >
                  通过
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  :disabled="scope.row.status !== 'PENDING'"
                  @click="handleReject(scope.row)"
                >
                  拒绝
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="pagination.pageNum"
              v-model:page-size="pagination.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :small="false"
              layout="total, sizes, prev, pager, next, jumper"
              :total="pagination.total"
              @size-change="handlePageSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </el-card>
      </main>

      <el-drawer v-model="detailVisible" size="48%" title="审核详情" :with-header="true">
        <div v-if="currentTask" class="detail-body">
          <div class="detail-meta">
            <div>
              <strong>表</strong>
              <span>{{ currentTask.tableName }}</span>
            </div>
            <div>
              <strong>字段</strong>
              <span>{{ currentTask.columnName }}</span>
            </div>
            <div>
              <strong>建议动作</strong>
              <span>{{ formatActionSuggested(currentTask.actionSuggested) }}</span>
            </div>
          </div>
          <div class="diff-grid">
            <div class="diff-panel">
              <h3>原文</h3>
              <pre v-html="diffOriginal"></pre>
            </div>
            <div class="diff-panel">
              <h3>脱敏后</h3>
              <pre v-html="diffSanitized"></pre>
            </div>
          </div>
        </div>
      </el-drawer>
    </div>
  </BaseLayout>
</template>

<script setup>
  import { computed, onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { Refresh } from '@element-plus/icons-vue';
  import cleaningService from '@/services/cleaning';
  import BaseLayout from '@/layouts/BaseLayout.vue';

  const reviews = ref([]);
  const loading = ref(false);
  const loadingOptimization = ref(false);
  const selectedRows = ref([]);
  const detailVisible = ref(false);
  const currentTask = ref(null);
  const optimizationView = ref(null);
  const filters = ref({
    status: 'PENDING',
    jobRunId: '',
  });

  const pagination = ref({
    pageNum: 1,
    pageSize: 20,
    total: 0,
    totalPages: 0,
  });

  const statusTag = status => {
    if (status === 'PENDING') return 'warning';
    if (status === 'WRITTEN') return 'success';
    if (status === 'REJECTED') return 'info';
    if (status === 'CONFLICT') return 'danger';
    if (status === 'FAILED') return 'danger';
    return 'info';
  };

  const reviewStatusLabelMap = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    WRITTEN: '已写回',
    CONFLICT: '冲突',
    FAILED: '失败',
  };

  const verdictLabelMap = {
    ALLOW: '放行',
    BLOCK: '拦截',
    REVIEW: '人审',
    REDACTED: '已脱敏',
  };

  const actionLabelMap = {
    NONE: '不处理',
    WRITEBACK: '写回',
    SOFT_DELETE: '软删除',
    HARD_DELETE: '硬删除',
    BLOCK_ONLY: '仅阻断',
    REVIEW_ONLY: '仅人审',
    REVIEW_THEN_WRITEBACK: '人审后写回',
    REVIEW_ROLLBACK_CONFLICT: '冲突转审',
  };

  const formatReviewStatus = status => reviewStatusLabelMap[status] || status || '-';

  const formatVerdict = verdict => verdictLabelMap[verdict] || verdict || '-';

  const formatActionSuggested = action => actionLabelMap[action] || action || '-';

  const loadReviews = async (resetPage = false) => {
    if (resetPage) {
      pagination.value.pageNum = 1;
    }
    loading.value = true;
    try {
      const params = {
        pageNum: pagination.value.pageNum,
        pageSize: pagination.value.pageSize,
      };
      if (filters.value.status) params.status = filters.value.status;
      if (filters.value.jobRunId) {
        const jobRunId = Number(filters.value.jobRunId);
        if (!Number.isNaN(jobRunId)) {
          params.jobRunId = jobRunId;
        }
      }
      const pageResult = await cleaningService.listReviews(params);
      reviews.value = pageResult?.data || [];
      pagination.value.total = pageResult?.total || 0;
      pagination.value.pageNum = pageResult?.pageNum || pagination.value.pageNum;
      pagination.value.pageSize = pageResult?.pageSize || pagination.value.pageSize;
      pagination.value.totalPages = pageResult?.totalPages || 0;
      if (
        pagination.value.totalPages > 0 &&
        pagination.value.pageNum > pagination.value.totalPages
      ) {
        pagination.value.pageNum = pagination.value.totalPages;
        await loadReviews(false);
        return;
      }
      selectedRows.value = [];
    } catch (error) {
      ElMessage.error('加载审核任务失败');
    } finally {
      loading.value = false;
    }
  };

  const handleQuery = async () => {
    await loadReviews(true);
    await loadOptimizationSuggestions();
  };

  const handlePageChange = async pageNum => {
    pagination.value.pageNum = pageNum;
    await loadReviews(false);
  };

  const handlePageSizeChange = async pageSize => {
    pagination.value.pageSize = pageSize;
    pagination.value.pageNum = 1;
    await loadReviews(false);
  };

  const handleSelectionChange = rows => {
    selectedRows.value = rows || [];
  };

  const openDetail = row => {
    currentTask.value = row;
    detailVisible.value = true;
  };

  const handleApprove = async row => {
    try {
      const reason = await promptReason('通过原因（可选）');
      await cleaningService.approveReview(row.id, {
        version: row.version,
        reason,
        reviewer: 'admin',
      });
      await loadReviews();
      ElMessage.success('审核通过');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('审核失败');
      }
    }
  };

  const handleReject = async row => {
    try {
      const reason = await promptReason('拒绝原因（可选）');
      await cleaningService.rejectReview(row.id, {
        version: row.version,
        reason,
        reviewer: 'admin',
      });
      await loadReviews();
      ElMessage.success('已拒绝');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('拒绝失败');
      }
    }
  };

  const handleRejudge = async row => {
    try {
      const actionInput = await ElMessageBox.prompt(
        '请输入改判后的动作（WRITEBACK / SOFT_DELETE / HARD_DELETE / BLOCK_ONLY / REVIEW_ONLY）',
        '改判',
        {
          confirmButtonText: '下一步',
          cancelButtonText: '取消',
          inputValue: row.actionSuggested || 'WRITEBACK',
        },
      );
      const actionSuggested = String(actionInput.value || '')
        .trim()
        .toUpperCase();
      if (!actionSuggested) {
        ElMessage.error('改判动作不能为空');
        return;
      }
      const reason = await promptReason('改判原因（可选）');
      await cleaningService.rejudgeReview(row.id, {
        version: row.version,
        reason,
        reviewer: 'admin',
        actionSuggested,
      });
      await loadReviews();
      ElMessage.success('改判完成');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('改判失败');
      }
    }
  };

  const handleTransfer = async row => {
    try {
      const targetInput = await ElMessageBox.prompt('请输入目标审核人', '转交', {
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
        inputValue: row.reviewer || 'reviewer',
      });
      const targetReviewer = String(targetInput.value || '').trim();
      if (!targetReviewer) {
        ElMessage.error('目标审核人不能为空');
        return;
      }
      const reason = await promptReason('转交原因（可选）');
      await cleaningService.transferReview(row.id, {
        version: row.version,
        reason,
        reviewer: 'admin',
        targetReviewer,
      });
      await loadReviews();
      ElMessage.success('已转交');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('转交失败');
      }
    }
  };

  const handleEscalateOverdue = async () => {
    try {
      const { value } = await ElMessageBox.prompt('输入超时小时数（默认 24）', '升级超时任务', {
        confirmButtonText: '执行升级',
        cancelButtonText: '取消',
        inputPattern: /^\d*$/,
        inputErrorMessage: '请输入数字',
      });
      const overdueHours = Number(value || 24);
      const result = await cleaningService.escalateOverdueReviews({
        overdueHours: overdueHours > 0 ? overdueHours : 24,
        limit: 200,
        reviewer: 'sla-bot',
        reason: `AUTO_ESCALATED: pending over ${overdueHours > 0 ? overdueHours : 24}h`,
      });
      ElMessage.success(
        `升级完成：候选 ${result?.totalCandidates || 0}，成功 ${result?.escalated || 0}，跳过 ${
          result?.skipped || 0
        }`,
      );
      await loadReviews(false);
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('升级超时任务失败');
      }
    }
  };

  const loadOptimizationSuggestions = async () => {
    loadingOptimization.value = true;
    try {
      const params = {};
      if (filters.value.jobRunId) {
        const jobRunId = Number(filters.value.jobRunId);
        if (!Number.isNaN(jobRunId)) {
          params.jobRunId = jobRunId;
        }
      }
      optimizationView.value = await cleaningService.getReviewOptimizationSuggestions(params);
    } catch (error) {
      ElMessage.error('加载优化建议失败');
      optimizationView.value = null;
    } finally {
      loadingOptimization.value = false;
    }
  };

  const handleBatchApprove = async () => {
    try {
      const reason = await promptReason('批量通过原因（可选）');
      const result = await cleaningService.batchApprove({
        taskIds: selectedRows.value.map(row => row.id),
        reason,
        reviewer: 'admin',
      });
      await loadReviews();
      ElMessage.success(`批量通过完成，成功 ${result?.success || 0} 条`);
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('批量通过失败');
      }
    }
  };

  const handleBatchReject = async () => {
    try {
      const reason = await promptReason('批量拒绝原因（可选）');
      const result = await cleaningService.batchReject({
        taskIds: selectedRows.value.map(row => row.id),
        reason,
        reviewer: 'admin',
      });
      await loadReviews();
      ElMessage.success(`批量拒绝完成，成功 ${result?.success || 0} 条`);
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('批量拒绝失败');
      }
    }
  };

  const handleBatchAllApprove = async () => {
    if (!filters.value.jobRunId) {
      return;
    }
    try {
      await ElMessageBox.confirm('确认对该 Job Run 的全部待审记录执行通过？', '批量确认', {
        type: 'warning',
      });
      const reason = await promptReason('批量通过原因（可选）');
      const result = await cleaningService.batchApprove({
        jobRunId: Number(filters.value.jobRunId),
        filter: 'ALL_PENDING',
        reason,
        reviewer: 'admin',
      });
      await loadReviews();
      ElMessage.success(`全量通过完成，成功 ${result?.success || 0} 条`);
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('全量通过失败');
      }
    }
  };

  const handleBatchAllReject = async () => {
    if (!filters.value.jobRunId) {
      return;
    }
    try {
      await ElMessageBox.confirm('确认对该 Job Run 的全部待审记录执行拒绝？', '批量确认', {
        type: 'warning',
      });
      const reason = await promptReason('批量拒绝原因（可选）');
      const result = await cleaningService.batchReject({
        jobRunId: Number(filters.value.jobRunId),
        filter: 'ALL_PENDING',
        reason,
        reviewer: 'admin',
      });
      await loadReviews();
      ElMessage.success(`全量拒绝完成，成功 ${result?.success || 0} 条`);
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('全量拒绝失败');
      }
    }
  };

  const promptReason = async title => {
    const result = await ElMessageBox.prompt(title, '审核备注', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '输入原因或留空',
    });
    return result.value || '';
  };

  const parseJsonSafe = value => {
    if (!value) {
      return {};
    }
    try {
      return JSON.parse(value);
    } catch (error) {
      return {};
    }
  };

  const resolveDiffText = task => {
    if (!task) {
      return { original: '', sanitized: '' };
    }
    const beforeRow = parseJsonSafe(task.beforeRowJson);
    const writeback = parseJsonSafe(task.writebackPayloadJson);
    const columnName = task.columnName;
    let original = beforeRow[columnName];
    if (original === undefined) {
      original = Object.values(beforeRow)[0];
    }
    let sanitized = writeback[columnName];
    if (sanitized === undefined) {
      sanitized = Object.values(writeback)[0];
    }
    if (sanitized === undefined || sanitized === null) {
      sanitized = task.sanitizedPreview;
    }
    return {
      original: original != null ? String(original) : '',
      sanitized: sanitized != null ? String(sanitized) : '',
    };
  };

  const buildDiffHtml = (original, sanitized, mode) => {
    const escapeHtml = text =>
      text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    if (!original && !sanitized) {
      return '';
    }
    const prefixLength = (() => {
      const min = Math.min(original.length, sanitized.length);
      let i = 0;
      while (i < min && original[i] === sanitized[i]) {
        i++;
      }
      return i;
    })();
    const suffixLength = (() => {
      const min = Math.min(original.length, sanitized.length);
      let i = 0;
      while (
        i < min - prefixLength &&
        original[original.length - 1 - i] === sanitized[sanitized.length - 1 - i]
      ) {
        i++;
      }
      return i;
    })();
    const originalMid = original.slice(prefixLength, original.length - suffixLength);
    const sanitizedMid = sanitized.slice(prefixLength, sanitized.length - suffixLength);
    if (mode === 'original') {
      return (
        escapeHtml(original.slice(0, prefixLength)) +
        `<span class="diff-removed">${escapeHtml(originalMid)}</span>` +
        escapeHtml(original.slice(original.length - suffixLength))
      );
    }
    return (
      escapeHtml(sanitized.slice(0, prefixLength)) +
      `<span class="diff-added">${escapeHtml(sanitizedMid)}</span>` +
      escapeHtml(sanitized.slice(sanitized.length - suffixLength))
    );
  };

  const diffOriginal = computed(() => {
    const diff = resolveDiffText(currentTask.value);
    return buildDiffHtml(diff.original, diff.sanitized, 'original');
  });

  const diffSanitized = computed(() => {
    const diff = resolveDiffText(currentTask.value);
    return buildDiffHtml(diff.original, diff.sanitized, 'sanitized');
  });

  onMounted(() => {
    loadReviews();
    loadOptimizationSuggestions();
  });
</script>

<style scoped>
  .review-workbench {
    min-height: 100vh;
    padding: 2rem;
  }

  .main-content {
    max-width: 1200px;
    margin: 0 auto;
  }

  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1.5rem;
  }

  .content-title {
    font-size: 1.75rem;
    font-weight: 600;
    color: #0f172a;
    margin: 0;
  }

  .content-subtitle {
    margin-top: 0.5rem;
    color: #64748b;
  }

  .filter-card {
    margin-bottom: 1rem;
  }

  .optimization-card {
    margin-bottom: 1rem;
  }

  .optimization-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .optimization-meta {
    color: #64748b;
    font-size: 13px;
  }

  .suggestion-list {
    margin-top: 10px;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .filter-row {
    display: flex;
    gap: 1rem;
    align-items: center;
    flex-wrap: wrap;
  }

  .bulk-row {
    margin-top: 1rem;
    display: flex;
    gap: 0.75rem;
    align-items: center;
    flex-wrap: wrap;
  }

  .pagination-bar {
    margin-top: 1rem;
    display: flex;
    justify-content: flex-end;
  }

  .detail-body {
    padding: 1rem 0;
  }

  .detail-meta {
    display: grid;
    gap: 0.5rem;
    margin-bottom: 1rem;
    color: #334155;
  }

  .detail-meta strong {
    margin-right: 0.5rem;
  }

  .diff-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 1rem;
  }

  .diff-panel {
    background: #f8fafc;
    border-radius: 12px;
    padding: 1rem;
  }

  .diff-panel h3 {
    margin-top: 0;
    font-size: 1rem;
    color: #0f172a;
  }

  .diff-panel pre {
    white-space: pre-wrap;
    word-break: break-word;
    font-family: 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  }

  :deep(.diff-removed) {
    background: rgba(239, 68, 68, 0.2);
    color: #b91c1c;
    padding: 0 4px;
    border-radius: 4px;
  }

  :deep(.diff-added) {
    background: rgba(34, 197, 94, 0.2);
    color: #15803d;
    padding: 0 4px;
    border-radius: 4px;
  }
</style>
