import { apiDownload, apiRequest, enableMock } from "@/api/http";
import { mockDb } from "@/api/mockDb";
import { createReportDocxBlob } from "@/utils/docx";
import { createSimpleXlsxBlob } from "@/utils/xlsx";
import type {
  AssetCategory,
  AssetCategoryOption,
  AssetImportResult,
  AssetQuery,
  AssetRecord,
  EntityId,
  LlmConfig,
  PageResult,
  ReportType,
  TemplateRecord
} from "@/types/domain";
import { assetCategoryLabels } from "@/utils/labels";

const wait = (ms = 300) => new Promise((resolve) => window.setTimeout(resolve, ms));
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);

export type MetricTone = "blue" | "cyan" | "green" | "orange" | "pink" | "red" | "purple";
export type HealthStatus = "ONLINE" | "DEGRADED" | "OFFLINE";
export type AlertLevel = "warning" | "danger" | "info";

export interface DashboardMetric {
  key: string;
  code: string;
  label: string;
  value: number;
  unit?: string;
  delta: string;
  tone: MetricTone;
}

export interface TrendPoint {
  date: string;
  count: number;
}

export interface ActivityTrend {
  key: string;
  title: string;
  color: string;
  points: TrendPoint[];
}

export interface DistributionItem {
  name: string;
  value: number;
  color: string;
}

export interface DistributionGroup {
  key: string;
  title: string;
  items: DistributionItem[];
}

export interface HealthItem {
  name: string;
  status: HealthStatus;
  latencyMs: number;
  detail: string;
}

export interface AlertItem {
  id: string;
  level: AlertLevel;
  title: string;
  description: string;
  time: string;
}

export interface RecentTask {
  id: string;
  name: string;
  type: string;
  status: string;
  owner: string;
  duration: string;
  time: string;
}

export interface DashboardData {
  source: "mock" | "api";
  updatedAt: string;
  metrics: DashboardMetric[];
  trends: ActivityTrend[];
  distributions: DistributionGroup[];
  health: HealthItem[];
  alerts: AlertItem[];
  recentTasks: RecentTask[];
}

interface AdminDashboardDto {
  overview?: {
    templateCount?: number;
    reportCount?: number;
    userCount?: number;
    sectionCount?: number;
  };
  trends?: TrendPoint[];
  distributions?: {
    reportType?: Array<{ code: string; label: string; count: number }>;
    reportStatus?: Array<{ code: string; label: string; count: number }>;
  };
  recentTasks?: Array<{
    reportId: string;
    name: string;
    type: ReportType;
    subject: string;
    powerPlant: string;
    reportYear: number;
    status: string;
    totalSections: number;
    completedSections: number;
    createdAt: string;
    updatedAt: string;
  }>;
  health?: Array<{ metric: string; label: string; status: string; value: number; unit?: string }>;
  alerts?: Array<{ type: string; level: string; message: string; count: number }>;
}

interface TemplatePageDto {
  records: TemplateDto[];
  total: number;
  page: number;
  size: number;
}

interface TemplateDto {
  id: string;
  name: string;
  reportType: ReportType;
  version: string;
  storageType?: string;
  filePath?: string;
  bucketName?: string;
  objectName?: string;
  originalFileName?: string;
  contentType?: string;
  fileSize?: number;
  configJson?: string;
  enabled: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt?: string;
}

export interface TemplateStyleConfig {
  titleSize: number;
  bodySize: number;
  lineHeight: number;
  header: string;
  footer?: string;
}

export interface TemplateConfigSchema {
  version: string;
  fields: Array<{
    key: string;
    label: string;
    type: string;
    description?: string;
    defaultValue?: unknown;
  }>;
  defaultConfig: Record<string, unknown>;
}

interface TemplateConfigPayload {
  configJson: string;
}

export interface TemplateVisualConfig {
  fonts?: {
    titleFont?: string;
    bodyFont?: string;
    titleSize?: number;
    heading1Size?: number;
    heading2Size?: number;
    bodySize?: number;
  };
  margins?: {
    topCm?: number;
    bottomCm?: number;
    leftCm?: number;
    rightCm?: number;
  };
  paragraph?: {
    lineSpacing?: number;
    firstLineIndentChars?: number;
  };
  caption?: {
    figureNumberingMode?: "GLOBAL" | "SECTION";
    tableNumberingMode?: "GLOBAL" | "SECTION";
  };
  outline?: unknown[];
  header?: string;
  footer?: string;
}

