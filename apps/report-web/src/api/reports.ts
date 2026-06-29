import { apiDownload, apiRequest, enableMock } from "@/api/http";
import { mockDb } from "@/api/mockDb";
import type {
  CreateReportPayload,
  EntityId,
  GenerateStreamEvent,
  OutlineNode,
  PageResult,
  Report,
  ReportDetail,
  ReportFile,
  ReportQuery,
  ReportSection,
  ReportStatus
} from "@/types/domain";
import { createReportDocxBlob, normalizeDocxFileName } from "@/utils/docx";
import { renumberOutline } from "@/utils/outline";

const wait = (ms = 300) => new Promise((resolve) => window.setTimeout(resolve, ms));
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);

interface BackendOutlineNode {
  id?: string;
  number: string;
  title: string;
  level: number;
  promptHint?: string;
  children?: BackendOutlineNode[];
}

interface BackendOutlineGenerateData {
  tempId: string;
  source: "AI" | "LOCAL_TEMPLATE";
  expireSeconds: number;
  outline: BackendOutlineNode[];
}

interface BackendOutlineConfirmData {
  reportId: string;
  status: string;
  outlineCount: number;
  outline: BackendOutlineNode[];
}

interface BackendExportData {
  fileId: string;
  reportId: string;
  fileName: string;
  fileSize: number;
  sha256: string;
  downloadUrl: string;
}

interface ExportDocxOptions {
  fileName?: string;
}

function touch(report: ReportDetail) {
  report.updatedAt = new Date().toISOString();
}

function findReport(id: EntityId) {
  return mockDb.data.reports.find((item) => sameId(item.id, id) && item.status !== "DELETED");
}

function upsertReport(report: ReportDetail, replacedId?: EntityId) {
  const db = mockDb.data;
  db.reports = db.reports.filter((item) => !sameId(item.id, report.id) && !sameId(item.id, replacedId));
  db.reports.unshift(report);
  mockDb.save(db);
}

function toGeneratePayload(report: Report | CreateReportPayload) {
  return {
    reportType: report.type,
    subject: report.subject,
    name: report.name,
    specialty: report.specialty,
    powerPlant: report.powerPlant,
    reportYear: report.reportYear,
    context: {}
  };
}

function flattenBackendOutline(nodes: BackendOutlineNode[], reportId: EntityId, parentId?: EntityId, result: OutlineNode[] = []) {
  nodes.forEach((node, index) => {
    const nodeId = node.id || `${parentId ?? "root"}-${index + 1}`;
    result.push({
      id: nodeId,
      reportId,
      parentId,
      level: node.level,
      sortOrder: index + 1,
      number: node.number,
      title: node.title,
      promptHint: node.promptHint
    });
    if (node.children?.length) flattenBackendOutline(node.children, reportId, nodeId, result);
  });
  return result;
}

function nestOutline(outline: OutlineNode[]) {
  const byParent = new Map<string, OutlineNode[]>();
  outline.forEach((node) => {
    const key = node.parentId === undefined ? "ROOT" : String(node.parentId);
    byParent.set(key, [...(byParent.get(key) || []), node]);
  });

  const build = (parentKey = "ROOT"): BackendOutlineNode[] =>
    (byParent.get(parentKey) || [])
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((node) => ({
        number: node.number,
        title: node.title,
        level: node.level,
        promptHint: node.promptHint,
        children: build(String(node.id))
      }));

  return build();
}

function createLocalDraft(payload: CreateReportPayload, generated: BackendOutlineGenerateData) {
  const now = new Date().toISOString();
  const outline = flattenBackendOutline(generated.outline, generated.tempId);
  const report: ReportDetail = {
    id: generated.tempId,
    tempId: generated.tempId,
    outlineSource: generated.source,
    outlineExpireSeconds: generated.expireSeconds,
    ...payload,
    status: "OUTLINE_READY",
    ownerId: 1,
    totalSections: outline.length,
    completedSections: 0,
    createdAt: now,
    updatedAt: now,
    outline,
    sections: mockDb.outlineToSections(outline),
    files: []
  };
  upsertReport(report);
  return report;
}

