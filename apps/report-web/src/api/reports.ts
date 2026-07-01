import { apiDownload, apiRequest, buildApiUrl, buildAuthHeaders, enableMock } from "@/api/http";
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
  ReportSection
} from "@/types/domain";
import { createReportDocxBlob, normalizeDocxFileName } from "@/utils/docx";
import { countContentOutlineNodes, isContentOutlineNode, renumberOutline } from "@/utils/outline";

interface BackendPage<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

interface BackendOutlineNode {
  id?: string;
  number: string;
  title: string;
  level: number;
  promptHint?: string;
  children?: BackendOutlineNode[];
}

interface GenerateOutlineResponse {
  tempId: string;
  source: "AI" | "LOCAL_TEMPLATE";
  expireSeconds: number;
  outline: BackendOutlineNode[];
}

interface ConfirmOutlineResponse {
  reportId: string;
  status: Report["status"];
  outlineCount: number;
  outline: BackendOutlineNode[];
}

interface BackendReportRecord {
  reportId: string;
  name: string;
  type: Report["type"];
  subject: string;
  specialty: string;
  powerPlant: string;
  reportYear: number;
  status: Report["status"];
  totalSections: number;
  completedSections: number;
  createdAt: string;
  updatedAt: string;
  outline?: BackendOutlineNode[];
  sections?: BackendSection[];
}

interface BackendSection {
  sectionId: string;
  outlineNodeId: string;
  reportId: string;
  number: string;
  title: string;
  contentMarkdown: string;
  status: ReportSection["status"];
  source?: ReportSection["source"];
  version: number;
  errorMessage?: string | null;
  createdAt?: string;
  updatedAt: string;
}

interface ExportDocxResponse {
  fileId: string;
  reportId: string;
  fileName: string;
  fileSize: number;
  sha256: string;
  downloadUrl?: string;
}

const DRAFT_PREFIX = "report-web:outline-draft:";
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);
const wait = (ms = 300) => new Promise((resolve) => window.setTimeout(resolve, ms));

function parseSseJson<T>(data: string): T | undefined {
  try {
    return JSON.parse(data) as T;
  } catch {
    return undefined;
  }
}

function draftKey(id: EntityId) {
  return `${DRAFT_PREFIX}${id}`;
}

function readDraft(id: EntityId): ReportDetail | undefined {
  const raw = sessionStorage.getItem(draftKey(id));
  if (!raw) return undefined;
  const draft = JSON.parse(raw) as ReportDetail;
  return { ...draft, status: "DRAFT" };
}

function writeDraft(report: ReportDetail) {
  sessionStorage.setItem(draftKey(report.id), JSON.stringify(report));
}

function removeDraft(id: EntityId) {
  sessionStorage.removeItem(draftKey(id));
}

function reportPayload(payload: CreateReportPayload) {
  return {
    templateId: payload.templateId,
    reportType: payload.type,
    subject: payload.subject,
    name: payload.name,
    specialty: payload.specialty,
    powerPlant: payload.powerPlant,
    reportYear: payload.reportYear
  };
}

function flattenOutline(nodes: BackendOutlineNode[] = [], reportId: EntityId, parentId?: EntityId): OutlineNode[] {
  return nodes.flatMap((node, index) => {
    const id = node.id || `${reportId}-${node.number || index + 1}`;
    const current: OutlineNode = {
      id,
      reportId,
      parentId,
      level: node.level,
      sortOrder: index + 1,
      number: node.number,
      title: node.title,
      promptHint: node.promptHint
    };
    return [current, ...flattenOutline(node.children || [], reportId, id)];
  });
}

function buildOutlineTree(nodes: OutlineNode[], parentId?: EntityId): BackendOutlineNode[] {
  return nodes
    .filter((node) => (parentId ? sameId(node.parentId, parentId) : !node.parentId))
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((node) => ({
      id: String(node.id).startsWith("local-") ? undefined : String(node.id),
      number: node.number,
      title: node.title,
      level: node.level,
      promptHint: node.promptHint,
      children: buildOutlineTree(nodes, node.id)
    }));
}