interface AssetPageDto {
  records: AssetDto[];
  total: number;
  page: number;
  size: number;
}

interface AssetDto {
  id: string;
  name: string;
  category: AssetCategory;
  categoryLabel?: string;
  fileType: string;
  storageType?: string;
  filePath?: string;
  bucketName?: string;
  objectName?: string;
  originalFileName: string;
  contentType?: string;
  fileSize: number;
  sha256?: string;
  description?: string;
  tags?: string;
  enabled: boolean;
  createdBy?: string;
  createdAt: string;
  updatedAt?: string;
}

interface LlmConfigDto {
  apiUrl: string;
  apiKey?: string;
  modelName: string;
  timeoutSeconds: number;
}

const distributionColors = ["#1e6bff", "#00b8d9", "#16a34a", "#f59e0b", "#dc2626", "#8b5cf6"];
const defaultTemplateConfig: TemplateStyleConfig = { titleSize: 18, bodySize: 12, lineHeight: 1.5, header: "示范电厂" };

function visualToTemplateStyle(config: TemplateVisualConfig = {}): TemplateStyleConfig {
  return {
    titleSize: config.fonts?.titleSize || config.fonts?.heading1Size || defaultTemplateConfig.titleSize,
    bodySize: config.fonts?.bodySize || defaultTemplateConfig.bodySize,
    lineHeight: config.paragraph?.lineSpacing || defaultTemplateConfig.lineHeight,
    header: config.header || defaultTemplateConfig.header,
    footer: config.footer || defaultTemplateConfig.footer
  };
}

function templateStyleToVisual(config: TemplateStyleConfig): TemplateVisualConfig {
  return {
    fonts: {
      titleSize: config.titleSize,
      heading1Size: config.titleSize,
      bodySize: config.bodySize
    },
    paragraph: {
      lineSpacing: config.lineHeight,
      firstLineIndentChars: 2
    },
    caption: {
      figureNumberingMode: "GLOBAL",
      tableNumberingMode: "GLOBAL"
    },
    header: config.header,
    footer: config.footer
  };
}

function parseTemplateConfig(rawConfig: unknown): TemplateStyleConfig {
  if (!rawConfig) return { ...defaultTemplateConfig };

  if (typeof rawConfig === "object") {
    return { ...defaultTemplateConfig, ...(rawConfig as Partial<TemplateStyleConfig>) };
  }

  if (typeof rawConfig !== "string") return { ...defaultTemplateConfig };

  try {
    const parsed = JSON.parse(rawConfig) as Partial<TemplateStyleConfig>;
    return { ...defaultTemplateConfig, ...parsed };
  } catch {
    return { ...defaultTemplateConfig };
  }
}

export async function fetchDashboardData(days = 30): Promise<DashboardData> {
  if (enableMock) return buildMockDashboard(days, "mock");

  const data = await apiRequest<AdminDashboardDto>(`/api/admin/stats/dashboard?days=${days}&recentLimit=10`);
  const statusDistribution = data.distributions?.reportStatus || [];
  const failed = statusDistribution.find((item) => item.code === "FAILED")?.count || 0;
  const reportCount = data.overview?.reportCount || 0;

  return {
    source: "api",
    updatedAt: new Date().toISOString(),
    metrics: [
      { key: "templates", code: "TPL", label: "报告模板", value: data.overview?.templateCount || 0, delta: "后端统计", tone: "orange" },
      { key: "reportGenerations", code: "RPT", label: "报告总数", value: reportCount, delta: "后端统计", tone: "pink" },
      { key: "sections", code: "SEC", label: "章节总数", value: data.overview?.sectionCount || 0, delta: "后端统计", tone: "cyan" },
      { key: "users", code: "USR", label: "用户数", value: data.overview?.userCount || 0, delta: "后端统计", tone: "blue" },
      { key: "failedTasks", code: "ERR", label: "生成失败", value: failed, delta: failed ? "需关注" : "稳定", tone: "red" }
    ],
    trends: [
      {
        key: "reportGeneration",
        title: `报告生成 · 近${days}天`,
        color: "#ec5da5",
        points: data.trends || []
      }
    ],
    distributions: normalizeDistributions(data.distributions),
    recentTasks: (data.recentTasks || []).map((task, index) => ({
      id: `RPT-${task.reportId}`,
      name: task.name,
      type: task.type === "SUMMER_PEAK_CHECK" ? "迎峰度夏检查" : "煤库存审计",
      status: task.status,
      owner: task.powerPlant || "C组",
      duration: `${Math.max(1, task.completedSections)}/${Math.max(1, task.totalSections)}`,
      time: new Date(task.updatedAt || task.createdAt).toLocaleString()
    })),
    health: (data.health || []).map((item) => ({
      name: item.label || item.metric,
      status: mapHealthStatus(item.status),
      latencyMs: Number(item.value || 0),
      detail: `${item.metric} ${item.value}${item.unit || ""}`
    })),
    alerts: (data.alerts || []).map((item, index) => ({
      id: item.type || `ALERT-${index + 1}`,
      level: mapAlertLevel(item.level),
      title: item.type,
      description: item.message,
      time: `count ${item.count}`
    }))
  };
}

