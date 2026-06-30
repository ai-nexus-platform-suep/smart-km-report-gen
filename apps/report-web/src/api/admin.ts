import { mockDb } from "@/api/mockDb";
import type { EntityId, LlmConfig, TemplateRecord } from "@/types/domain";

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

interface DashboardOverviewDto {
  templateCount?: number;
  templates?: number;
  reportGenerationCount?: number;
  reportGenerations?: number;
  exportedReportCount?: number;
  exportedReports?: number;
  contentReadyReportCount?: number;
  contentReadyReports?: number;
  generatingReportCount?: number;
  generatingReports?: number;
  failureTaskCount?: number;
  failedTasks?: number;
}

interface DashboardDistributionDto {
  reportType?: Array<Omit<DistributionItem, "color">>;
  reportStatus?: Array<Omit<DistributionItem, "color">>;
}

const metricDefs = [
  ["templates", "TPL", "报告模板", "templateCount", "orange"],
  ["reportGenerations", "RPT", "报告生成", "reportGenerationCount", "pink"],
  ["exportedReports", "EXP", "DOCX 导出", "exportedReportCount", "blue"],
  ["contentReadyReports", "RDY", "正文就绪", "contentReadyReportCount", "green"],
  ["generatingReports", "RUN", "正文生成中", "generatingReportCount", "cyan"],
  ["failedTasks", "ERR", "生成失败", "failureTaskCount", "red"]
] as const;

const distributionColors = ["#1e6bff", "#00b8d9", "#16a34a", "#f59e0b", "#dc2626", "#8b5cf6"];
const reportTrendKeys = new Set(["reportGeneration", "reportExport", "reportFailure", "reportRunning"]);

export async function fetchDashboardData(days = 30): Promise<DashboardData> {
  return buildMockDashboard(days, "mock");
}

function normalizeMetrics(overview: DashboardOverviewDto): DashboardMetric[] {
  return metricDefs.map(([key, code, label, primary, tone]) => {
    const value = overview[primary] ?? overview[key as keyof DashboardOverviewDto] ?? 0;
    return {
      key,
      code,
      label,
      value: Number(value),
      delta: key === "failedTasks" ? "需关注" : "报告侧",
      tone
    };
  });
}

function normalizeReportTrends(trends: ActivityTrend[], fallback: ActivityTrend[]) {
  const reportTrends = trends.filter((trend) => reportTrendKeys.has(trend.key));
  return reportTrends.length ? reportTrends : fallback;
}

function normalizeDistributions(distribution: DashboardDistributionDto): DistributionGroup[] {
  const reportType = (distribution.reportType || []).map((item, index) => ({
    ...item,
    color: distributionColors[index % distributionColors.length]
  }));
  const reportStatus = (distribution.reportStatus || []).map((item, index) => ({
    ...item,
    color: distributionColors[(index + 2) % distributionColors.length]
  }));

  return [
    { key: "reportType", title: "报告类型分布", items: reportType },
    { key: "reportStatus", title: "报告状态分布", items: reportStatus }
  ];
}