function mapReport(record: BackendReportRecord): Report {
  return {
    id: record.reportId,
    name: record.name,
    type: record.type,
    subject: record.subject,
    specialty: record.specialty,
    powerPlant: record.powerPlant,
    reportYear: record.reportYear,
    status: record.status,
    ownerId: 0,
    totalSections: record.totalSections,
    completedSections: record.completedSections,
    createdAt: record.createdAt,
    updatedAt: record.updatedAt,
    files: []
  };
}

function mapSection(section: BackendSection): ReportSection {
  return {
    id: section.sectionId,
    reportId: section.reportId,
    outlineNodeId: section.outlineNodeId,
    number: section.number,
    title: section.title,
    contentMarkdown: section.contentMarkdown || "",
    status: section.status,
    source: section.source || "AI",
    version: section.version,
    errorMessage: section.errorMessage || undefined,
    createdAt: section.createdAt || section.updatedAt,
    updatedAt: section.updatedAt
  };
}

function mapDetail(record: BackendReportRecord): ReportDetail {
  return {
    ...mapReport(record),
    outline: flattenOutline(record.outline || [], record.reportId),
    sections: (record.sections || []).map(mapSection)
  };
}

function mapFile(file: ExportDocxResponse): ReportFile {
  return {
    id: file.fileId,
    reportId: file.reportId,
    fileName: file.fileName,
    fileSize: file.fileSize,
    sha256: file.sha256,
    downloadUrl: file.downloadUrl,
    createdAt: new Date().toISOString()
  };
}

function makeDraft(payload: CreateReportPayload, result: GenerateOutlineResponse, idOverride?: EntityId): ReportDetail {
  const id = idOverride || result.tempId || `temp-${Date.now()}`;
  const outline = flattenOutline(result.outline, id);
  return {
    id,
    tempId: result.tempId,
    outlineSource: result.source,
    outlineExpireSeconds: result.expireSeconds,
    name: payload.name,
    type: payload.type,
    subject: payload.subject,
    specialty: payload.specialty,
    powerPlant: payload.powerPlant,
    reportYear: payload.reportYear,
    status: "DRAFT",
    ownerId: 0,
    totalSections: countContentOutlineNodes(outline),
    completedSections: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    outline,
    sections: [],
    files: []
  };
}

export async function listReports(query: ReportQuery): Promise<PageResult<Report>> {
  if (enableMock) return mockListReports(query);

  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.pageSize)
  });
  if (query.status && query.status !== "DELETED") params.set("status", query.status);
  if (query.type) params.set("reportType", query.type);
  if (query.specialty) params.set("specialty", query.specialty);
  if (query.powerPlant) params.set("powerPlant", query.powerPlant);
  if (query.subject || query.keyword) params.set("subject", query.subject || query.keyword || "");
  if (query.reportYear || query.year) params.set("reportYear", String(query.reportYear || query.year));

  const page = await apiRequest<BackendPage<BackendReportRecord>>(`/api/reports/history?${params.toString()}`);
  return {
    items: page.records.map(mapReport),
    total: page.total,
    page: page.page,
    pageSize: page.size
  };
}

export async function createReport(payload: CreateReportPayload) {
  if (enableMock) return mockCreateReport(payload);

  const result = await apiRequest<GenerateOutlineResponse>("/api/reports/outline/generate", {
    method: "POST",
    body: JSON.stringify(reportPayload(payload))
  });
  const draft = makeDraft(payload, result);
  writeDraft(draft);
  return draft;
}

export async function getReport(id: EntityId) {
  if (enableMock) return mockGetReport(id);

  const draft = readDraft(id);
  if (draft) return draft;

  const detail = await apiRequest<BackendReportRecord>(`/api/reports/history/${id}`);
  return mapDetail(detail);
}