function normalizeBackendStatus(status: string): ReportStatus {
  const known: ReportStatus[] = ["DRAFT", "OUTLINE_READY", "GENERATING", "CONTENT_READY", "EXPORTING", "EXPORTED", "FAILED", "DELETED"];
  return known.includes(status as ReportStatus) ? (status as ReportStatus) : "DRAFT";
}

export async function listReports(query: ReportQuery): Promise<PageResult<Report>> {
  await wait();
  const db = mockDb.data;
  let items = db.reports.filter((report) => report.status !== "DELETED");

  if (query.keyword) {
    const keyword = query.keyword.toLowerCase();
    items = items.filter(
      (report) =>
        report.name.toLowerCase().includes(keyword) ||
        report.subject.toLowerCase().includes(keyword) ||
        report.powerPlant.toLowerCase().includes(keyword)
    );
  }
  if (query.type) items = items.filter((report) => report.type === query.type);
  if (query.status) items = items.filter((report) => report.status === query.status);
  if (query.year) items = items.filter((report) => report.reportYear === query.year);

  items = items.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  const start = (query.page - 1) * query.pageSize;

  return {
    items: items.slice(start, start + query.pageSize),
    page: query.page,
    pageSize: query.pageSize,
    total: items.length
  };
}

export async function createReport(payload: CreateReportPayload) {
  if (!enableMock) {
    const generated = await apiRequest<BackendOutlineGenerateData>("/api/reports/outline/generate", {
      method: "POST",
      body: JSON.stringify(toGeneratePayload(payload))
    });
    return createLocalDraft(payload, generated);
  }

  await wait();
  const db = mockDb.data;
  const id = mockDb.nextId(db);
  const report: ReportDetail = {
    id,
    ...payload,
    status: "DRAFT",
    ownerId: 1,
    totalSections: 0,
    completedSections: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    outline: [],
    sections: [],
    files: []
  };
  db.reports.push(report);
  mockDb.save(db);
  return report;
}

export async function getReport(id: EntityId) {
  await wait(180);
  const report = findReport(id);
  if (!report) throw new Error("报告不存在或已删除");
  return report;
}

export async function deleteReport(id: EntityId) {
  await wait();
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  report.status = "DELETED";
  touch(report);
  mockDb.save(db);
}

export async function generateOutline(id: EntityId) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");

  if (!enableMock) {
    const generated = await apiRequest<BackendOutlineGenerateData>("/api/reports/outline/generate", {
      method: "POST",
      body: JSON.stringify(toGeneratePayload(report))
    });
    report.tempId = generated.tempId;
    report.outlineSource = generated.source;
    report.outlineExpireSeconds = generated.expireSeconds;
    report.outline = flattenBackendOutline(generated.outline, report.id);
    report.sections = mockDb.outlineToSections(report.outline);
    report.status = "OUTLINE_READY";
    report.totalSections = report.sections.length;
    report.completedSections = 0;
    touch(report);
    mockDb.save(db);
    return report;
  }

  await wait(550);
  const baseId = mockDb.nextId(db) + 100;
  report.outline = mockDb.seedOutline(report.id, baseId, report.type);
  report.sections = mockDb.outlineToSections(report.outline);
  report.status = "OUTLINE_READY";
  report.totalSections = report.sections.length;
  report.completedSections = 0;
  touch(report);
  mockDb.save(db);
  return report;
}