export async function fetchAdminHealth() {
  if (enableMock) return { service: "report-admin", status: "UP" };
  return apiRequest<{ service: string; status: string }>("/api/admin/health");
}

export async function fetchStatsOverview() {
  if (enableMock) {
    const dashboard = buildMockDashboard(30, "mock");
    return {
      templateCount: mockDb.data.templates.length,
      reportCount: mockDb.data.reports.length,
      userCount: dashboard.metrics.find((item) => item.key === "users")?.value || 0,
      sectionCount: mockDb.data.reports.reduce((sum, report) => sum + report.totalSections, 0)
    };
  }
  return apiRequest<NonNullable<AdminDashboardDto["overview"]>>("/api/admin/stats/overview");
}

export async function fetchStatsTrend(days = 30) {
  if (enableMock) return buildMockDashboard(days, "mock").trends[0]?.points || [];
  return apiRequest<TrendPoint[]>(`/api/admin/stats/trend?days=${days}`);
}

export async function fetchStatsDistribution() {
  if (enableMock) {
    const dashboard = buildMockDashboard(30, "mock");
    return {
      reportType: dashboard.distributions.find((item) => item.key === "reportType")?.items || [],
      reportStatus: dashboard.distributions.find((item) => item.key === "reportStatus")?.items || []
    };
  }
  return apiRequest<NonNullable<AdminDashboardDto["distributions"]>>("/api/admin/stats/distribution");
}

export async function fetchRecentTasks(limit = 10) {
  if (enableMock) return buildMockDashboard(30, "mock").recentTasks.slice(0, limit);
  return apiRequest<NonNullable<AdminDashboardDto["recentTasks"]>>(`/api/admin/stats/recent-tasks?limit=${limit}`);
}

export async function fetchStatsHealth() {
  if (enableMock) return buildMockDashboard(30, "mock").health;
  return apiRequest<NonNullable<AdminDashboardDto["health"]>>("/api/admin/stats/health");
}

export async function fetchStatsAlerts() {
  if (enableMock) return buildMockDashboard(30, "mock").alerts;
  return apiRequest<NonNullable<AdminDashboardDto["alerts"]>>("/api/admin/stats/alerts");
}

function normalizeDistributions(distributions: AdminDashboardDto["distributions"]): DistributionGroup[] {
  const reportType = (distributions?.reportType || []).map((item, index) => ({
    name: item.label || item.code,
    value: item.count,
    color: distributionColors[index % distributionColors.length]
  }));
  const reportStatus = (distributions?.reportStatus || []).map((item, index) => ({
    name: item.label || item.code,
    value: item.count,
    color: distributionColors[(index + 2) % distributionColors.length]
  }));

  return [
    { key: "reportType", title: "报告类型分布", items: reportType },
    { key: "reportStatus", title: "报告状态分布", items: reportStatus }
  ];
}

function mapHealthStatus(status: string): HealthStatus {
  if (status === "NORMAL" || status === "ONLINE" || status === "UP") return "ONLINE";
  if (status === "WARN" || status === "DEGRADED") return "DEGRADED";
  return "OFFLINE";
}

function mapAlertLevel(level: string): AlertLevel {
  if (level === "ERROR" || level === "DANGER") return "danger";
  if (level === "WARN" || level === "WARNING") return "warning";
  return "info";
}

function mapTemplate(template: TemplateDto): TemplateRecord {
  return {
    id: template.id,
    name: template.name,
    reportType: template.reportType,
    version: template.version,
    storageType: template.storageType,
    filePath: template.filePath,
    bucketName: template.bucketName,
    objectName: template.objectName,
    originalFileName: template.originalFileName,
    contentType: template.contentType,
    fileSize: template.fileSize,
    configJson: template.configJson,
    enabled: template.enabled,
    createdBy: template.createdBy,
    createdAt: template.createdAt,
    updatedAt: template.updatedAt
  };
}