export async function deleteReport(id: EntityId) {
  if (enableMock) return mockDeleteReport(id);
  await apiRequest<null>(`/api/reports/history/${id}`, { method: "DELETE" });
}

export async function generateOutline(id: EntityId) {
  if (enableMock) return mockGenerateOutline(id);

  const draft = readDraft(id);
  if (!draft) throw new Error("只能在新建报告临时态重新生成大纲");
  const result = await apiRequest<GenerateOutlineResponse>("/api/reports/outline/generate", {
    method: "POST",
    body: JSON.stringify(reportPayload(draft))
  });
  const nextDraft = makeDraft(draft, result, id);
  writeDraft(nextDraft);
  return nextDraft;
}

export async function saveOutline(id: EntityId, outline: OutlineNode[]) {
  if (enableMock) return mockSaveOutline(id, outline);

  const draft = readDraft(id);
  if (!draft) {
    const detail = await getReport(id);
    return {
      ...detail,
      outline: renumberOutline(outline),
      totalSections: countContentOutlineNodes(outline),
      updatedAt: new Date().toISOString()
    };
  }
  const result = await apiRequest<ConfirmOutlineResponse>("/api/reports/outline/confirm", {
    method: "POST",
    body: JSON.stringify({
      tempId: draft.tempId,
      name: draft.name,
      reportType: draft.type,
      subject: draft.subject,
      specialty: draft.specialty,
      powerPlant: draft.powerPlant,
      reportYear: draft.reportYear,
      outline: buildOutlineTree(renumberOutline(outline))
    })
  });

  removeDraft(id);
  const confirmedOutline = flattenOutline(result.outline || buildOutlineTree(outline), result.reportId);
  return {
    ...draft,
    id: result.reportId,
    tempId: undefined,
    outlineSource: undefined,
    outlineExpireSeconds: undefined,
    status: result.status,
    outline: confirmedOutline,
    totalSections: countContentOutlineNodes(confirmedOutline),
    updatedAt: new Date().toISOString()
  };
}

export async function saveOutlineDraft(id: EntityId, outline: OutlineNode[]) {
  if (enableMock) return mockSaveOutlineDraft(id, outline);

  const draft = readDraft(id) || await getReport(id);
  const nextOutline = renumberOutline(outline);
  const nextDraft: ReportDetail = {
    ...draft,
    tempId: draft.tempId || String(draft.id),
    status: "DRAFT",
    outline: nextOutline,
    totalSections: countContentOutlineNodes(nextOutline),
    updatedAt: new Date().toISOString()
  };
  writeDraft(nextDraft);
  return nextDraft;
}

export async function startGenerate(id: EntityId) {
  if (enableMock) return mockStartGenerate(id);
  await apiRequest(`/api/reports/${id}/sections/generate`, { method: "POST" });
}