export async function saveOutline(id: EntityId, outline: OutlineNode[]) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");

  if (!enableMock) {
    if (!report.tempId) throw new Error("缺少大纲临时 ID，请重新生成大纲");
    const confirmed = await apiRequest<BackendOutlineConfirmData>("/api/reports/outline/confirm", {
      method: "POST",
      body: JSON.stringify({
        tempId: report.tempId,
        ...toGeneratePayload(report),
        outline: nestOutline(renumberOutline(outline))
      })
    });
    const confirmedOutline = flattenBackendOutline(confirmed.outline, confirmed.reportId);
    const nextReport: ReportDetail = {
      ...report,
      id: confirmed.reportId,
      status: normalizeBackendStatus(confirmed.status),
      totalSections: confirmed.outlineCount || confirmedOutline.length,
      completedSections: 0,
      updatedAt: new Date().toISOString(),
      outline: confirmedOutline,
      sections: mockDb.outlineToSections(confirmedOutline)
    };
    upsertReport(nextReport, id);
    return nextReport;
  }

  await wait();
  report.outline = renumberOutline(outline);
  report.sections = mockDb.outlineToSections(report.outline);
  report.totalSections = report.sections.length;
  report.completedSections = 0;
  report.status = "OUTLINE_READY";
  touch(report);
  mockDb.save(db);
  return report;
}

export async function startGenerate(id: EntityId) {
  await wait(240);
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  report.status = "GENERATING";
  touch(report);
  mockDb.save(db);
}

export function createGenerateStream(
  id: EntityId,
  onEvent: (event: GenerateStreamEvent) => void,
  onClose?: () => void
) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  let closed = false;
  const timers: number[] = [];

  if (!report) {
    window.setTimeout(() => onEvent({ type: "error", message: "报告不存在" }), 0);
    return { close: () => undefined };
  }

  const sections = report.sections.filter((section) => section.status !== "GENERATED");
  onEvent({ type: "task_started", reportId: report.id, taskId: `mock-${Date.now()}`, totalSections: report.sections.length });

  let delay = 400;
  let completed = report.completedSections;

  sections.forEach((section) => {
    timers.push(
      window.setTimeout(() => {
        if (closed) return;
        section.status = "GENERATING";
        mockDb.save(db);
        onEvent({ type: "section_started", sectionId: section.id, number: section.number, title: section.title });
      }, delay)
    );
    delay += 260;

    const chunks = [
      `## ${section.number} ${section.title}\n\n`,
      `本节依据报告主题和模板结构，对“${section.title}”进行业务化分析。`,
      "系统将检查范围、数据依据、异常风险和整改措施拆分为可复核条目，便于导出后继续审签。\n\n",
      "| 项目 | 当前判断 | 后续措施 |\n| --- | --- | --- |\n",
      `| ${section.title} | 已完成初稿 | 用户复核并补充现场数据 |\n`
    ];

    chunks.forEach((delta) => {
      timers.push(
        window.setTimeout(() => {
          if (closed) return;
          section.contentMarkdown += delta;
          section.updatedAt = new Date().toISOString();
          mockDb.save(db);
          onEvent({ type: "content_delta", sectionId: section.id, delta });
        }, delay)
      );
      delay += 220;
    });

    timers.push(
      window.setTimeout(() => {
        if (closed) return;
        section.status = "GENERATED";
        completed += 1;
        report.completedSections = completed;
        mockDb.save(db);
        onEvent({
          type: "section_completed",
          sectionId: section.id,
          completedSections: completed,
          totalSections: report.sections.length
        });
        onEvent({
          type: "progress",
          completedSections: completed,
          totalSections: report.sections.length,
          percent: Math.round((completed / report.sections.length) * 100)
        });
      }, delay)
    );
    delay += 320;
  });

  timers.push(
    window.setTimeout(() => {
      if (closed) return;
      report.status = "CONTENT_READY";
      report.generatedAt = new Date().toISOString();
      report.completedSections = report.sections.length;
      touch(report);
      mockDb.save(db);
      onEvent({ type: "task_completed", reportId: report.id, status: "CONTENT_READY" });
      onClose?.();
    }, delay + 200)
  );

  return {
    close() {
      closed = true;
      timers.forEach((timer) => window.clearTimeout(timer));
    }
  };
}

export async function saveSection(reportId: EntityId, sectionId: EntityId, contentMarkdown: string) {
  await wait();
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, reportId));
  const section = report?.sections.find((item) => sameId(item.id, sectionId));
  if (!report || !section) throw new Error("章节不存在");
  section.contentMarkdown = contentMarkdown;
  section.status = "USER_EDITED";
  section.source = "USER_EDITED";
  section.version += 1;
  section.updatedAt = new Date().toISOString();
  touch(report);
  mockDb.save(db);
  return section;
}