function mapAsset(asset: AssetDto): AssetRecord {
  return {
    id: asset.id,
    name: asset.name,
    category: asset.category,
    categoryLabel: asset.categoryLabel,
    fileType: asset.fileType,
    storageType: asset.storageType,
    filePath: asset.filePath,
    bucketName: asset.bucketName,
    objectName: asset.objectName,
    originalFileName: asset.originalFileName,
    contentType: asset.contentType,
    fileSize: asset.fileSize,
    sha256: asset.sha256,
    description: asset.description,
    tags: asset.tags,
    enabled: asset.enabled,
    createdBy: asset.createdBy,
    createdAt: asset.createdAt,
    updatedAt: asset.updatedAt
  };
}

function mockAssetFile(asset: AssetRecord) {
  if (!isExcelAsset(asset)) {
    return new Blob(
      [
        `素材名称：${asset.name}\n`,
        `分类：${asset.categoryLabel || asset.category}\n`,
        `原始文件：${asset.originalFileName}\n`,
        `描述：${asset.description || ""}\n`,
        `标签：${asset.tags || ""}\n`
      ],
      { type: asset.contentType || "application/octet-stream" }
    );
  }
  const rows = [
    ["素材名称", asset.name],
    ["分类", asset.categoryLabel || asset.category],
    ["原始文件", asset.originalFileName],
    ["描述", asset.description || ""],
    ["标签", asset.tags || ""],
    [],
    ["项目", "当前值", "备注"],
    ["文件类型", "xlsx", "素材管理仅支持 Excel"],
    ["启用状态", asset.enabled ? "启用" : "停用", ""]
  ];
  return createSimpleXlsxBlob(asset.originalFileName || `${asset.name}.xlsx`, rows);
}

function isExcelAsset(asset: Pick<AssetRecord, "fileType" | "originalFileName">) {
  return /^(xlsx|xls)$/i.test(asset.fileType || "") || /\.(xlsx|xls)$/i.test(asset.originalFileName || "");
}

async function mockTemplateFile(template: TemplateRecord) {
  const blob = await createReportDocxBlob({
    id: template.id,
    name: template.name,
    type: template.reportType,
    subject: `${template.name} 模板预览`,
    specialty: "电气",
    powerPlant: "示范电厂",
    reportYear: new Date().getFullYear(),
    status: "CONTENT_READY",
    ownerId: 1,
    totalSections: 2,
    completedSections: 2,
    createdAt: template.createdAt,
    updatedAt: template.updatedAt || template.createdAt,
    files: [],
    outline: [],
    sections: [
      {
        id: `${template.id}-preview-1`,
        reportId: template.id,
        outlineNodeId: `${template.id}-outline-1`,
        number: "1",
        title: "模板正文示例",
        contentMarkdown: `## 模板说明\n\n${template.name} 用于 ${template.reportType} 报告导出，预览内容来自当前模板文件下载通道。`,
        status: "GENERATED",
        source: "AI",
        version: 1,
        createdAt: template.createdAt,
        updatedAt: template.updatedAt || template.createdAt
      }
    ]
  });
  return {
    blob,
    fileName: template.originalFileName || `${template.name}.docx`
  };
}

export async function listTemplates(params: { page?: number; size?: number; reportType?: ReportType | null; enabled?: boolean | null; keyword?: string } = {}) {
  if (enableMock) {
    await wait();
    return mockDb.data.templates.filter((template) => {
      if (params.reportType && template.reportType !== params.reportType) return false;
      if (typeof params.enabled === "boolean" && template.enabled !== params.enabled) return false;
      if (params.keyword && !template.name.toLowerCase().includes(params.keyword.toLowerCase())) return false;
      return true;
    });
  }

  const query = new URLSearchParams({
    page: String(params.page || 1),
    size: String(params.size || 50)
  });
  if (params.reportType) query.set("reportType", params.reportType);
  if (typeof params.enabled === "boolean") query.set("enabled", String(params.enabled));
  if (params.keyword) query.set("keyword", params.keyword);

  const page = await apiRequest<TemplatePageDto>(`/api/admin/templates?${query.toString()}`);
  return page.records.map(mapTemplate);
}

