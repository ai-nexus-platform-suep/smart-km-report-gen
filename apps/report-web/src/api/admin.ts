import { apiDownload, apiRequest, enableMock } from "@/api/http";
import { mockDb } from "@/api/mockDb";
import type { EntityId, LlmConfig, ReportType, TemplateRecord } from "@/types/domain";

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
  filePath?: string;
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

interface LlmConfigDto {
  apiUrl: string;
  apiKey?: string;
  modelName: string;
  timeoutSeconds: number;
}

const distributionColors = ["#1e6bff", "#00b8d9", "#16a34a", "#f59e0b", "#dc2626", "#8b5cf6"];
const defaultTemplateConfig: TemplateStyleConfig = { titleSize: 18, bodySize: 12, lineHeight: 1.5, header: "示范电厂" };

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
    enabled: template.enabled,
    createdBy: template.createdBy,
    createdAt: template.createdAt
  };
}

export async function listTemplates(params: { page?: number; size?: number; reportType?: ReportType | null; enabled?: boolean | null; keyword?: string } = {}) {
  if (enableMock) {
    await wait();
    return mockDb.data.templates;
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
  return apiDownload(`/api/admin/templates/${id}/download`);
}

export async function getTemplateConfig(id: EntityId): Promise<TemplateStyleConfig> {
  if (enableMock) {
    await wait();
    return { ...defaultTemplateConfig };
  }
  const config = await apiRequest<Partial<TemplateStyleConfig>>(`/api/admin/templates/${id}/config`);
  return { ...defaultTemplateConfig, ...config };
}

export async function updateTemplateConfig(id: EntityId, config: TemplateStyleConfig) {
  if (enableMock) {
    await wait();
    return config;
  }
  return apiRequest<TemplateStyleConfig>(`/api/admin/templates/${id}/config`, {
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
