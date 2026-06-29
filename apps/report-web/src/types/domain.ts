export type ReportType = "SUMMER_PEAK_CHECK" | "COAL_INVENTORY_AUDIT";

export type EntityId = string | number;

export type ReportStatus =
  | "DRAFT"
  | "OUTLINE_READY"
  | "GENERATING"
  | "CONTENT_READY"
  | "EXPORTING"
  | "EXPORTED"
  | "FAILED"
  | "DELETED";

export type SectionStatus = "PENDING" | "GENERATING" | "GENERATED" | "USER_EDITED" | "FAILED";

export interface Report {
  id: EntityId;
  tempId?: string;
  outlineSource?: "AI" | "LOCAL_TEMPLATE";
  outlineExpireSeconds?: number;
  name: string;
  type: ReportType;
  subject: string;
  specialty: string;
  powerPlant: string;
  reportYear: number;
  status: ReportStatus;
  ownerId: number;
  totalSections: number;
  completedSections: number;
  generatedAt?: string;
  createdAt: string;
  updatedAt: string;
  files: ReportFile[];
}

export interface CreateReportPayload {
  name: string;
  type: ReportType;
  subject: string;
  specialty: string;
  powerPlant: string;
  reportYear: number;
}

export interface OutlineNode {
  id: EntityId;
  reportId: EntityId;
  parentId?: EntityId;
  level: number;
  sortOrder: number;
  number: string;
  title: string;
  promptHint?: string;
}

export interface ReportSection {
  id: EntityId;
  reportId: EntityId;
  outlineNodeId: EntityId;
  number: string;
  title: string;
  contentMarkdown: string;
  tableJson?: TableBlock;
  status: SectionStatus;
  source: "AI" | "USER_EDITED" | "REGENERATED";
  version: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TableBlock {
  columns: string[];
  rows: string[][];
}

export interface ReportFile {
  id: EntityId;
  reportId: EntityId;
  fileName: string;
  fileSize: number;
  sha256: string;
  downloadUrl?: string;
  createdAt: string;
}

export interface ReportDetail extends Report {
  outline: OutlineNode[];
  sections: ReportSection[];
}

export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface ReportQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  type?: ReportType | null;
  status?: ReportStatus | null;
  year?: number | null;
}

export interface TemplateRecord {
  id: EntityId;
  name: string;
  reportType: ReportType;
  version: string;
  enabled: boolean;
  createdBy: string;
  createdAt: string;
}

export interface LlmConfig {
  id: EntityId;
  provider: "OLLAMA" | "OPENAI_COMPATIBLE";
  baseUrl: string;
  apiKeyConfigured: boolean;
  modelName: string;
  timeoutSeconds: number;
  enabled: boolean;
}

export type GenerateStreamEvent =
  | { type: "task_started"; reportId: EntityId; taskId: string; totalSections: number }
  | { type: "section_started"; sectionId: EntityId; number: string; title: string }
  | { type: "content_delta"; sectionId: EntityId; delta: string }
  | { type: "section_completed"; sectionId: EntityId; completedSections: number; totalSections: number }
  | { type: "progress"; completedSections: number; totalSections: number; percent: number }
  | { type: "task_completed"; reportId: EntityId; status: "CONTENT_READY" }
  | { type: "error"; message: string; sectionId?: EntityId };