export async function getTemplateConfigSchema() {
  if (enableMock) {
    await wait();
    return {
      version: "1.0",
      fields: [],
      defaultConfig: templateStyleToVisual(defaultTemplateConfig)
    } satisfies TemplateConfigSchema;
  }
  return apiRequest<TemplateConfigSchema>("/api/admin/templates/config-schema");
}

export async function getTemplateDefaults(reportType: ReportType) {
  if (enableMock) {
    await wait();
    return templateStyleToVisual(defaultTemplateConfig);
  }
  return apiRequest<TemplateVisualConfig>(`/api/admin/templates/defaults/${reportType}`);
}

export async function getTemplate(id: EntityId) {
  if (enableMock) {
    await wait();
    const template = mockDb.data.templates.find((item) => sameId(item.id, id));
    if (!template) throw new Error("模板不存在");
    return template;
  }

  const template = await apiRequest<TemplateDto>(`/api/admin/templates/${id}`);
  return mapTemplate(template);
}

export async function uploadTemplate(payload: {
  file: File;
  name: string;
  reportType: ReportType;
  version?: string;
  configJson?: string;
  enabled?: boolean;
}) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    const record: TemplateRecord = {
      id: mockDb.nextId(db),
      name: payload.name,
      reportType: payload.reportType,
      version: payload.version || "v1.0",
      enabled: payload.enabled ?? true,
      createdBy: "当前管理员",
      createdAt: new Date().toISOString()
    };
    db.templates.unshift(record);
    mockDb.save(db);
    return record;
  }

  const form = new FormData();
  form.append("file", payload.file);
  form.append("name", payload.name);
  form.append("reportType", payload.reportType);
  if (payload.version) form.append("version", payload.version);
  if (payload.configJson) form.append("configJson", payload.configJson);
  if (typeof payload.enabled === "boolean") form.append("enabled", String(payload.enabled));

  const template = await apiRequest<TemplateDto>("/api/admin/templates", {
    method: "POST",
    body: form
  });
  return mapTemplate(template);
}

export async function replaceTemplateFile(id: EntityId, file: File) {
  if (enableMock) {
    await wait();
    return getTemplate(id);
  }

  const form = new FormData();
  form.append("file", file);
  const template = await apiRequest<TemplateDto>(`/api/admin/templates/${id}/file`, {
    method: "PUT",
    body: form
  });
  return mapTemplate(template);
}

export async function updateTemplate(template: TemplateRecord) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    const index = db.templates.findIndex((item) => sameId(item.id, template.id));
    if (index < 0) throw new Error("模板不存在");
    db.templates[index] = template;
    mockDb.save(db);
    return template;
  }

  const updated = await apiRequest<TemplateDto>(`/api/admin/templates/${template.id}`, {
    method: "PUT",
    body: JSON.stringify({
      name: template.name,
      reportType: template.reportType,
      version: template.version,
      configJson: template.configJson || "{}",
      enabled: template.enabled
    })
  });
  return mapTemplate(updated);
}

export async function deleteTemplate(id: EntityId) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    db.templates = db.templates.filter((template) => !sameId(template.id, id));
    mockDb.save(db);
    return;
  }
  await apiRequest<null>(`/api/admin/templates/${id}`, { method: "DELETE" });
}

export async function downloadTemplate(id: EntityId) {
  if (enableMock) {
    await wait();
    const template = mockDb.data.templates.find((item) => sameId(item.id, id));
    if (!template) throw new Error("模板不存在");
    return mockTemplateFile(template);
  }
  return apiDownload(`/api/admin/templates/${id}/download`);
}

export async function getTemplateConfig(id: EntityId): Promise<TemplateStyleConfig> {
  if (enableMock) {
    await wait();
    return { ...defaultTemplateConfig };
  }
  return visualToTemplateStyle(await getTemplateVisualConfig(id));
}

export async function getTemplateConfigRaw(id: EntityId): Promise<string> {
  if (enableMock) {
    await wait();
    const template = mockDb.data.templates.find((item) => sameId(item.id, id));
    return template?.configJson || JSON.stringify(defaultTemplateConfig, null, 2);
  }
  const config = await apiRequest<string | TemplateConfigPayload>(`/api/admin/templates/${id}/config`);
  return typeof config === "string" ? config : config?.configJson || "";
}

export async function updateTemplateConfig(id: EntityId, config: TemplateStyleConfig) {
  if (enableMock) {
    await wait();
    return config;
  }
  await updateTemplateVisualConfig(id, templateStyleToVisual(config));
  return config;
}