export function createGenerateStream(
  id: EntityId,
  onEvent: (event: GenerateStreamEvent) => void,
  onClose?: () => void
) {
  if (enableMock) return mockCreateGenerateStream(id, onEvent, onClose);

  const controller = new AbortController();
  let currentSectionId: EntityId | undefined;
  let currentTotal = 0;
  let currentCompleted = 0;

  async function connect() {
    try {
      const response = await fetch(buildApiUrl(`/api/reports/${id}/sections/stream`), {
        headers: {
          Accept: "text/event-stream",
          ...buildAuthHeaders()
        },
        signal: controller.signal
      });
      if (!response.ok || !response.body) throw new Error(`SSE 连接失败：${response.status}`);

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let done = false;

      while (!done) {
        const chunk = await reader.read();
        done = chunk.done;
        buffer += decoder.decode(chunk.value || new Uint8Array(), { stream: !done });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || "";
        blocks.forEach(handleSseBlock);
      }
      if (buffer.trim()) handleSseBlock(buffer);
    } catch (error) {
      if (!controller.signal.aborted) {
        onEvent({ type: "error", message: error instanceof Error ? error.message : "SSE 连接异常" });
      }
    } finally {
      onClose?.();
    }
  }

  function handleSseBlock(block: string) {
    const lines = block.split(/\r?\n/);
    const event = lines.find((line) => line.startsWith("event:"))?.slice(6).trim() || "message";
    const data = lines
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart())
      .join("\n");

    if (event === "progress") {
      const progress = parseSseJson<{
        current: number;
        total: number;
        sectionId: string;
        sectionNumber?: string;
        number?: string;
        sectionTitle?: string;
        title?: string;
      }>(data);
      if (!progress?.sectionId) return;
      currentSectionId = progress.sectionId;
      currentCompleted = Math.max(0, progress.current - 1);
      currentTotal = Math.max(0, progress.total);
      const percent = currentTotal ? Math.round((currentCompleted / currentTotal) * 100) : 0;
      onEvent({
        type: "section_started",
        sectionId: progress.sectionId,
        number: progress.sectionNumber || progress.number || String(progress.current),
        title: progress.sectionTitle || progress.title || `章节 ${progress.current}`
      });
      onEvent({ type: "progress", completedSections: currentCompleted, totalSections: currentTotal, percent });
      return;
    }

    if (event === "content") {
      const payload = parseSseJson<{ sectionId?: string; delta?: string }>(data);
      const sectionId = payload?.sectionId || currentSectionId;
      const delta = payload?.delta ?? data;
      if (sectionId) onEvent({ type: "content_delta", sectionId, delta });
      return;
    }

    if (event === "section_done") {
      const payload = parseSseJson<{ sectionId?: string }>(data);
      const sectionId = payload?.sectionId || currentSectionId;
      if (!sectionId) return;
      currentCompleted += 1;
      const percent = currentTotal ? Math.round((currentCompleted / currentTotal) * 100) : 0;
      onEvent({ type: "section_completed", sectionId, completedSections: currentCompleted, totalSections: currentTotal });
      onEvent({ type: "progress", completedSections: currentCompleted, totalSections: currentTotal, percent });
      return;
    }

    if (event === "done") {
      onEvent({ type: "task_completed", reportId: id, status: "CONTENT_READY" });
      return;
    }

    if (event === "error") {
      const payload = parseSseJson<{ message?: string; sectionId?: string }>(data);
      onEvent({ type: "error", message: payload?.message || data || "正文生成失败", sectionId: payload?.sectionId || currentSectionId });
    }
  }

  void connect();
  return {
    close() {
      controller.abort();
    }
  };
}

export async function saveSection(reportId: EntityId, sectionId: EntityId, contentMarkdown: string) {
  if (enableMock) return mockSaveSection(reportId, sectionId, contentMarkdown);
  const section = await apiRequest<BackendSection>(`/api/reports/${reportId}/sections/${sectionId}`, {
    method: "PUT",
    body: JSON.stringify({ contentMarkdown })
  });
  return mapSection(section);
}

export async function regenerateSection(reportId: EntityId, sectionId: EntityId, hint = "") {
  if (enableMock) return mockRegenerateSection(reportId, sectionId);
  const regenerated = await apiRequest<BackendSection | undefined>(`/api/reports/${reportId}/sections/${sectionId}/regenerate`, {
    method: "POST",
    body: JSON.stringify({ hint })
  });
  if (regenerated?.sectionId) return mapSection(regenerated);
  const detail = await getReport(reportId);
  const section = detail.sections.find((item) => sameId(item.id, sectionId));
  if (!section) throw new Error("章节已重新生成，但未能获取最新章节内容");
  return section;
}

export async function exportDocx(reportId: EntityId) {
  if (enableMock) return mockExportDocx(reportId);
  const file = await apiRequest<ExportDocxResponse>(`/api/reports/${reportId}/export/docx`, {
    method: "POST",
    body: JSON.stringify({
      figureNumberingMode: "GLOBAL",
      tableNumberingMode: "SECTION",
      includeEmptySections: true
    })
  });
  return mapFile(file);
}

