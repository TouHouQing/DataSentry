<template>
  <BaseLayout>
    <div class="cleaning-policy-page">
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">清理策略配置</h1>
            <p class="content-subtitle">管理清理策略、规则与优先级绑定</p>
          </div>
          <div class="header-actions">
            <CleaningModeSwitch @change="handleModeChange" />
            <el-button type="primary" :icon="Plus" size="large" @click="openPolicyDialog()">
              新增策略
            </el-button>
            <el-button :icon="Refresh" size="large" @click="loadAll">刷新</el-button>
            <el-button size="large" @click="openBindingDialog">在线默认绑定</el-button>
          </div>
        </div>

        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="intro-alert"
          :title="
            isBeginnerMode
              ? '新手模式：默认使用结构化表单，降低 JSON 配置门槛。'
              : '专家模式：显示完整枚举值，并保留高级 JSON 编辑能力。'
          "
        />

        <el-tabs v-model="activeTab" class="content-tabs">
          <el-tab-pane label="策略" name="policies">
            <el-card>
              <el-table :data="policies" style="width: 100%" stripe v-loading="loadingPolicies">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="策略名称" min-width="160" />
                <el-table-column label="默认动作" min-width="220">
                  <template #default="scope">
                    {{ formatDefaultAction(scope.row.defaultAction) }}
                  </template>
                </el-table-column>
                <el-table-column label="规则数" width="100">
                  <template #default="scope">
                    {{ scope.row.rules?.length || 0 }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
                      {{ scope.row.enabled ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="600" fixed="right">
                  <template #default="scope">
                    <el-button
                      size="small"
                      type="success"
                      :loading="publishLoadingMap[scope.row.id]"
                      @click="publishPolicyVersion(scope.row)"
                    >
                      发布
                    </el-button>
                    <el-button
                      size="small"
                      type="warning"
                      plain
                      :loading="grayPublishLoadingMap[scope.row.id]"
                      @click="publishPolicyGray(scope.row)"
                    >
                      灰度发布
                    </el-button>
                    <el-button size="small" type="warning" @click="offlinePolicy(scope.row)">
                      下线
                    </el-button>
                    <el-button
                      size="small"
                      :loading="versionLoadingMap[scope.row.id]"
                      @click="openVersionDialog(scope.row)"
                    >
                      版本
                    </el-button>
                    <el-button size="small" @click="openExperimentDialog(scope.row)">
                      实验
                    </el-button>
                    <el-button size="small" type="info" @click="openCopilotDialog(scope.row)">
                      AI建议
                    </el-button>
                    <el-button size="small" type="primary" @click="openPolicyDialog(scope.row)">
                      编辑
                    </el-button>
                    <el-button size="small" type="danger" @click="deletePolicy(scope.row)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="模板库" name="templates">
            <el-card>
              <div class="rule-toolbar">
                <el-button type="primary" :icon="Plus" size="large" @click="openTemplateDialog()">
                  新增模板
                </el-button>
              </div>
              <el-table
                :data="policyTemplates"
                style="width: 100%"
                stripe
                v-loading="loadingPolicyTemplates"
              >
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="模板名称" min-width="160" />
                <el-table-column prop="category" label="分类" min-width="140" />
                <el-table-column label="默认动作" min-width="220">
                  <template #default="scope">
                    {{ formatDefaultAction(scope.row.defaultAction) }}
                  </template>
                </el-table-column>
                <el-table-column label="规则数" width="100">
                  <template #default="scope">
                    {{ scope.row.rules?.length || 0 }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
                      {{ scope.row.enabled ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="320" fixed="right">
                  <template #default="scope">
                    <el-button size="small" type="success" @click="cloneFromTemplate(scope.row)">
                      克隆为策略
                    </el-button>
                    <el-button size="small" type="primary" @click="openTemplateDialog(scope.row)">
                      编辑
                    </el-button>
                    <el-button size="small" type="danger" @click="deleteTemplate(scope.row)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="规则" name="rules">
            <el-card>
              <div class="rule-toolbar">
                <el-button type="primary" :icon="Plus" size="large" @click="openRuleDialog()">
                  新增规则
                </el-button>
              </div>
              <el-table :data="rules" style="width: 100%" stripe v-loading="loadingRules">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="规则名称" min-width="160" />
                <el-table-column label="类型" width="180">
                  <template #default="scope">
                    {{ formatRuleType(scope.row.ruleType) }}
                  </template>
                </el-table-column>
                <el-table-column label="类别" width="180">
                  <template #default="scope">
                    {{ formatRuleCategory(scope.row.category) }}
                  </template>
                </el-table-column>
                <el-table-column prop="severity" label="严重度" width="120" />
                <el-table-column label="状态" width="100">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
                      {{ scope.row.enabled ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="240" fixed="right">
                  <template #default="scope">
                    <el-button size="small" type="primary" @click="openRuleDialog(scope.row)">
                      编辑
                    </el-button>
                    <el-button size="small" type="danger" @click="deleteRule(scope.row)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </main>

      <el-dialog
        v-model="policyDialogVisible"
        :title="policyDialogTitle"
        width="920px"
        :close-on-click-modal="false"
      >
        <el-form :model="policyForm" label-width="120px" label-position="left">
          <el-form-item label="策略名称">
            <el-input v-model="policyForm.name" placeholder="请输入策略名称" />
          </el-form-item>

          <el-form-item label="策略描述">
            <el-input v-model="policyForm.description" placeholder="请输入描述" />
          </el-form-item>

          <el-form-item label="默认动作">
            <el-select v-model="policyForm.defaultAction" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="item in defaultActionOptions"
                :key="item.code"
                :label="formatOptionLabel(item)"
                :value="item.code"
              />
            </el-select>
          </el-form-item>

          <el-alert
            v-if="selectedDefaultActionHint"
            type="info"
            :closable="false"
            show-icon
            :title="selectedDefaultActionHint"
            class="inline-alert"
          />

          <el-form-item label="启用状态">
            <el-switch v-model="policyForm.enabled" />
          </el-form-item>

          <el-divider>策略阈值配置</el-divider>
          <div class="threshold-row">
            <el-form-item label="Block 阈值">
              <el-input-number v-model="policyForm.blockThreshold" :min="0" :max="1" :step="0.05" />
              <div class="field-help">{{ getThresholdHint('blockThreshold') }}</div>
            </el-form-item>
            <el-form-item label="Review 阈值">
              <el-input-number
                v-model="policyForm.reviewThreshold"
                :min="0"
                :max="1"
                :step="0.05"
              />
              <div class="field-help">{{ getThresholdHint('reviewThreshold') }}</div>
            </el-form-item>
            <el-form-item label="L3 启用">
              <el-switch v-model="policyForm.llmEnabled" />
            </el-form-item>
            <el-form-item label="L2 阈值">
              <el-input-number v-model="policyForm.l2Threshold" :min="0" :max="1" :step="0.05" />
              <div class="field-help">{{ getThresholdHint('l2Threshold') }}</div>
            </el-form-item>
            <el-form-item label="Shadow 启用">
              <el-switch v-model="policyForm.shadowEnabled" />
            </el-form-item>
            <el-form-item label="Shadow 采样">
              <el-input-number
                v-model="policyForm.shadowSampleRatio"
                :min="0"
                :max="1"
                :step="0.05"
              />
              <div class="field-help">{{ getThresholdHint('shadowSampleRatio') }}</div>
            </el-form-item>
          </div>

          <el-divider>规则绑定与优先级</el-divider>
          <el-table :data="ruleSelections" style="width: 100%" height="280">
            <el-table-column width="60">
              <template #default="scope">
                <el-checkbox v-model="scope.row.selected" />
              </template>
            </el-table-column>
            <el-table-column prop="name" label="规则名称" min-width="160" />
            <el-table-column label="类型" width="180">
              <template #default="scope">
                {{ formatRuleType(scope.row.ruleType) }}
              </template>
            </el-table-column>
            <el-table-column label="类别" width="180">
              <template #default="scope">
                {{ formatRuleCategory(scope.row.category) }}
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="140">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.priority"
                  :min="0"
                  :max="100"
                  :step="1"
                  size="small"
                  :disabled="!scope.row.selected"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-form>
        <template #footer>
          <el-button @click="policyDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="savePolicy">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="experimentDialogVisible"
        :title="`实验记录${selectedVersionPolicy ? ` - ${selectedVersionPolicy.name}` : ''}`"
        width="860px"
        :close-on-click-modal="false"
      >
        <el-alert
          v-if="policyShadowSummary"
          type="info"
          :closable="false"
          show-icon
          class="inline-alert"
          :title="`Shadow 对比：总样本 ${policyShadowSummary.totalRecords || 0}，差异 ${
            policyShadowSummary.diffRecords || 0
          }（${Number(policyShadowSummary.diffRate || 0).toFixed(2)}%），高/中/低 = ${
            policyShadowSummary.highDiffRecords || 0
          }/${policyShadowSummary.mediumDiffRecords || 0}/${policyShadowSummary.lowDiffRecords || 0}`"
        />
        <el-table
          :data="policyExperiments"
          style="width: 100%"
          stripe
          v-loading="loadingPolicyExperiments"
        >
          <el-table-column prop="action" label="动作" width="130" />
          <el-table-column prop="publishMode" label="发布模式" width="120" />
          <el-table-column label="灰度比例" width="120">
            <template #default="scope">
              {{
                scope.row.grayRatio !== undefined && scope.row.grayRatio !== null
                  ? `${(Number(scope.row.grayRatio) * 100).toFixed(2)}%`
                  : '-'
              }}
            </template>
          </el-table-column>
          <el-table-column prop="experimentName" label="实验名称" min-width="180" />
          <el-table-column prop="operator" label="操作者" width="120" />
          <el-table-column prop="note" label="备注" min-width="160" />
          <el-table-column label="创建时间" min-width="180">
            <template #default="scope">
              {{ formatDateTime(scope.row.createdTime) }}
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="experimentDialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="loadingPolicyExperiments"
            @click="loadPolicyExperiments(selectedVersionPolicy?.id)"
          >
            刷新
          </el-button>
          <el-button
            :loading="loadingPolicyShadowSummary"
            @click="loadPolicyShadowSummary(selectedVersionPolicy?.id)"
          >
            刷新 Shadow 摘要
          </el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="bindingDialogVisible"
        title="在线默认策略绑定"
        width="640px"
        :close-on-click-modal="false"
      >
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="inline-alert"
          title="用于实时 check/sanitize 调用的默认策略。不设置时会走系统兜底策略。"
        />
        <el-form :model="bindingForm" label-width="130px" label-position="left">
          <el-form-item label="智能体">
            <el-select
              v-model="bindingForm.agentId"
              filterable
              clearable
              style="width: 100%"
              @change="onBindingAgentChange"
            >
              <el-option
                v-for="agent in bindingAgents"
                :key="agent.id"
                :label="agent.name || `Agent-${agent.id}`"
                :value="agent.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="默认策略">
            <el-select v-model="bindingForm.policyId" filterable clearable style="width: 100%">
              <el-option
                v-for="policy in policies"
                :key="policy.id"
                :label="`${policy.name} (#${policy.id})`"
                :value="policy.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="启用状态">
            <el-switch v-model="bindingForm.enabled" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="bindingDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveBinding">保存绑定</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="versionDialogVisible"
        :title="`策略版本历史${selectedVersionPolicy ? ` - ${selectedVersionPolicy.name}` : ''}`"
        width="820px"
        :close-on-click-modal="false"
        @closed="closeVersionDialog"
      >
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="inline-alert"
          title="发布会创建新版本快照；回滚会将目标版本重新置为已发布。"
        />
        <el-table
          :data="policyVersions"
          style="width: 100%"
          stripe
          v-loading="loadingPolicyVersions"
        >
          <el-table-column prop="versionNo" label="版本号" width="100" />
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag :type="formatVersionStatusTag(scope.row.status)" size="small">
                {{ formatVersionStatus(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="默认动作" min-width="160">
            <template #default="scope">
              {{ formatDefaultAction(scope.row.defaultAction) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="scope">
              {{ formatDateTime(scope.row.updatedTime || scope.row.createdTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="scope">
              <el-button
                size="small"
                type="warning"
                :disabled="scope.row.status === 'PUBLISHED'"
                :loading="rollbackVersionLoadingMap[scope.row.id]"
                @click="rollbackPolicyVersion(scope.row)"
              >
                回滚到此版本
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="versionDialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="refreshVersionsLoading"
            @click="loadPolicyVersions(selectedVersionPolicy?.id)"
          >
            刷新
          </el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="copilotDialogVisible"
        :title="`AI 策略建议${selectedCopilotPolicy ? ` - ${selectedCopilotPolicy.name}` : ''}`"
        width="760px"
        :close-on-click-modal="false"
      >
        <div v-loading="copilotLoading">
          <el-empty v-if="!copilotSuggestion" description="暂无建议，请先积累审核反馈样本后再试" />
          <template v-else>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="样本量">
                {{ copilotSuggestion.sampleSize || 0 }}
              </el-descriptions-item>
              <el-descriptions-item label="争议率">
                {{ `${(Number(copilotSuggestion.disputeRate || 0) * 100).toFixed(2)}%` }}
              </el-descriptions-item>
              <el-descriptions-item label="建议档位">
                {{ copilotSuggestion.recommendationLevel || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="建议默认动作">
                {{ formatDefaultAction(copilotSuggestion.recommendedDefaultAction) }}
              </el-descriptions-item>
              <el-descriptions-item label="建议规则数">
                {{ copilotSuggestion.recommendedRuleIds?.length || 0 }}
              </el-descriptions-item>
              <el-descriptions-item label="生成时间">
                {{ formatDateTime(copilotSuggestion.generatedTime) }}
              </el-descriptions-item>
            </el-descriptions>

            <el-divider>建议说明</el-divider>
            <el-alert
              v-for="insight in copilotSuggestion.insights || []"
              :key="insight"
              type="info"
              :closable="false"
              show-icon
              class="inline-alert"
              :title="insight"
            />
          </template>
        </div>
        <template #footer>
          <el-button @click="copilotDialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :disabled="!copilotSuggestion || !selectedCopilotPolicy"
            @click="applyCopilotSuggestion"
          >
            应用建议到草稿
          </el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="templateDialogVisible"
        :title="templateDialogTitle"
        width="900px"
        :close-on-click-modal="false"
      >
        <el-form :model="templateForm" label-width="120px" label-position="left">
          <el-form-item label="模板名称">
            <el-input v-model="templateForm.name" placeholder="请输入模板名称" />
          </el-form-item>
          <el-form-item label="模板分类">
            <el-input
              v-model="templateForm.category"
              placeholder="例如：客服文本 / 用户资料 / 营销内容"
            />
          </el-form-item>
          <el-form-item label="模板描述">
            <el-input v-model="templateForm.description" placeholder="请输入模板描述" />
          </el-form-item>
          <el-form-item label="默认动作">
            <el-select
              v-model="templateForm.defaultAction"
              placeholder="请选择"
              style="width: 100%"
            >
              <el-option
                v-for="item in defaultActionOptions"
                :key="item.code"
                :label="formatOptionLabel(item)"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="templateForm.enabled" />
          </el-form-item>
          <el-form-item label="配置 JSON">
            <el-input
              v-model="templateForm.configJson"
              type="textarea"
              :rows="6"
              placeholder="请输入模板配置 JSON，留空默认 {}"
            />
          </el-form-item>
          <el-divider>模板规则绑定</el-divider>
          <el-table :data="templateRuleSelections" style="width: 100%" height="260">
            <el-table-column width="60">
              <template #default="scope">
                <el-checkbox v-model="scope.row.selected" />
              </template>
            </el-table-column>
            <el-table-column prop="name" label="规则名称" min-width="160" />
            <el-table-column label="类型" width="180">
              <template #default="scope">
                {{ formatRuleType(scope.row.ruleType) }}
              </template>
            </el-table-column>
            <el-table-column label="类别" width="180">
              <template #default="scope">
                {{ formatRuleCategory(scope.row.category) }}
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="140">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.priority"
                  :min="0"
                  :max="100"
                  :step="1"
                  size="small"
                  :disabled="!scope.row.selected"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-form>
        <template #footer>
          <el-button @click="templateDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveTemplate">保存模板</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="ruleDialogVisible"
        :title="ruleDialogTitle"
        width="760px"
        :close-on-click-modal="false"
      >
        <el-form :model="ruleForm" label-width="120px" label-position="left">
          <el-form-item label="规则名称">
            <el-input v-model="ruleForm.name" placeholder="请输入规则名称" />
          </el-form-item>

          <el-form-item label="规则类型">
            <el-select
              v-model="ruleForm.ruleType"
              placeholder="请选择"
              style="width: 100%"
              @change="onRuleTypeChange"
            >
              <el-option
                v-for="item in ruleTypeOptions"
                :key="item.code"
                :label="formatOptionLabel(item)"
                :value="item.code"
              />
            </el-select>
            <div class="field-help">{{ selectedRuleTypeHelp }}</div>
          </el-form-item>

          <el-form-item label="规则类别">
            <el-select
              v-model="ruleForm.categoryPreset"
              style="width: 100%"
              placeholder="请选择规则类别"
            >
              <el-option
                v-for="item in ruleCategoryOptions"
                :key="item.code"
                :label="formatOptionLabel(item)"
                :value="item.code"
              />
              <el-option label="自定义类别" value="__CUSTOM__" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="ruleForm.categoryPreset === '__CUSTOM__'" label="自定义类别">
            <el-input v-model="ruleForm.customCategory" placeholder="请输入自定义规则类别" />
          </el-form-item>

          <el-form-item label="严重度">
            <div class="severity-editor">
              <el-input-number v-model="ruleForm.severity" :min="0" :max="1" :step="0.05" />
              <el-slider v-model="ruleForm.severity" :min="0" :max="1" :step="0.01" />
              <span class="severity-percent">{{ severityPercentLabel }}</span>
            </div>
            <div class="field-help">{{ severityHint }}</div>
          </el-form-item>

          <el-form-item label="启用状态">
            <el-switch v-model="ruleForm.enabled" />
          </el-form-item>

          <template v-if="ruleForm.ruleType === 'REGEX'">
            <el-divider content-position="left">正则配置（结构化）</el-divider>

            <el-form-item label="快速模板">
              <el-select
                v-model="ruleForm.regexTemplateCode"
                clearable
                filterable
                style="width: 100%"
                placeholder="选择模板后自动填充 pattern/flags"
                @change="applyRegexTemplate"
              >
                <el-option
                  v-for="item in regexTemplateOptions"
                  :key="item.code"
                  :label="formatOptionLabel(item)"
                  :value="item.code"
                />
              </el-select>
              <div class="field-help">{{ regexTemplateHint }}</div>
            </el-form-item>

            <el-form-item label="pattern">
              <el-input
                v-model="ruleForm.regexPattern"
                placeholder="例如：\b1[3-9]\d{9}\b"
                @blur="normalizeRegexPatternInput"
              />
              <div class="field-help">{{ getFieldHelp('policy.regex.pattern') }}</div>
              <div v-if="regexPatternNormalizationHint" class="field-help field-help-warning">
                {{ regexPatternNormalizationHint }}
              </div>
            </el-form-item>

            <el-form-item label="flags">
              <el-select
                v-model="ruleForm.regexFlags"
                multiple
                clearable
                filterable
                style="width: 100%"
                placeholder="可多选"
              >
                <el-option
                  v-for="flag in regexFlagOptions"
                  :key="flag"
                  :label="flag"
                  :value="flag"
                />
              </el-select>
              <div class="field-help">{{ getFieldHelp('policy.regex.flags') }}</div>
            </el-form-item>

            <el-form-item label="脱敏方式">
              <el-radio-group v-model="ruleForm.regexMaskMode" @change="onRegexMaskModeChange">
                <el-radio-button
                  v-for="mode in regexMaskModeOptions"
                  :key="mode"
                  :label="mode"
                  :value="mode"
                >
                  {{ mode === 'DELETE' ? '删除命中字符' : '固定文本替换' }}
                </el-radio-button>
              </el-radio-group>
              <div class="field-help">{{ getFieldHelp('policy.regex.maskMode') }}</div>
            </el-form-item>

            <el-form-item v-if="ruleForm.regexMaskMode !== 'DELETE'" label="替换文本">
              <el-input
                v-model="ruleForm.regexMaskText"
                placeholder="例如：*** 或 [PHONE]，留空默认 [REDACTED]"
                @input="onRegexMaskTextInput"
              />
              <div class="field-help">{{ getFieldHelp('policy.regex.maskText') }}</div>
            </el-form-item>
          </template>

          <template v-else-if="ruleForm.ruleType === 'L2_DUMMY'">
            <el-form-item label="检测模式">
              <el-radio-group v-model="ruleForm.l2Mode">
                <el-radio-button label="REGEX">关键词正则</el-radio-button>
                <el-radio-button label="ENTROPY">乱码检测 (熵)</el-radio-button>
                <el-radio-button label="REPETITION">重复检测</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <template v-if="ruleForm.l2Mode === 'REGEX'">
              <el-form-item label="正则表达式">
                <el-input
                  v-model="ruleForm.regexPattern"
                  placeholder="例如：(?i)(代开票|发票|保真)"
                />
                <div class="field-help">匹配风险内容的正则表达式 (支持 Java Regex 语法)。</div>
              </el-form-item>
              <el-form-item label="风险阈值">
                <el-input-number
                  v-model="ruleForm.l2RegexThreshold"
                  :min="0"
                  :max="1"
                  :step="0.05"
                />
                <div class="field-help">命中规则后的风险评分 (0.0 - 1.0)。</div>
              </el-form-item>
            </template>

            <template v-else-if="ruleForm.l2Mode === 'ENTROPY'">
              <el-form-item label="熵值阈值">
                <el-input-number
                  v-model="ruleForm.l2EntropyThreshold"
                  :min="0"
                  :max="8"
                  :step="0.1"
                />
                <div class="field-help">
                  Shannon 熵阈值 (默认
                  4.8)。数值越高，允许的随机性越大；数值越低，越容易把随机乱码判定为异常。
                </div>
              </el-form-item>
            </template>

            <template v-else-if="ruleForm.l2Mode === 'REPETITION'">
              <el-form-item label="最大重复次数">
                <el-input-number v-model="ruleForm.l2MaxRepetition" :min="1" :max="100" :step="1" />
                <div class="field-help">
                  允许的最大重复字符数 (默认 10)。例如设为 5，则 "aaaaaa" (6个a) 会被判定为异常。
                </div>
              </el-form-item>
            </template>
          </template>

          <template v-else-if="ruleForm.ruleType === 'LLM'">
            <el-form-item label="提示词 (Prompt)">
              <el-input
                v-model="ruleForm.llmPrompt"
                type="textarea"
                :rows="6"
                placeholder="请输入自定义提示词，留空则使用系统默认提示词"
              />
              <div class="field-help">用于指导大模型进行数据清洗的系统提示词。</div>
            </el-form-item>
          </template>

          <el-alert
            v-else-if="isBeginnerMode && !ruleForm.showAdvancedConfig"
            type="info"
            :closable="false"
            show-icon
            title="当前规则类型无必填配置项，如需自定义参数可展开高级 JSON。"
            class="inline-alert"
          />

          <el-form-item v-if="allowAdvancedConfig" label="高级配置">
            <el-button text type="primary" @click="toggleAdvancedConfig">
              {{ ruleForm.showAdvancedConfig ? '收起高级 JSON' : '展开高级 JSON' }}
            </el-button>
            <div class="field-help">{{ getFieldHelp('policy.configJson') }}</div>
          </el-form-item>

          <el-form-item v-if="allowAdvancedConfig && ruleForm.showAdvancedConfig" label="配置 JSON">
            <el-input
              v-model="ruleForm.configJson"
              type="textarea"
              :rows="6"
              placeholder='例如: {"pattern":"\\d{11}","flags":"CASE_INSENSITIVE","maskMode":"PLACEHOLDER","maskText":"[REDACTED]"}'
            />
            <div class="config-toolbar">
              <el-button size="small" @click="fillRuleConfigTemplate">按类型填充模板</el-button>
            </div>
            <div class="field-help">{{ ruleTypeConfigHint }}</div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="ruleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveRule">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </BaseLayout>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { Plus, Refresh } from '@element-plus/icons-vue';
  import cleaningMetaService from '@/services/cleaningMeta';
  import cleaningService from '@/services/cleaning';
  import agentService from '@/services/agent';
  import {
    buildOptionLabel,
    mergeOptionsWithFallback,
    readCleaningUiMode,
    UI_MODE_BEGINNER,
    UI_MODE_EXPERT,
  } from '@/constants/cleaningLabelMaps';
  import CleaningModeSwitch from '@/components/cleaning/CleaningModeSwitch.vue';
  import BaseLayout from '@/layouts/BaseLayout.vue';

  const activeTab = ref('policies');
  const policies = ref([]);
  const rules = ref([]);
  const loadingPolicies = ref(false);
  const loadingRules = ref(false);
  const bindingAgents = ref([]);
  const publishLoadingMap = reactive({});
  const grayPublishLoadingMap = reactive({});
  const versionLoadingMap = reactive({});
  const rollbackVersionLoadingMap = reactive({});
  const versionDialogVisible = ref(false);
  const selectedVersionPolicy = ref(null);
  const policyVersions = ref([]);
  const loadingPolicyVersions = ref(false);
  const refreshVersionsLoading = ref(false);
  const experimentDialogVisible = ref(false);
  const policyExperiments = ref([]);
  const loadingPolicyExperiments = ref(false);
  const policyShadowSummary = ref(null);
  const loadingPolicyShadowSummary = ref(false);
  const copilotDialogVisible = ref(false);
  const copilotLoading = ref(false);
  const selectedCopilotPolicy = ref(null);
  const copilotSuggestion = ref(null);
  const policyTemplates = ref([]);
  const loadingPolicyTemplates = ref(false);
  const templateDialogVisible = ref(false);
  const templateDialogTitle = ref('新增模板');

  const uiMode = ref(readCleaningUiMode());
  const optionMeta = ref(mergeOptionsWithFallback(null));

  const policyDialogVisible = ref(false);
  const policyDialogTitle = ref('新增策略');
  const policyForm = reactive({
    id: null,
    name: '',
    description: '',
    defaultAction: 'DETECT_ONLY',
    enabled: true,
    blockThreshold: 0.7,
    reviewThreshold: 0.4,
    llmEnabled: true,
    l2Threshold: 0.6,
    shadowEnabled: false,
    shadowSampleRatio: 0,
  });
  const ruleSelections = ref([]);
  const templateRuleSelections = ref([]);
  const templateForm = reactive({
    id: null,
    name: '',
    description: '',
    category: '',
    defaultAction: 'DETECT_ONLY',
    enabled: true,
    configJson: '{}',
  });

  const bindingDialogVisible = ref(false);
  const bindingForm = reactive({
    agentId: undefined,
    policyId: undefined,
    enabled: true,
  });

  const ruleDialogVisible = ref(false);
  const ruleDialogTitle = ref('新增规则');
  const ruleForm = reactive({
    id: null,
    name: '',
    ruleType: 'REGEX',
    categoryPreset: '',
    customCategory: '',
    severity: 0.8,
    enabled: true,
    regexTemplateCode: '',
    regexPattern: '',
    regexFlags: ['CASE_INSENSITIVE'],
    regexMaskMode: 'PLACEHOLDER',
    regexMaskText: '[REDACTED]',
    llmPrompt: '',
    l2Mode: 'REGEX', // REGEX, ENTROPY, REPETITION
    l2EntropyThreshold: 4.8,
    l2MaxRepetition: 10,
    l2RegexThreshold: 0.8,
    showAdvancedConfig: false,
    configJson: '',
  });

  const isBeginnerMode = computed(() => uiMode.value === UI_MODE_BEGINNER);
  const defaultActionOptions = computed(() => optionMeta.value.defaultActions || []);
  const ruleTypeOptions = computed(() => optionMeta.value.ruleTypes || []);
  const ruleCategoryOptions = computed(() => optionMeta.value.ruleCategories || []);
  const thresholdGuidance = computed(() => optionMeta.value.thresholdGuidance || []);
  const severityGuidance = computed(() => optionMeta.value.severityGuidance || []);
  const regexTemplateOptions = computed(() => optionMeta.value.regexTemplates || []);
  const riskConfirmations = computed(() => optionMeta.value.riskConfirmations || {});

  const selectedDefaultActionHint = computed(() => {
    const matched = defaultActionOptions.value.find(item => item.code === policyForm.defaultAction);
    if (!matched) {
      return '';
    }
    const effect = matched.effect ? `；作用：${matched.effect}` : '';
    const risk = matched.riskLevel ? `；风险：${matched.riskLevel}` : '';
    const recommended = matched.recommendedFor ? `；适用：${matched.recommendedFor}` : '';
    return `${matched.labelZh || matched.code}：${matched.description || ''}${effect}${risk}${recommended}`;
  });

  const ruleTypeSchema = computed(() => optionMeta.value.ruleTypeSchemas?.[ruleForm.ruleType]);

  const ruleTypeBehavior = computed(() => {
    const fallback = {
      showStructuredConfig: ruleForm.ruleType === 'REGEX',
      showAdvancedJson: true,
    };
    return optionMeta.value.ruleTypeUiBehavior?.[ruleForm.ruleType] || fallback;
  });

  const allowAdvancedConfig = computed(() => ruleTypeBehavior.value.showAdvancedJson !== false);

  const regexFlagOptions = computed(() => {
    const fields = ruleTypeSchema.value?.fields || [];
    const flagField = fields.find(item => item.name === 'flags');
    if (flagField?.options?.length) {
      return flagField.options;
    }
    return ['CASE_INSENSITIVE', 'MULTILINE', 'DOTALL'];
  });

  const regexMaskModeOptions = computed(() => {
    const fields = ruleTypeSchema.value?.fields || [];
    const maskModeField = fields.find(item => item.name === 'maskMode');
    if (maskModeField?.options?.length) {
      return maskModeField.options;
    }
    return ['PLACEHOLDER', 'DELETE'];
  });

  const selectedRuleTypeHelp = computed(() => {
    const schemaDescription = ruleTypeSchema.value?.description;
    if (schemaDescription) {
      return schemaDescription;
    }
    return ruleTypeOptions.value.find(item => item.code === ruleForm.ruleType)?.description || '';
  });

  const regexTemplateHint = computed(() => {
    if (!ruleForm.regexTemplateCode) {
      return '可选择模板快速生成 pattern 与 flags，再按业务微调。';
    }
    const template = regexTemplateOptions.value.find(
      item => item.code === ruleForm.regexTemplateCode,
    );
    if (!template) {
      return '可选择模板快速生成 pattern 与 flags，再按业务微调。';
    }
    const example = template.effect ? `；示例：${template.effect}` : '';
    return `${template.description || ''}${example}`;
  });

  const regexPatternNormalizationHint = computed(() => {
    if (ruleForm.ruleType !== 'REGEX') {
      return '';
    }
    const original = String(ruleForm.regexPattern || '');
    if (!original) {
      return '';
    }
    const normalized = normalizeRegexPatternText(original);
    if (normalized === original) {
      return '';
    }
    return `检测到双反斜杠写法，保存时会自动规范化为：${normalized}`;
  });

  const severityPercentLabel = computed(() => `${Math.round((ruleForm.severity || 0) * 100)} 分`);

  const severityHint = computed(() => {
    const value = Number(ruleForm.severity || 0);
    const matched = severityGuidance.value.find(item => value >= item.min && value <= item.max);
    if (matched) {
      return `${matched.labelZh}（${matched.min} - ${matched.max}）：${matched.description || ''}`;
    }
    return getFieldHelp('policy.severity');
  });

  const ruleTypeConfigHint = computed(() => {
    const matched = ruleTypeOptions.value.find(item => item.code === ruleForm.ruleType);
    return matched?.configSchemaHint || '可填写 JSON 对象，为规则提供额外参数';
  });

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

  const formatDefaultAction = code => buildOptionLabel(code, defaultActionOptions.value);

  const formatRuleType = code => buildOptionLabel(code, ruleTypeOptions.value);

  const formatRuleCategory = code => buildOptionLabel(code, ruleCategoryOptions.value);

  const getFieldHelp = key => optionMeta.value.fieldHelp?.[key] || '';

  const getThresholdHint = thresholdCode => {
    const matched = thresholdGuidance.value.find(item => item.code === thresholdCode);
    if (!matched) {
      return optionMeta.value.fieldHelp?.['policy.threshold'] || '';
    }
    return `${matched.description || ''}（推荐 ${matched.recommendedRange || '-'}）`;
  };

  const normalizeFlags = flags => {
    if (!flags) {
      return [];
    }
    if (Array.isArray(flags)) {
      return flags.map(item => String(item).trim()).filter(Boolean);
    }
    return String(flags)
      .split(',')
      .map(item => item.trim())
      .filter(Boolean);
  };

  const normalizeRegexPatternText = pattern => {
    if (!pattern) {
      return '';
    }
    return String(pattern)
      .replace(/\\\\([dDwWsSbB])/g, '\\$1')
      .trim();
  };

  const normalizeRegexMaskMode = mode => {
    const normalized = String(mode || '')
      .trim()
      .toUpperCase();
    if (normalized === 'DELETE') {
      return 'DELETE';
    }
    return 'PLACEHOLDER';
  };

  const onRegexMaskModeChange = nextMode => {
    ruleForm.regexMaskMode = normalizeRegexMaskMode(nextMode);
    if (ruleForm.regexMaskMode === 'DELETE') {
      ruleForm.regexMaskText = '';
    } else if (!String(ruleForm.regexMaskText || '').trim()) {
      ruleForm.regexMaskText = '[REDACTED]';
    }
    if (ruleForm.showAdvancedConfig) {
      syncConfigJsonFromStructured();
    }
  };

  const onRegexMaskTextInput = () => {
    if (ruleForm.showAdvancedConfig) {
      syncConfigJsonFromStructured();
    }
  };

  const normalizeRegexPatternInput = () => {
    if (ruleForm.ruleType !== 'REGEX') {
      return;
    }
    const original = String(ruleForm.regexPattern || '');
    const normalized = normalizeRegexPatternText(original);
    if (normalized === original) {
      return;
    }
    ruleForm.regexPattern = normalized;
    if (ruleForm.showAdvancedConfig) {
      syncConfigJsonFromStructured();
    }
    ElMessage.info('已自动规范化正则 pattern（将双反斜杠转换为单反斜杠写法）');
  };

  const syncConfigJsonFromStructured = () => {
    if (ruleForm.ruleType !== 'REGEX') {
      ruleForm.configJson = '{}';
      return;
    }
    const config = {
      pattern: ruleForm.regexPattern || '',
      flags: ruleForm.regexFlags.join(','),
      maskMode: ruleForm.regexMaskMode,
      ...(ruleForm.regexMaskMode === 'DELETE'
        ? {}
        : { maskText: String(ruleForm.regexMaskText || '').trim() || '[REDACTED]' }),
    };
    ruleForm.configJson = JSON.stringify(config, null, 2);
  };

  const applyRegexTemplate = code => {
    if (!code) {
      return;
    }
    const template = regexTemplateOptions.value.find(item => item.code === code);
    const config = template?.sampleConfig;
    if (!config || typeof config !== 'object') {
      ElMessage.warning('模板缺少配置内容');
      return;
    }
    ruleForm.regexPattern = String(config.pattern || '').trim();
    ruleForm.regexFlags = normalizeFlags(config.flags || 'CASE_INSENSITIVE');
    ruleForm.regexMaskMode = normalizeRegexMaskMode(config.maskMode);
    ruleForm.regexMaskText =
      ruleForm.regexMaskMode === 'DELETE'
        ? ''
        : String(config.maskText || '[REDACTED]').trim() || '[REDACTED]';
    if (ruleForm.showAdvancedConfig) {
      syncConfigJsonFromStructured();
    }
  };

  const fillRuleConfigTemplate = () => {
    if (ruleForm.ruleType === 'REGEX' && ruleForm.regexTemplateCode) {
      applyRegexTemplate(ruleForm.regexTemplateCode);
      ElMessage.success('已按正则模板填充');
      return;
    }
    const templates = optionMeta.value.jsonConfigTemplates || {};
    const template = templates[ruleForm.ruleType];
    if (!template || typeof template !== 'object') {
      ruleForm.configJson = '{}';
      ElMessage.success('已填充空模板');
      return;
    }
    ruleForm.configJson = JSON.stringify(template, null, 2);
    ElMessage.success('已按规则类型填充模板');
  };

  const toggleAdvancedConfig = () => {
    ruleForm.showAdvancedConfig = !ruleForm.showAdvancedConfig;
    if (ruleForm.showAdvancedConfig && !ruleForm.configJson) {
      syncConfigJsonFromStructured();
    }
  };

  const parseJsonSafe = value => {
    if (!value || typeof value !== 'string') {
      return {};
    }
    try {
      const parsed = JSON.parse(value);
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  };

  const normalizeApiError = error => {
    const message = error?.response?.data?.message || error?.message;
    return message && String(message).trim() ? String(message) : null;
  };

  const formatDateTime = value => {
    if (!value) {
      return '-';
    }
    return String(value).replace('T', ' ');
  };

  const formatVersionStatus = status => {
    const labels = {
      DRAFT: '草稿',
      PUBLISHED: '已发布',
      OFFLINE: '已下线',
      ROLLED_BACK: '已回退',
    };
    return labels[status] || status || '-';
  };

  const formatVersionStatusTag = status => {
    if (status === 'PUBLISHED') {
      return 'success';
    }
    if (status === 'OFFLINE') {
      return 'warning';
    }
    if (status === 'ROLLED_BACK') {
      return 'info';
    }
    return 'primary';
  };

  const loadOptionMeta = async () => {
    const remote = await cleaningMetaService.getOptions();
    optionMeta.value = mergeOptionsWithFallback(remote);
  };

  const loadPolicies = async () => {
    loadingPolicies.value = true;
    try {
      policies.value = await cleaningService.listPolicies();
    } catch (error) {
      ElMessage.error('加载策略失败');
    } finally {
      loadingPolicies.value = false;
    }
  };

  const loadRules = async () => {
    loadingRules.value = true;
    try {
      rules.value = await cleaningService.listRules();
    } catch (error) {
      ElMessage.error('加载规则失败');
    } finally {
      loadingRules.value = false;
    }
  };

  const loadTemplates = async () => {
    loadingPolicyTemplates.value = true;
    try {
      policyTemplates.value = await cleaningService.listPolicyTemplates();
    } catch (error) {
      ElMessage.error('加载模板失败');
    } finally {
      loadingPolicyTemplates.value = false;
    }
  };

  const loadAll = async () => {
    await Promise.all([loadOptionMeta(), loadPolicies(), loadRules(), loadTemplates()]);
  };

  const openPolicyDialog = policy => {
    const isEdit = !!policy;
    policyDialogTitle.value = isEdit ? '编辑策略' : '新增策略';
    policyForm.id = isEdit ? policy.id : null;
    policyForm.name = isEdit ? policy.name : '';
    policyForm.description = isEdit ? policy.description || '' : '';
    policyForm.defaultAction = isEdit ? policy.defaultAction || 'DETECT_ONLY' : 'DETECT_ONLY';
    policyForm.enabled = isEdit ? !!policy.enabled : true;
    const config = isEdit ? parseJsonSafe(policy.configJson) : {};
    policyForm.blockThreshold = config.blockThreshold ?? 0.7;
    policyForm.reviewThreshold = config.reviewThreshold ?? 0.4;
    policyForm.llmEnabled = config.llmEnabled ?? true;
    policyForm.l2Threshold = config.l2Threshold ?? 0.6;
    policyForm.shadowEnabled = config.shadowEnabled ?? false;
    policyForm.shadowSampleRatio = config.shadowSampleRatio ?? 0;

    ruleSelections.value = rules.value.map(rule => {
      const binding = policy?.rules?.find(item => item.ruleId === rule.id);
      return {
        ruleId: rule.id,
        name: rule.name,
        ruleType: rule.ruleType,
        category: rule.category,
        selected: !!binding,
        priority: binding?.priority ?? 0,
      };
    });

    policyDialogVisible.value = true;
  };

  const loadPolicyVersions = async (policyId, { asRefresh = false } = {}) => {
    if (!policyId) {
      policyVersions.value = [];
      return;
    }
    if (asRefresh) {
      refreshVersionsLoading.value = true;
    } else {
      loadingPolicyVersions.value = true;
      versionLoadingMap[policyId] = true;
    }
    try {
      policyVersions.value = await cleaningService.listPolicyVersions(policyId);
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '加载策略版本失败');
      policyVersions.value = [];
    } finally {
      if (asRefresh) {
        refreshVersionsLoading.value = false;
      } else {
        loadingPolicyVersions.value = false;
        versionLoadingMap[policyId] = false;
      }
    }
  };

  const openVersionDialog = async policy => {
    selectedVersionPolicy.value = policy;
    versionDialogVisible.value = true;
    await loadPolicyVersions(policy?.id);
  };

  const closeVersionDialog = () => {
    selectedVersionPolicy.value = null;
    policyVersions.value = [];
    refreshVersionsLoading.value = false;
  };

  const loadPolicyExperiments = async policyId => {
    if (!policyId) {
      policyExperiments.value = [];
      return;
    }
    loadingPolicyExperiments.value = true;
    try {
      policyExperiments.value = await cleaningService.listPolicyExperiments(policyId, {
        limit: 100,
      });
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '加载实验记录失败');
      policyExperiments.value = [];
    } finally {
      loadingPolicyExperiments.value = false;
    }
  };

  const loadPolicyShadowSummary = async policyId => {
    if (!policyId) {
      policyShadowSummary.value = null;
      return;
    }
    loadingPolicyShadowSummary.value = true;
    try {
      policyShadowSummary.value = await cleaningService.getPolicyShadowSummary(policyId, {
        limit: 1000,
      });
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '加载 Shadow 摘要失败');
      policyShadowSummary.value = null;
    } finally {
      loadingPolicyShadowSummary.value = false;
    }
  };

  const openExperimentDialog = async policy => {
    selectedVersionPolicy.value = policy;
    experimentDialogVisible.value = true;
    await Promise.all([loadPolicyExperiments(policy?.id), loadPolicyShadowSummary(policy?.id)]);
  };

  const openCopilotDialog = async policy => {
    if (!policy?.id) {
      return;
    }
    selectedCopilotPolicy.value = policy;
    copilotSuggestion.value = null;
    copilotDialogVisible.value = true;
    copilotLoading.value = true;
    try {
      copilotSuggestion.value = await cleaningService.getPolicyCopilotSuggestion(policy.id, {
        limit: 300,
      });
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '加载 AI 建议失败');
      copilotSuggestion.value = null;
    } finally {
      copilotLoading.value = false;
    }
  };

  const applyCopilotSuggestion = () => {
    const suggestion = copilotSuggestion.value;
    const policy = selectedCopilotPolicy.value;
    if (!suggestion || !policy) {
      return;
    }
    openPolicyDialog(policy);
    if (suggestion.recommendedDefaultAction) {
      policyForm.defaultAction = suggestion.recommendedDefaultAction;
    }
    const recommendedConfig = suggestion.recommendedConfig || {};
    if (Number.isFinite(Number(recommendedConfig.blockThreshold))) {
      policyForm.blockThreshold = Number(recommendedConfig.blockThreshold);
    }
    if (Number.isFinite(Number(recommendedConfig.reviewThreshold))) {
      policyForm.reviewThreshold = Number(recommendedConfig.reviewThreshold);
    }
    if (Number.isFinite(Number(recommendedConfig.l2Threshold))) {
      policyForm.l2Threshold = Number(recommendedConfig.l2Threshold);
    }
    if (typeof recommendedConfig.llmEnabled === 'boolean') {
      policyForm.llmEnabled = recommendedConfig.llmEnabled;
    }
    if (typeof recommendedConfig.shadowEnabled === 'boolean') {
      policyForm.shadowEnabled = recommendedConfig.shadowEnabled;
    }
    if (Number.isFinite(Number(recommendedConfig.shadowSampleRatio))) {
      policyForm.shadowSampleRatio = Number(recommendedConfig.shadowSampleRatio);
    }
    const recommendedRuleIds = new Set(suggestion.recommendedRuleIds || []);
    if (recommendedRuleIds.size > 0) {
      ruleSelections.value = ruleSelections.value.map(item => {
        if (recommendedRuleIds.has(item.ruleId)) {
          return {
            ...item,
            selected: true,
            priority: item.priority > 0 ? item.priority : 50,
          };
        }
        return item;
      });
    }
    copilotDialogVisible.value = false;
    ElMessage.success('已应用 AI 建议，请确认后保存策略');
  };

  const publishPolicyVersion = async policy => {
    if (!policy?.id) {
      return;
    }
    try {
      await ElMessageBox.confirm(
        `确认发布策略「${policy.name}」？发布后将冻结为新版本供作业运行绑定。`,
        '发布确认',
        {
          type: 'warning',
          confirmButtonText: '确认发布',
          cancelButtonText: '取消',
        },
      );
    } catch (error) {
      return;
    }
    let note;
    try {
      const noteInput = await ElMessageBox.prompt('请输入发布说明（可选）', '发布说明', {
        confirmButtonText: '发布',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：修复误杀、调整阈值、放宽某类规则',
      });
      note = noteInput.value || undefined;
    } catch (error) {
      return;
    }
    publishLoadingMap[policy.id] = true;
    try {
      await cleaningService.publishPolicy(policy.id, {
        publishMode: 'FULL',
        note,
        operator: 'admin',
      });
      await loadPolicies();
      if (versionDialogVisible.value && selectedVersionPolicy.value?.id === policy.id) {
        await loadPolicyVersions(policy.id, { asRefresh: true });
      }
      ElMessage.success('策略已发布');
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '策略发布失败');
    } finally {
      publishLoadingMap[policy.id] = false;
    }
  };

  const publishPolicyGray = async policy => {
    if (!policy?.id) {
      return;
    }
    try {
      const ratioInput = await ElMessageBox.prompt('请输入灰度比例（0-1）', '灰度发布', {
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
        inputValue: '0.1',
      });
      const grayRatio = Number(ratioInput.value);
      if (!Number.isFinite(grayRatio) || grayRatio <= 0 || grayRatio > 1) {
        ElMessage.error('灰度比例必须在 (0, 1] 之间');
        return;
      }
      const experimentInput = await ElMessageBox.prompt('请输入实验名称（可选）', '灰度发布', {
        confirmButtonText: '发布',
        cancelButtonText: '取消',
        inputPlaceholder: `policy-${policy.id}-gray`,
      });
      const noteInput = await ElMessageBox.prompt('请输入灰度发布说明（可选）', '灰度说明', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：仅验证手机号脱敏规则',
      });
      grayPublishLoadingMap[policy.id] = true;
      await cleaningService.publishPolicy(policy.id, {
        publishMode: 'GRAY',
        grayRatio,
        experimentName: experimentInput.value || undefined,
        note: noteInput.value || undefined,
        operator: 'admin',
      });
      await loadPolicies();
      if (experimentDialogVisible.value && selectedVersionPolicy.value?.id === policy.id) {
        await loadPolicyExperiments(policy.id);
      }
      if (versionDialogVisible.value && selectedVersionPolicy.value?.id === policy.id) {
        await loadPolicyVersions(policy.id, { asRefresh: true });
      }
      ElMessage.success('灰度发布成功');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        const message = normalizeApiError(error);
        ElMessage.error(message || '灰度发布失败');
      }
    } finally {
      grayPublishLoadingMap[policy.id] = false;
    }
  };

  const offlinePolicy = async policy => {
    if (!policy?.id) {
      return;
    }
    try {
      await ElMessageBox.confirm(`确认下线策略「${policy.name}」的当前发布版本？`, '下线确认', {
        type: 'warning',
        confirmButtonText: '确认下线',
        cancelButtonText: '取消',
      });
    } catch (error) {
      return;
    }
    let note;
    try {
      const noteInput = await ElMessageBox.prompt('请输入下线说明（可选）', '下线说明', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：策略需紧急停用，等待修复后重新发布',
      });
      note = noteInput.value || undefined;
    } catch (error) {
      return;
    }
    try {
      await cleaningService.offlinePolicy(policy.id, {
        note,
        operator: 'admin',
      });
      await loadPolicies();
      if (versionDialogVisible.value && selectedVersionPolicy.value?.id === policy.id) {
        await loadPolicyVersions(policy.id, { asRefresh: true });
      }
      if (experimentDialogVisible.value && selectedVersionPolicy.value?.id === policy.id) {
        await loadPolicyExperiments(policy.id);
      }
      ElMessage.success('策略已下线');
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '策略下线失败');
    }
  };

  const rollbackPolicyVersion = async version => {
    const policy = selectedVersionPolicy.value;
    if (!policy?.id || !version?.id) {
      return;
    }
    try {
      await ElMessageBox.confirm(
        `确认将策略「${policy.name}」回滚到版本 v${version.versionNo}？`,
        '回滚确认',
        {
          type: 'warning',
          confirmButtonText: '确认回滚',
          cancelButtonText: '取消',
        },
      );
    } catch (error) {
      return;
    }
    let note;
    try {
      const noteInput = await ElMessageBox.prompt('请输入回滚说明（可选）', '回滚说明', {
        confirmButtonText: '回滚',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：新版本误杀率升高，回退到稳定版本',
      });
      note = noteInput.value || undefined;
    } catch (error) {
      return;
    }
    rollbackVersionLoadingMap[version.id] = true;
    try {
      await cleaningService.rollbackPolicyVersion(policy.id, {
        versionId: version.id,
        note,
        operator: 'admin',
      });
      await Promise.all([loadPolicies(), loadPolicyVersions(policy.id, { asRefresh: true })]);
      ElMessage.success(`已回滚到版本 v${version.versionNo}`);
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '策略版本回滚失败');
    } finally {
      rollbackVersionLoadingMap[version.id] = false;
    }
  };

  const ensurePolicyRiskConfirmed = async () => {
    const riskMessage = riskConfirmations.value[policyForm.defaultAction];
    if (!riskMessage) {
      return;
    }
    await ElMessageBox.confirm(riskMessage, '高风险动作确认', {
      type: 'warning',
      confirmButtonText: '继续保存',
      cancelButtonText: '返回修改',
    });
  };

  const savePolicy = async () => {
    if (!policyForm.name) {
      ElMessage.warning('请输入策略名称');
      return;
    }

    if (policyForm.reviewThreshold > policyForm.blockThreshold) {
      ElMessage.error('Review 阈值不能大于 Block 阈值');
      return;
    }

    if (!policyForm.shadowEnabled && policyForm.shadowSampleRatio > 0) {
      ElMessage.warning('Shadow 未启用，采样率将自动重置为 0');
      policyForm.shadowSampleRatio = 0;
    }

    try {
      await ensurePolicyRiskConfirmed();

      const payload = {
        name: policyForm.name,
        description: policyForm.description,
        enabled: policyForm.enabled ? 1 : 0,
        defaultAction: policyForm.defaultAction,
        config: {
          blockThreshold: policyForm.blockThreshold,
          reviewThreshold: policyForm.reviewThreshold,
          llmEnabled: policyForm.llmEnabled,
          l2Threshold: policyForm.l2Threshold,
          shadowEnabled: policyForm.shadowEnabled,
          shadowSampleRatio: policyForm.shadowSampleRatio,
        },
      };

      let policyId = policyForm.id;
      if (policyId) {
        await cleaningService.updatePolicy(policyId, payload);
      } else {
        const created = await cleaningService.createPolicy(payload);
        policyId = created?.id;
      }

      if (policyId) {
        const bindings = ruleSelections.value
          .filter(item => item.selected)
          .map(item => ({ ruleId: item.ruleId, priority: item.priority }));
        await cleaningService.updatePolicyRules(policyId, bindings);
      }

      policyDialogVisible.value = false;
      await loadPolicies();
      ElMessage.success('策略已保存');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('保存策略失败');
      }
    }
  };

  const openTemplateDialog = template => {
    const isEdit = !!template;
    templateDialogTitle.value = isEdit ? '编辑模板' : '新增模板';
    templateForm.id = isEdit ? template.id : null;
    templateForm.name = isEdit ? template.name : '';
    templateForm.description = isEdit ? template.description || '' : '';
    templateForm.category = isEdit ? template.category || '' : '';
    templateForm.defaultAction = isEdit ? template.defaultAction || 'DETECT_ONLY' : 'DETECT_ONLY';
    templateForm.enabled = isEdit ? !!template.enabled : true;
    templateForm.configJson = isEdit ? template.configJson || '{}' : '{}';
    templateRuleSelections.value = rules.value.map(rule => {
      const binding = template?.rules?.find(item => item.ruleId === rule.id);
      return {
        ruleId: rule.id,
        name: rule.name,
        ruleType: rule.ruleType,
        category: rule.category,
        selected: !!binding,
        priority: binding?.priority ?? 0,
      };
    });
    templateDialogVisible.value = true;
  };

  const saveTemplate = async () => {
    if (!templateForm.name) {
      ElMessage.warning('请输入模板名称');
      return;
    }
    if (!templateForm.category) {
      ElMessage.warning('请输入模板分类');
      return;
    }
    let config = {};
    try {
      config = templateForm.configJson ? JSON.parse(templateForm.configJson) : {};
    } catch (error) {
      ElMessage.error('配置 JSON 格式错误');
      return;
    }
    const payload = {
      name: templateForm.name,
      description: templateForm.description,
      category: templateForm.category,
      enabled: templateForm.enabled ? 1 : 0,
      defaultAction: templateForm.defaultAction,
      config,
      rules: templateRuleSelections.value
        .filter(item => item.selected)
        .map(item => ({ ruleId: item.ruleId, priority: item.priority })),
    };
    try {
      if (templateForm.id) {
        await cleaningService.updatePolicyTemplate(templateForm.id, payload);
      } else {
        await cleaningService.createPolicyTemplate(payload);
      }
      templateDialogVisible.value = false;
      await loadTemplates();
      ElMessage.success('模板已保存');
    } catch (error) {
      const message = normalizeApiError(error);
      ElMessage.error(message || '模板保存失败');
    }
  };

  const deleteTemplate = async template => {
    try {
      await ElMessageBox.confirm(`确认删除模板「${template.name}」?`, '删除确认', {
        type: 'warning',
      });
      await cleaningService.deletePolicyTemplate(template.id);
      await loadTemplates();
      ElMessage.success('模板已删除');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('模板删除失败');
      }
    }
  };

  const cloneFromTemplate = async template => {
    try {
      const { value } = await ElMessageBox.prompt('请输入克隆后的策略名称', '克隆模板', {
        confirmButtonText: '确认克隆',
        cancelButtonText: '取消',
        inputValue: `${template.name}-clone`,
      });
      const name = String(value || '').trim();
      if (!name) {
        ElMessage.error('策略名称不能为空');
        return;
      }
      await cleaningService.clonePolicyTemplate(template.id, { name });
      await loadPolicies();
      ElMessage.success('模板已克隆为策略');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        const message = normalizeApiError(error);
        ElMessage.error(message || '克隆模板失败');
      }
    }
  };

  const deletePolicy = async policy => {
    try {
      await ElMessageBox.confirm(`确认删除策略「${policy.name}」?`, '删除确认', {
        type: 'warning',
      });
      await cleaningService.deletePolicy(policy.id);
      await loadPolicies();
      ElMessage.success('策略已删除');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除策略失败');
      }
    }
  };

  const onRuleTypeChange = nextType => {
    if (nextType === 'REGEX') {
      if (!ruleForm.regexPattern) {
        const template = optionMeta.value.jsonConfigTemplates?.REGEX;
        ruleForm.regexPattern = String(template?.pattern || '\\d{11}');
      }
      if (ruleForm.regexFlags.length === 0) {
        ruleForm.regexFlags = ['CASE_INSENSITIVE'];
      }
      if (!ruleForm.regexMaskMode) {
        ruleForm.regexMaskMode = 'PLACEHOLDER';
      }
      if (ruleForm.regexMaskMode !== 'DELETE' && !String(ruleForm.regexMaskText || '').trim()) {
        ruleForm.regexMaskText = '[REDACTED]';
      }
    } else if (isBeginnerMode.value) {
      ruleForm.showAdvancedConfig = false;
    }

    if (nextType === 'L2_DUMMY') {
      ruleForm.l2Mode = 'REGEX';
      ruleForm.regexPattern = '';
      ruleForm.l2RegexThreshold = 0.8;
      ruleForm.l2EntropyThreshold = 4.8;
      ruleForm.l2MaxRepetition = 10;
    }
  };

  const openRuleDialog = rule => {
    const isEdit = !!rule;
    ruleDialogTitle.value = isEdit ? '编辑规则' : '新增规则';

    ruleForm.id = isEdit ? rule.id : null;
    ruleForm.name = isEdit ? rule.name : '';
    ruleForm.ruleType = isEdit ? rule.ruleType : 'REGEX';
    ruleForm.severity = isEdit ? Number(rule.severity ?? 0.8) : 0.8;
    ruleForm.enabled = isEdit ? !!rule.enabled : true;

    const categories = ruleCategoryOptions.value.map(item => item.code);
    const currentCategory = isEdit ? rule.category : '';
    if (currentCategory && categories.includes(currentCategory)) {
      ruleForm.categoryPreset = currentCategory;
      ruleForm.customCategory = '';
    } else if (currentCategory) {
      ruleForm.categoryPreset = '__CUSTOM__';
      ruleForm.customCategory = currentCategory;
    } else {
      ruleForm.categoryPreset = categories[0] || '';
      ruleForm.customCategory = '';
    }

    const parsedConfig = isEdit ? parseJsonSafe(rule.configJson) : {};
    ruleForm.configJson = JSON.stringify(parsedConfig, null, 2);
    ruleForm.showAdvancedConfig = !isBeginnerMode.value;
    ruleForm.regexTemplateCode = '';

    if (ruleForm.ruleType === 'REGEX') {
      ruleForm.regexPattern = normalizeRegexPatternText(String(parsedConfig.pattern || ''));
      ruleForm.regexFlags = normalizeFlags(parsedConfig.flags || 'CASE_INSENSITIVE');
      ruleForm.regexMaskMode = normalizeRegexMaskMode(parsedConfig.maskMode);
      ruleForm.regexMaskText =
        ruleForm.regexMaskMode === 'DELETE'
          ? ''
          : String(parsedConfig.maskText || '[REDACTED]').trim() || '[REDACTED]';
      if (ruleForm.regexFlags.length === 0) {
        ruleForm.regexFlags = ['CASE_INSENSITIVE'];
      }
      if (ruleForm.showAdvancedConfig) {
        syncConfigJsonFromStructured();
      }
    } else if (ruleForm.ruleType === 'LLM') {
      ruleForm.llmPrompt = String(parsedConfig.prompt || '');
    } else if (ruleForm.ruleType === 'L2_DUMMY') {
      // Determine mode based on category or existing config
      const cat = rule.category || '';
      if (cat === 'ANOMALY_ENTROPY') {
        ruleForm.l2Mode = 'ENTROPY';
        ruleForm.l2EntropyThreshold =
          parsedConfig.threshold !== undefined ? Number(parsedConfig.threshold) : 4.8;
      } else if (cat === 'ANOMALY_REPETITION') {
        ruleForm.l2Mode = 'REPETITION';
        ruleForm.l2MaxRepetition =
          parsedConfig.maxRepetition !== undefined ? Number(parsedConfig.maxRepetition) : 10;
      } else if (cat === 'L2_REGEX') {
        ruleForm.l2Mode = 'REGEX';
        ruleForm.regexPattern = String(parsedConfig.pattern || '');
        ruleForm.l2RegexThreshold =
          parsedConfig.threshold !== undefined ? Number(parsedConfig.threshold) : 0.8;
      } else {
        // Fallback or default
        ruleForm.l2Mode = 'REGEX';
        ruleForm.regexPattern = String(parsedConfig.pattern || '');
      }
    } else {
      ruleForm.regexPattern = '';
      ruleForm.regexFlags = ['CASE_INSENSITIVE'];
      ruleForm.regexMaskMode = 'PLACEHOLDER';
      ruleForm.regexMaskText = '[REDACTED]';
      ruleForm.llmPrompt = '';
    }

    ruleDialogVisible.value = true;
  };

  const buildRuleConfig = () => {
    if (allowAdvancedConfig.value && ruleForm.showAdvancedConfig) {
      const raw = ruleForm.configJson?.trim();
      if (!raw) {
        return {};
      }
      try {
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          ElMessage.error('配置 JSON 必须是对象');
          return null;
        }
        if (ruleForm.ruleType === 'REGEX' && typeof parsed.pattern === 'string') {
          const normalizedPattern = normalizeRegexPatternText(parsed.pattern);
          if (normalizedPattern && normalizedPattern !== parsed.pattern) {
            parsed.pattern = normalizedPattern;
            ruleForm.configJson = JSON.stringify(parsed, null, 2);
            ElMessage.info('已自动规范化 JSON 中的 pattern（双反斜杠 -> 单反斜杠）');
          }
          parsed.maskMode = normalizeRegexMaskMode(parsed.maskMode);
          if (parsed.maskMode === 'DELETE') {
            delete parsed.maskText;
          } else if (!String(parsed.maskText || '').trim()) {
            parsed.maskText = '[REDACTED]';
          }
          ruleForm.configJson = JSON.stringify(parsed, null, 2);
        }
        return parsed;
      } catch (error) {
        ElMessage.error('配置 JSON 格式不正确');
        return null;
      }
    }

    if (ruleForm.ruleType === 'REGEX') {
      const pattern = normalizeRegexPatternText(ruleForm.regexPattern);
      if (!pattern) {
        ElMessage.error('REGEX 规则必须填写 pattern');
        return null;
      }
      if (pattern !== ruleForm.regexPattern) {
        ruleForm.regexPattern = pattern;
        if (ruleForm.showAdvancedConfig) {
          syncConfigJsonFromStructured();
        }
        ElMessage.info('已自动规范化 pattern（双反斜杠 -> 单反斜杠）');
      }
      const flags = ruleForm.regexFlags.join(',');
      const maskMode = normalizeRegexMaskMode(ruleForm.regexMaskMode);
      const maskText = String(ruleForm.regexMaskText || '').trim();
      return {
        pattern,
        ...(flags ? { flags } : {}),
        maskMode,
        ...(maskMode === 'DELETE' ? {} : { maskText: maskText || '[REDACTED]' }),
      };
    }

    if (ruleForm.ruleType === 'LLM') {
      return {
        prompt: String(ruleForm.llmPrompt || '').trim(),
      };
    }

    if (ruleForm.ruleType === 'L2_DUMMY') {
      const mode = ruleForm.l2Mode;
      if (mode === 'ENTROPY') {
        return {
          threshold: Number(ruleForm.l2EntropyThreshold),
        };
      } else if (mode === 'REPETITION') {
        return {
          maxRepetition: Number(ruleForm.l2MaxRepetition),
        };
      } else {
        // REGEX
        return {
          pattern: ruleForm.regexPattern,
          threshold: Number(ruleForm.l2RegexThreshold),
        };
      }
    }

    return {};
  };

  const saveRule = async () => {
    let category =
      ruleForm.categoryPreset === '__CUSTOM__'
        ? String(ruleForm.customCategory || '').trim()
        : String(ruleForm.categoryPreset || '').trim();

    // Auto-set category for L2_DUMMY based on mode
    if (ruleForm.ruleType === 'L2_DUMMY') {
      if (ruleForm.l2Mode === 'ENTROPY') category = 'ANOMALY_ENTROPY';
      else if (ruleForm.l2Mode === 'REPETITION') category = 'ANOMALY_REPETITION';
      else category = 'L2_REGEX';
    }

    if (!ruleForm.name || !ruleForm.ruleType || !category) {
      ElMessage.warning('请填写完整规则信息');
      return;
    }

    const config = buildRuleConfig();
    if (config === null) {
      return;
    }

    const payload = {
      name: ruleForm.name,
      ruleType: ruleForm.ruleType,
      category,
      severity: Number(ruleForm.severity),
      enabled: ruleForm.enabled ? 1 : 0,
      config,
    };

    try {
      if (ruleForm.id) {
        await cleaningService.updateRule(ruleForm.id, payload);
      } else {
        await cleaningService.createRule(payload);
      }
      ruleDialogVisible.value = false;
      await loadRules();
      await loadPolicies();
      ElMessage.success('规则已保存');
    } catch (error) {
      ElMessage.error('保存规则失败');
    }
  };

  const loadBindingAgents = async () => {
    try {
      bindingAgents.value = await agentService.list();
    } catch (error) {
      bindingAgents.value = [];
      ElMessage.error('加载 Agent 失败');
    }
  };

  const onBindingAgentChange = async agentId => {
    bindingForm.policyId = undefined;
    if (!agentId) {
      return;
    }
    try {
      const binding = await cleaningService.getOnlineDefaultBinding(Number(agentId));
      bindingForm.policyId = binding?.policyId;
      bindingForm.enabled = binding?.enabled !== 0;
    } catch (error) {
      bindingForm.policyId = undefined;
      bindingForm.enabled = true;
    }
  };

  const openBindingDialog = async () => {
    bindingDialogVisible.value = true;
    if (bindingAgents.value.length === 0) {
      await loadBindingAgents();
    }
    if (!bindingForm.agentId && bindingAgents.value.length > 0) {
      bindingForm.agentId = bindingAgents.value[0].id;
      await onBindingAgentChange(bindingForm.agentId);
    }
  };

  const saveBinding = async () => {
    if (!bindingForm.agentId || !bindingForm.policyId) {
      ElMessage.warning('请选择 Agent 和策略');
      return;
    }
    try {
      await cleaningService.upsertOnlineDefaultBinding({
        agentId: Number(bindingForm.agentId),
        policyId: Number(bindingForm.policyId),
        enabled: bindingForm.enabled ? 1 : 0,
      });
      bindingDialogVisible.value = false;
      ElMessage.success('默认绑定已保存');
    } catch (error) {
      ElMessage.error('保存默认绑定失败');
    }
  };

  const deleteRule = async rule => {
    try {
      await ElMessageBox.confirm(`确认删除规则「${rule.name}」?`, '删除确认', {
        type: 'warning',
      });
      await cleaningService.deleteRule(rule.id);
      await loadRules();
      await loadPolicies();
      ElMessage.success('规则已删除');
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除规则失败');
      }
    }
  };

  onMounted(() => {
    loadAll();
  });
</script>

<style scoped>
  .cleaning-policy-page {
    min-height: 100vh;
    padding: 2rem;
  }

  .main-content {
    max-width: 1240px;
    margin: 0 auto;
  }

  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
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

  .header-actions {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }

  .intro-alert {
    margin-bottom: 1rem;
  }

  .content-tabs {
    background: white;
    border-radius: 12px;
    padding: 1rem;
  }

  .threshold-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 1rem;
    margin-bottom: 1rem;
  }

  .severity-editor {
    width: 100%;
    display: grid;
    grid-template-columns: 150px 1fr auto;
    gap: 12px;
    align-items: center;
  }

  .severity-percent {
    color: #475569;
    font-weight: 500;
  }

  .field-help {
    margin-top: 6px;
    font-size: 12px;
    color: #64748b;
    line-height: 1.4;
  }

  .field-help-warning {
    color: #d97706;
  }

  .inline-alert {
    margin-bottom: 10px;
  }

  .config-toolbar {
    margin-top: 8px;
  }

  .rule-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 1rem;
  }
</style>
