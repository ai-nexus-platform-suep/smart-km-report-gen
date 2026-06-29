import type { ReportStatus, ReportType, SectionStatus } from "@/types/domain";

export const reportTypeLabels: Record<ReportType, string> = {
  SUMMER_PEAK_CHECK: "迎峰度夏检查报告",
  COAL_INVENTORY_AUDIT: "煤库存审计报告"
};

export const reportStatusLabels: Record<ReportStatus, string> = {
  DRAFT: "草稿",
  OUTLINE_READY: "大纲就绪",
  GENERATING: "生成中",
  CONTENT_READY: "正文就绪",
  EXPORTING: "导出中",
  EXPORTED: "已导出",
  FAILED: "失败",
  DELETED: "已删除"
};

export const sectionStatusLabels: Record<SectionStatus, string> = {
  PENDING: "等待生成",
  GENERATING: "正在生成",
  GENERATED: "AI 已生成",
  USER_EDITED: "用户已编辑",
  FAILED: "生成失败"
};

export function statusTone(status: ReportStatus | SectionStatus) {
  if (status === "GENERATING" || status === "EXPORTING") return "generating";
  if (status === "CONTENT_READY" || status === "EXPORTED" || status === "GENERATED") return "success";
  if (status === "OUTLINE_READY" || status === "USER_EDITED") return "warning";
  if (status === "FAILED") return "danger";
  return "blue";
}

export function formatBytes(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