export async function downloadFile(_reportId: EntityId, fileId: EntityId) {
  if (enableMock) return mockDownloadFile(_reportId, fileId);
  return (await apiDownload(`/api/reports/files/${fileId}/download`)).blob;
}

export function applyStreamEvent(detail: ReportDetail, event: GenerateStreamEvent) {
  if (event.type === "section_started") {
    let section = detail.sections.find((item) => sameId(item.id, event.sectionId));
    if (!section) {
      const outline = detail.outline.find((node) => node.number === event.number || node.title === event.title);
      if (outline && !isContentOutlineNode(detail.outline, outline)) return;
      const now = new Date().toISOString();
      section = {
        id: event.sectionId,
        reportId: detail.id,
        outlineNodeId: outline?.id || event.sectionId,
        number: event.number,
        title: event.title,
        contentMarkdown: "",
        status: "GENERATING",
        source: "AI",
        version: 1,
        createdAt: now,
        updatedAt: now
      };
      detail.sections.push(section);
    }
    section.status = "GENERATING";
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

async function mockListReports(query: ReportQuery): Promise<PageResult<Report>> {
  await wait();
  let items = mockDb.data.reports.filter((report) => report.status !== "DELETED");
  const keyword = query.keyword || query.subject;
  if (keyword) {
    const value = keyword.toLowerCase();
    items = items.filter((report) => [report.name, report.subject, report.specialty, report.powerPlant].some((field) => field.toLowerCase().includes(value)));
  }
  if (query.powerPlant) items = items.filter((report) => report.powerPlant.toLowerCase().includes(query.powerPlant!.toLowerCase()));
  if (query.specialty) items = items.filter((report) => report.specialty.toLowerCase().includes(query.specialty!.toLowerCase()));
  if (query.type) items = items.filter((report) => report.type === query.type);
  if (query.status) items = items.filter((report) => report.status === query.status);
  if (query.year || query.reportYear) items = items.filter((report) => report.reportYear === (query.year || query.reportYear));

  items = items.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  const start = (query.page - 1) * query.pageSize;
  return {
    items: items.slice(start, start + query.pageSize),
    page: query.page,
    pageSize: query.pageSize,
    total: items.length
  };
}

async function mockCreateReport(payload: CreateReportPayload) {
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

async function mockGetReport(id: EntityId) {
  await wait(180);
  const report = mockDb.data.reports.find((item) => sameId(item.id, id) && item.status !== "DELETED");
  if (!report) throw new Error("报告不存在或已删除");
  return report;
}

async function mockDeleteReport(id: EntityId) {
  await wait();
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  report.status = "DELETED";
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
}

async function mockGenerateOutline(id: EntityId) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  await wait(550);
  const baseId = mockDb.nextId(db) + 100;
  report.tempId = String(report.id);
  report.outline = mockDb.seedOutline(report.id, baseId, report.type);
  report.sections = mockDb.outlineToSections(report.outline);
  report.status = "DRAFT";
  report.totalSections = report.sections.length;
  report.completedSections = 0;
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
  return report;
}

async function mockSaveOutline(id: EntityId, outline: OutlineNode[]) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  await wait();
  report.outline = renumberOutline(outline);
  report.sections = mockDb.outlineToSections(report.outline);
  report.totalSections = report.sections.length;
  report.completedSections = 0;
  report.tempId = undefined;
  report.status = "OUTLINE_READY";
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
  return report;
}

async function mockSaveOutlineDraft(id: EntityId, outline: OutlineNode[]) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  await wait();
  report.outline = renumberOutline(outline);
  report.sections = mockDb.outlineToSections(report.outline);
  report.totalSections = report.sections.length;
  report.completedSections = 0;
  report.tempId = String(report.id);
  report.status = "DRAFT";
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
  return report;
}