export async function updateTemplateConfigRaw(id: EntityId, configJson: string) {
  if (enableMock) {
    await wait();
    const template = mockDb.data.templates.find((item) => sameId(item.id, id));
    if (template) template.configJson = configJson;
    mockDb.save(mockDb.data);
    return;
  }
  await apiRequest<unknown>(`/api/admin/templates/${id}/config`, {
    method: "PUT",
    body: JSON.stringify({
      configJson
    } satisfies TemplateConfigPayload)
  });
}

export async function getTemplateVisualConfig(id: EntityId) {
  if (enableMock) {
    await wait();
    const template = mockDb.data.templates.find((item) => sameId(item.id, id));
    return templateStyleToVisual(parseTemplateConfig(template?.configJson));
  }
  return apiRequest<TemplateVisualConfig>(`/api/admin/templates/${id}/visual-config`);
}

export async function updateTemplateVisualConfig(id: EntityId, config: TemplateVisualConfig) {
  if (enableMock) {
    await wait();
    const template = mockDb.data.templates.find((item) => sameId(item.id, id));
    if (template) template.configJson = JSON.stringify(config);
    mockDb.save(mockDb.data);
    return config;
  }
  return apiRequest<TemplateVisualConfig>(`/api/admin/templates/${id}/visual-config`, {
    method: "PUT",
    body: JSON.stringify(config)
  });
}

export async function getLlmConfig(): Promise<LlmConfig> {
  if (enableMock) {
    await wait();
    return mockDb.data.llmConfigs[0];
  }

  const config = await apiRequest<LlmConfigDto>("/api/admin/config/llm");
  return {
    id: "global-llm",
    provider: "OPENAI_COMPATIBLE",
    baseUrl: config.apiUrl,
    apiKeyConfigured: Boolean(config.apiKey),
    modelName: config.modelName,
    timeoutSeconds: config.timeoutSeconds,
    enabled: true
  };
}

export async function updateLlmConfig(config: Pick<LlmConfig, "baseUrl" | "modelName" | "timeoutSeconds"> & { apiKey?: string }) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    db.llmConfigs[0] = { ...db.llmConfigs[0], ...config };
    mockDb.save(db);
    return db.llmConfigs[0];
  }

  const updated = await apiRequest<LlmConfigDto>("/api/admin/config/llm", {
    method: "PUT",
    body: JSON.stringify({
      apiUrl: config.baseUrl,
      ...(config.apiKey ? { apiKey: config.apiKey } : {}),
      modelName: config.modelName,
      timeoutSeconds: config.timeoutSeconds
    })
  });
  return {
    id: "global-llm",
    provider: "OPENAI_COMPATIBLE",
    baseUrl: updated.apiUrl,
    apiKeyConfigured: Boolean(updated.apiKey || config.apiKey),
    modelName: updated.modelName,
    timeoutSeconds: updated.timeoutSeconds,
    enabled: true
  } satisfies LlmConfig;
}

export async function listAssetCategories(): Promise<AssetCategoryOption[]> {
  if (enableMock) {
    await wait();
    return Object.entries(assetCategoryLabels).map(([value, label]) => ({ value: value as AssetCategory, label }));
  }
  return apiRequest<AssetCategoryOption[]>("/api/admin/assets/categories");
}

export async function listAssets(params: Partial<AssetQuery> = {}): Promise<PageResult<AssetRecord>> {
  if (enableMock) {
    await wait();
    const page = params.page || 1;
    const size = params.size || 10;
    const keyword = params.keyword?.trim().toLowerCase();
    let records = mockDb.data.assets.filter((asset) => {
      if (params.category && asset.category !== params.category) return false;
      if (typeof params.enabled === "boolean" && asset.enabled !== params.enabled) return false;
      if (keyword) {
        const haystack = [asset.name, asset.originalFileName, asset.description, asset.tags].filter(Boolean).join(" ").toLowerCase();
        if (!haystack.includes(keyword)) return false;
      }
      return true;
    });
    records = records.sort((a, b) => (b.updatedAt || b.createdAt).localeCompare(a.updatedAt || a.createdAt));
    const start = (page - 1) * size;
    return {
      items: records.slice(start, start + size),
      total: records.length,
      page,
      pageSize: size
    };
  }

  const query = new URLSearchParams({
    page: String(params.page || 1),
    size: String(params.size || 10)
  });
  if (params.category) query.set("category", params.category);
  if (typeof params.enabled === "boolean") query.set("enabled", String(params.enabled));
  if (params.keyword) query.set("keyword", params.keyword);

  const page = await apiRequest<AssetPageDto>(`/api/admin/assets?${query.toString()}`);
  const records = page.records.map(mapAsset);
  return {
    items: records,
    total: page.total,
    page: page.page,
    pageSize: page.size
  };
}