function buildMockDashboard(days: number, source: DashboardData["source"]): DashboardData {
  const reports = mockDb.data.reports.filter((item) => item.status !== "DELETED");
  const failed = reports.filter((item) => item.status === "FAILED").length;
  const exported = reports.filter((item) => item.status === "EXPORTED").length;
  const generating = reports.filter((item) => item.status === "CONTENT_GENERATING").length;
  const incomplete = reports.filter((item) => item.status === "CONTENT_INCOMPLETE").length;
  const contentReady = reports.filter((item) => item.status === "CONTENT_READY").length;
  const totalReports = Math.max(1, reports.length);

  const overview: DashboardOverviewDto = {
    templateCount: 7,
    reportGenerationCount: totalReports,
    exportedReportCount: exported,
    contentReadyReportCount: contentReady,
    generatingReportCount: generating,
    failureTaskCount: failed
  };

  return {
    source,
    updatedAt: new Date().toISOString(),
    metrics: normalizeMetrics(overview),
    trends: [
      buildTrend("reportGeneration", "报告生成 · 近30天", "#ec5da5", days, [0, 0, 0, 0, totalReports]),
      buildTrend("reportExport", "DOCX 导出 · 近30天", "#1e6bff", days, [0, 0, 0, exported]),
      buildTrend("reportRunning", "正文生成中 · 近30天", "#00b8d9", days, [0, 0, 0, generating]),
      buildTrend("reportFailure", "生成失败 · 近30天", "#dc2626", days, [0, 0, 0, failed])
    ],
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
          { name: "正文待补全", value: incomplete, color: "#f59e0b" },
          { name: "正文就绪", value: contentReady, color: "#16a34a" },
          { name: "已导出", value: exported, color: "#1e6bff" },
          { name: "生成失败", value: failed, color: "#dc2626" }
        ]
      }
    ],
    health: [
      { name: "Report API", status: "ONLINE", latencyMs: 42, detail: "报告接口响应正常" },
      { name: "DOCX Export", status: "ONLINE", latencyMs: 88, detail: "报告导出服务可用" },
      { name: "Report SSE", status: "DEGRADED", latencyMs: 180, detail: "正文生成流式通道" },
      { name: "Report Mock", status: source === "mock" ? "DEGRADED" : "ONLINE", latencyMs: 12, detail: source === "mock" ? "报告侧本地演示数据" : "真实报告统计数据" }
    ],
    alerts: [
      {
        id: "RPT-ALERT-01",
        level: failed > 0 ? "danger" : "info",
        title: failed > 0 ? "存在失败报告任务" : "报告任务运行正常",
        description: failed > 0 ? `当前存在 ${failed} 条失败任务，请进入报告记录复核。` : "近 30 天未发现失败报告任务。",
        time: new Date().toLocaleString()
      },
      {
        id: "RPT-ALERT-02",
        level: source === "mock" ? "warning" : "info",
        title: source === "mock" ? "报告统计接口未接入" : "报告统计接口已接入",
        description: source === "mock" ? "当前使用 C 组本地报告 mock 数据，后端需补齐报告统计接口。" : "当前展示来自后端报告统计接口的数据。",
        time: new Date().toLocaleString()
      }
    ],
    recentTasks: reports.slice(0, 6).map((report, index) => ({
      id: `RPT-${report.id}`,
      name: report.name,
      type: report.type === "SUMMER_PEAK_CHECK" ? "迎峰度夏检查" : "煤库存审计",
      status: report.status,
      owner: `用户 ${report.ownerId}`,
      duration: `${3 + index * 2}m ${18 + index * 4}s`,
      time: new Date(report.updatedAt).toLocaleString()
    }))
  };
}

function buildTrend(key: string, title: string, color: string, days: number, tailValues: number[]): ActivityTrend {
  const dates = recentDates(days);
  const points = dates.map((date, index) => ({
    date,
    count: index >= dates.length - tailValues.length ? tailValues[index - (dates.length - tailValues.length)] : 0
  }));

  return { key, title, color, points };
}

function recentDates(days: number) {
  return Array.from({ length: days }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (days - index - 1));
    return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  });
}

export async function listTemplates() {
  await wait();
  return mockDb.data.templates;
}

export async function addTemplate(template: Omit<TemplateRecord, "id" | "createdAt" | "createdBy">) {
  await wait();
  const db = mockDb.data;
  const record: TemplateRecord = {
    ...template,
    id: mockDb.nextId(db),
    createdAt: new Date().toISOString(),
    createdBy: "当前管理员"
  };
  db.templates.unshift(record);
  mockDb.save(db);
  return record;
}

export async function updateTemplate(template: TemplateRecord) {
  await wait();
  const db = mockDb.data;
  const index = db.templates.findIndex((item) => sameId(item.id, template.id));
  if (index < 0) throw new Error("模板不存在");
  db.templates[index] = template;
  mockDb.save(db);
  return template;
}

export async function deleteTemplate(id: EntityId) {
  await wait();
  const db = mockDb.data;
  db.templates = db.templates.filter((template) => !sameId(template.id, id));
  mockDb.save(db);
}

export async function listLlmConfigs() {
  await wait();
  return mockDb.data.llmConfigs;
}

export async function saveLlmConfig(config: LlmConfig) {
  await wait();
  const db = mockDb.data;
  const index = db.llmConfigs.findIndex((item) => sameId(item.id, config.id));
  let record = config;

  if (index >= 0) {
    db.llmConfigs[index] = config;
  } else {
    record = { ...config, id: mockDb.nextId(db) };
    db.llmConfigs.unshift(record);
  }

  mockDb.save(db);
  return record;
}

export async function testLlmConfig(id: EntityId) {
  await wait(800);
  const config = mockDb.data.llmConfigs.find((item) => sameId(item.id, id));
  if (!config) throw new Error("模型配置不存在");
  return {
    success: config.enabled,
    message: config.enabled ? "连接成功，模型响应时间 286ms" : "配置未启用，无法测试"
  };
}