export async function regenerateSection(reportId: EntityId, sectionId: EntityId) {
  await wait(500);
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, reportId));
  const section = report?.sections.find((item) => sameId(item.id, sectionId));
  if (!report || !section) throw new Error("章节不存在");
  section.contentMarkdown = `## ${section.number} ${section.title}\n\n本章节已根据当前大纲重新生成。内容保留专业报告口径，并补充风险、数据依据和整改闭环要求。\n\n- 数据来源：模板配置与专业素材。\n- 风险判断：按电力行业检查口径归类。\n- 整改建议：明确责任、时限和复核方式。`;
  section.status = "GENERATED";
  section.source = "REGENERATED";
  section.version += 1;
  section.updatedAt = new Date().toISOString();
  touch(report);
  mockDb.save(db);
  return section;
}

export async function exportDocx(reportId: EntityId, options: ExportDocxOptions = {}) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, reportId));
  if (!report) throw new Error("报告不存在");
  const fileName = normalizeDocxFileName(options.fileName, report.name);

  if (!enableMock) {
    const exported = await apiRequest<BackendExportData>(`/api/reports/${encodeURIComponent(String(reportId))}/export/docx`, {
      method: "POST",
      body: JSON.stringify({
        fileName,
        figureNumberingMode: "GLOBAL",
        tableNumberingMode: "SECTION",
        includeEmptySections: true
      })
    });
    const file: ReportFile = {
      id: exported.fileId,
      reportId: exported.reportId,
      fileName: normalizeDocxFileName(exported.fileName || fileName, report.name),
      fileSize: exported.fileSize,
      sha256: exported.sha256,
      downloadUrl: exported.downloadUrl,
      createdAt: new Date().toISOString()
    };
    report.status = "EXPORTED";
    report.files.unshift(file);
    touch(report);
    mockDb.save(db);
    return file;
  }

  await wait(800);
  report.status = "EXPORTED";
  const blob = await createReportDocxBlob(report);
  const file = mockDb.makeFile(mockDb.nextId(db), reportId, fileName, blob.size);
  report.files.unshift(file);
  touch(report);
  mockDb.save(db);
  return file;
}

export async function downloadFile(reportId: EntityId, fileId: EntityId) {
  const report = mockDb.data.reports.find((item) => sameId(item.id, reportId));
  const file = report?.files.find((item) => sameId(item.id, fileId));
  if (!report || !file) throw new Error("文件不存在或已失效");

  if (!enableMock) {
    return apiDownload(file.downloadUrl || `/api/reports/files/${encodeURIComponent(String(fileId))}/download`);
  }

  await wait(180);
  return createReportDocxBlob(report);
}

export function applyStreamEvent(detail: ReportDetail, event: GenerateStreamEvent) {
  if (event.type === "section_started") {
    const section = detail.sections.find((item) => sameId(item.id, event.sectionId));
    if (section) section.status = "GENERATING";
  }
  if (event.type === "content_delta") {
    const section = detail.sections.find((item) => sameId(item.id, event.sectionId));
    if (section) section.contentMarkdown += event.delta;
  }
  if (event.type === "section_completed") {
    const section = detail.sections.find((item) => sameId(item.id, event.sectionId));
    if (section) section.status = "GENERATED";
    detail.completedSections = event.completedSections;
  }
  if (event.type === "task_completed") {
    detail.status = "CONTENT_READY";
    detail.completedSections = detail.totalSections;
  }
}

export function exportPrecheck(report: ReportDetail) {
  const unsaved = report.sections.filter((section) => section.status === "USER_EDITED").length;
  const failed = report.sections.filter((section) => section.status === "FAILED").length;
  const empty = report.sections.filter((section) => !section.contentMarkdown.trim()).length;
  return { unsaved, failed, empty };
}