export async function uploadAsset(payload: {
  file: File;
  name: string;
  category: AssetCategory;
  description?: string;
  tags?: string;
  enabled?: boolean;
}) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    const id = `asset-${mockDb.nextId(db)}`;
    const originalFileName = payload.file.name || payload.name;
    const fileType = originalFileName.split(".").pop()?.toLowerCase() || "bin";
    const asset: AssetRecord = {
      id,
      name: payload.name,
      category: payload.category,
      categoryLabel: assetCategoryLabels[payload.category],
      fileType,
      storageType: "MINIO",
      filePath: `minio://report-assets/assets/${payload.category}/${id}.${fileType}`,
      bucketName: "report-assets",
      objectName: `assets/${payload.category}/${id}.${fileType}`,
      originalFileName,
      contentType: payload.file.type || "application/octet-stream",
      fileSize: payload.file.size,
      sha256: `mock-${id}`,
      description: payload.description,
      tags: payload.tags,
      enabled: payload.enabled ?? true,
      createdBy: "当前管理员",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    db.assets.unshift(asset);
    mockDb.save(db);
    return asset;
  }

  const form = new FormData();
  form.append("file", payload.file);
  form.append("name", payload.name);
  form.append("category", payload.category);
  if (payload.description) form.append("description", payload.description);
  if (payload.tags) form.append("tags", payload.tags);
  if (typeof payload.enabled === "boolean") form.append("enabled", String(payload.enabled));

  const asset = await apiRequest<AssetDto>("/api/admin/assets", {
    method: "POST",
    body: form
  });
  return mapAsset(asset);
}

export async function getAsset(id: EntityId) {
  if (enableMock) {
    await wait();
    const asset = mockDb.data.assets.find((item) => sameId(item.id, id));
    if (!asset) throw new Error("素材不存在");
    return asset;
  }
  const asset = await apiRequest<AssetDto>(`/api/admin/assets/${id}`);
  return mapAsset(asset);
}

export async function updateAsset(asset: Pick<AssetRecord, "id" | "name" | "category" | "description" | "tags" | "enabled">) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    const index = db.assets.findIndex((item) => sameId(item.id, asset.id));
    if (index < 0) throw new Error("素材不存在");
    db.assets[index] = {
      ...db.assets[index],
      ...asset,
      categoryLabel: assetCategoryLabels[asset.category],
      updatedAt: new Date().toISOString()
    };
    mockDb.save(db);
    return db.assets[index];
  }

  const updated = await apiRequest<AssetDto>(`/api/admin/assets/${asset.id}`, {
    method: "PUT",
    body: JSON.stringify({
      name: asset.name,
      category: asset.category,
      description: asset.description,
      tags: asset.tags,
      enabled: asset.enabled
    })
  });
  return mapAsset(updated);
}

export async function deleteAsset(id: EntityId) {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    db.assets = db.assets.filter((asset) => !sameId(asset.id, id));
    mockDb.save(db);
    return;
  }
  await apiRequest<null>(`/api/admin/assets/${id}`, { method: "DELETE" });
}

export async function downloadAsset(asset: AssetRecord) {
  if (enableMock) {
    await wait();
    return {
      blob: mockAssetFile(asset),
      fileName: asset.originalFileName || `${asset.name}.${asset.fileType}`
    };
  }
  return apiDownload(`/api/admin/assets/${asset.id}/download`);
}