async function mockStartGenerate(id: EntityId) {
  await wait(240);
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, id));
  if (!report) throw new Error("报告不存在");
  report.status = "CONTENT_GENERATING";
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
}

function mockCreateGenerateStream(
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
    timers.push(window.setTimeout(() => {
      if (closed) return;
      section.status = "GENERATING";
      mockDb.save(db);
      onEvent({ type: "section_started", sectionId: section.id, number: section.number, title: section.title });
    }, delay));
    delay += 260;

    [`## ${section.number} ${section.title}\n\n`, `本节依据报告主题和模板结构，对“${section.title}”进行业务化分析。`, "\n\n| 项目 | 当前判断 | 后续措施 |\n| --- | --- | --- |\n", `| ${section.title} | 已完成初稿 | 用户复核并补充现场数据 |\n`].forEach((delta) => {
      timers.push(window.setTimeout(() => {
        if (closed) return;
        section.contentMarkdown += delta;
        section.updatedAt = new Date().toISOString();
        mockDb.save(db);
        onEvent({ type: "content_delta", sectionId: section.id, delta });
      }, delay));
      delay += 220;
    });

    timers.push(window.setTimeout(() => {
      if (closed) return;
      section.status = "GENERATED";
      completed += 1;
      report.completedSections = completed;
      mockDb.save(db);
      onEvent({ type: "section_completed", sectionId: section.id, completedSections: completed, totalSections: report.sections.length });
      onEvent({ type: "progress", completedSections: completed, totalSections: report.sections.length, percent: Math.round((completed / report.sections.length) * 100) });
    }, delay));
    delay += 320;
  });

  timers.push(window.setTimeout(() => {
    if (closed) return;
    report.status = "CONTENT_READY";
    report.generatedAt = new Date().toISOString();
    report.completedSections = report.sections.length;
    report.updatedAt = new Date().toISOString();
    mockDb.save(db);
    onEvent({ type: "task_completed", reportId: report.id, status: "CONTENT_READY" });
    onClose?.();
  }, delay + 200));

  return {
    close() {
      closed = true;
      timers.forEach((timer) => window.clearTimeout(timer));
    }
  };
}

async function mockSaveSection(reportId: EntityId, sectionId: EntityId, contentMarkdown: string) {
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
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
  return section;
}

async function mockRegenerateSection(reportId: EntityId, sectionId: EntityId) {
  await wait(500);
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, reportId));
  const section = report?.sections.find((item) => sameId(item.id, sectionId));
  if (!report || !section) throw new Error("章节不存在");
  section.contentMarkdown = `## ${section.number} ${section.title}\n\n本章节已根据当前大纲重新生成。内容保留专业报告口径，并补充风险、数据依据和整改闭环要求。`;
  section.status = "GENERATED";
  section.source = "REGENERATED";
  section.version += 1;
  section.updatedAt = new Date().toISOString();
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
  return section;
}

async function mockExportDocx(reportId: EntityId) {
  const db = mockDb.data;
  const report = db.reports.find((item) => sameId(item.id, reportId));
  if (!report) throw new Error("报告不存在");
  const fileName = normalizeDocxFileName(report.name);
  await wait(800);
  report.status = "EXPORTED";
  const blob = await createReportDocxBlob(report);
  const file = mockDb.makeFile(mockDb.nextId(db), reportId, fileName, blob.size);
  report.files.unshift(file);
  report.updatedAt = new Date().toISOString();
  mockDb.save(db);
  return file;
}

async function mockDownloadFile(reportId: EntityId, fileId: EntityId) {
  const report = mockDb.data.reports.find((item) => sameId(item.id, reportId));
  const file = report?.files.find((item) => sameId(item.id, fileId));
  if (!report || !file) throw new Error("文件不存在或已失效");
  await wait(180);
  return createReportDocxBlob(report);
}