export async function importSeedAssets(): Promise<AssetImportResult> {
  if (enableMock) {
    await wait();
    const db = mockDb.data;
    const existing = new Set(db.assets.map((asset) => String(asset.id)));
    const seed = [
      {
        id: "asset-seed-safety",
        name: "安全检查通用数据表",
        category: "OTHER" as AssetCategory,
        categoryLabel: "其他",
        fileType: "xlsx",
        storageType: "MINIO",
        filePath: "minio://report-assets/assets/OTHER/asset-seed-safety.xlsx",
        bucketName: "report-assets",
        objectName: "assets/OTHER/asset-seed-safety.xlsx",
        originalFileName: "安全检查通用数据表.xlsx",
        contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        fileSize: 36864,
        sha256: "mock-asset-seed-safety",
        description: "用于补充报告通用检查数据。",
        tags: "安全,通用",
        enabled: true,
        createdBy: "seed",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }
    ];
    const imported = seed.filter((asset) => !existing.has(String(asset.id)));
    db.assets.unshift(...imported);
    mockDb.save(db);
    return {
      scanned: seed.length,
      imported: imported.length,
      skipped: seed.length - imported.length,
      errors: []
    };
  }
  return apiRequest<AssetImportResult>("/api/admin/assets/import-seed", { method: "POST" });
}

function buildMockDashboard(days: number, source: DashboardData["source"]): DashboardData {
  const reports = mockDb.data.reports.filter((item) => item.status !== "DELETED");
  const failed = reports.filter((item) => item.status === "FAILED").length;
  const exported = reports.filter((item) => item.status === "EXPORTED").length;
  const generating = reports.filter((item) => item.status === "CONTENT_GENERATING").length;
  const contentReady = reports.filter((item) => item.status === "CONTENT_READY").length;
  const totalReports = Math.max(1, reports.length);

  return {
    source,
    updatedAt: new Date().toISOString(),
    metrics: [
      { key: "templates", code: "TPL", label: "报告模板", value: mockDb.data.templates.length, delta: "Mock", tone: "orange" },
      { key: "reportGenerations", code: "RPT", label: "报告总数", value: totalReports, delta: "Mock", tone: "pink" },
      { key: "exportedReports", code: "EXP", label: "DOCX 导出", value: exported, delta: "Mock", tone: "blue" },
      { key: "contentReadyReports", code: "RDY", label: "正文就绪", value: contentReady, delta: "Mock", tone: "green" },
      { key: "generatingReports", code: "RUN", label: "正文生成中", value: generating, delta: "Mock", tone: "cyan" },
      { key: "failedTasks", code: "ERR", label: "生成失败", value: failed, delta: failed ? "需关注" : "稳定", tone: "red" }
    ],
    trends: [{ key: "reportGeneration", title: `报告生成 · 近${days}天`, color: "#ec5da5", points: recentDates(days).map((date, index) => ({ date, count: index === days - 1 ? totalReports : 0 })) }],
    distributions: [
      {
        key: "reportType",
        title: "报告类型分布",
        items: [
          { name: "迎峰度夏", value: reports.filter((item) => item.type === "SUMMER_PEAK_CHECK").length || 1, color: "#1e6bff" },
          { name: "煤库存审计", value: reports.filter((item) => item.type === "COAL_INVENTORY_AUDIT").length, color: "#00b8d9" }
        ]
      },
      {
        key: "reportStatus",
        title: "报告状态分布",
        items: [
          { name: "正文生成中", value: generating, color: "#00b8d9" },
          { name: "正文就绪", value: contentReady, color: "#16a34a" },
          { name: "已导出", value: exported, color: "#1e6bff" },
          { name: "生成失败", value: failed, color: "#dc2626" }
        ]
      }
    ],
    health: [
      { name: "Report API", status: "ONLINE", latencyMs: 42, detail: "报告接口响应正常" },
      { name: "DOCX Export", status: "ONLINE", latencyMs: 88, detail: "报告导出服务可用" },
      { name: "Report SSE", status: "DEGRADED", latencyMs: 180, detail: "正文生成流式通道" }
    ],
    alerts: [
      {
        id: "RPT-ALERT-01",
        level: failed > 0 ? "danger" : "info",
        title: failed > 0 ? "存在失败报告任务" : "报告任务运行正常",
        description: failed > 0 ? `当前存在 ${failed} 条失败任务。` : "近 30 天未发现失败报告任务。",
        time: new Date().toLocaleString()
      }
    ],
    recentTasks: reports.slice(0, 6).map((report) => ({
      id: `RPT-${report.id}`,
      name: report.name,
      type: report.type === "SUMMER_PEAK_CHECK" ? "迎峰度夏检查" : "煤库存审计",
      status: report.status,
      owner: `用户 ${report.ownerId}`,
      duration: `${report.completedSections}/${report.totalSections}`,
      time: new Date(report.updatedAt).toLocaleString()
    }))
  };
}

function recentDates(days: number) {
  return Array.from({ length: days }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (days - index - 1));
    return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  });
}
