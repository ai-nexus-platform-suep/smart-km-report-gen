import type {
  AssetRecord,
  EntityId,
  LlmConfig,
  OutlineNode,
  ReportDetail,
  ReportFile,
  ReportSection,
  ReportStatus,
  ReportType,
  TemplateRecord
} from "@/types/domain";
import { isContentOutlineNode } from "@/utils/outline";

interface MockDb {
  reports: ReportDetail[];
  templates: TemplateRecord[];
  assets: AssetRecord[];
  llmConfigs: LlmConfig[];
  nextId: number;
}

const DB_KEY = "power-report-generator:report-user-mock-db";

function now() {
  return new Date().toISOString();
}

function makeFile(id: EntityId, reportId: EntityId, fileName: string, fileSize = 384 * 1024): ReportFile {
  return {
    id,
    reportId,
    fileName,
    fileSize,
    sha256: `mock-${reportId}-${id}`,
    createdAt: now()
  };
}

function seedOutline(reportId: EntityId, baseId: number, type: ReportType): OutlineNode[] {
  const summer = [
    ["检查依据与范围", "明确检查标准、覆盖范围和执行边界。"],
    ["设备运行状态评估", "围绕主设备、辅机系统和高温工况进行分析。"],
    ["风险隐患与整改建议", "识别风险等级、责任部门和整改期限。"],
    ["迎峰度夏保障措施", "形成可执行的保障措施与应急预案。"]
  ];
  const coal = [
    ["审计目标与盘点范围", "明确煤库存审计目标、口径和采样方式。"],
    ["库存数据核验", "核对账面库存、实物盘点和计量偏差。"],
    ["采购消耗与损耗分析", "分析入库、消耗、损耗和异常波动。"],
    ["审计结论与管理建议", "形成结论、风险提示和管理改进建议。"]
  ];
  const items = type === "SUMMER_PEAK_CHECK" ? summer : coal;

  return items.flatMap((item, index) => {
    const chapterId = baseId + index * 10;
    return [
      {
        id: chapterId,
        reportId,
        level: 1,
        sortOrder: index + 1,
        number: `${index + 1}`,
        title: item[0],
        promptHint: item[1]
      },
      {
        id: chapterId + 1,
        reportId,
        parentId: chapterId,
        level: 2,
        sortOrder: 1,
        number: `${index + 1}.1`,
        title: "现状说明",
        promptHint: "基于业务事实进行客观描述，避免空泛表述。"
      },
      {
        id: chapterId + 2,
        reportId,
        parentId: chapterId,
        level: 2,
        sortOrder: 2,
        number: `${index + 1}.2`,
        title: "问题与措施",
        promptHint: "给出问题判断、影响范围和可落地措施。"
      }
    ];
  });
}

function outlineToSections(outline: OutlineNode[]): ReportSection[] {
  return outline.filter((node) => isContentOutlineNode(outline, node)).map((node) => ({
    id: `${node.id}-section`,
    reportId: node.reportId,
    outlineNodeId: node.id,
    number: node.number,
    title: node.title,
    contentMarkdown: "",
    tableJson:
      node.level === 1
        ? {
            columns: ["检查项", "状态", "建议"],
            rows: [
              [node.title, "待生成", "等待 AI 补充"],
              ["资料完整性", "待核验", "生成后由用户复核"]
            ]
          }
        : undefined,
    status: "PENDING",
    source: "AI",
    version: 1,
    createdAt: now(),
    updatedAt: now()
  }));
}

function seedReport(): ReportDetail {
  const reportId = 1;
  const outline = seedOutline(reportId, 100, "SUMMER_PEAK_CHECK");
  const sections = outlineToSections(outline).map((section, index) => ({
    ...section,
    status: (index < 4 ? "GENERATED" : "PENDING") as ReportSection["status"],
    contentMarkdown:
      index < 4
        ? `## ${section.number} ${section.title}\n\n本章节围绕${section.title}开展分析，重点核验设备状态、资料完整性与整改闭环。系统已记录本节生成来源和版本，用户可继续编辑后导出。\n\n| 指标 | 当前状态 | 处理建议 |\n| --- | --- | --- |\n| 数据完整性 | 良好 | 保持复核 |\n| 风险闭环 | 进行中 | 明确责任人 |`
        : ""
  }));

  return {
    id: reportId,
    name: "2026 年迎峰度夏检查报告",
    type: "SUMMER_PEAK_CHECK",
    subject: "华东区域高温负荷保障检查",
    specialty: "电气",
    powerPlant: "示范电厂",
    reportYear: 2026,
    status: "CONTENT_GENERATING",
    ownerId: 1,
    totalSections: sections.length,
    completedSections: sections.filter((section) => section.status === "GENERATED").length,
    createdAt: now(),
    updatedAt: now(),
    outline,
    sections,
    files: []
  };
}

function seedTemplates(): TemplateRecord[] {
  return [
    {
      id: "tpl-summer-default",
      name: "迎峰度夏检查报告模板",
      reportType: "SUMMER_PEAK_CHECK",
      version: "v1.2",
      storageType: "MINIO",
      filePath: "minio://report-templates/templates/SUMMER_PEAK_CHECK/tpl-summer-default.docx",
      bucketName: "report-templates",
      objectName: "templates/SUMMER_PEAK_CHECK/tpl-summer-default.docx",
      originalFileName: "迎峰度夏检查报告模板.docx",
      contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      fileSize: 20480,
      configJson: "{}",
      enabled: true,
      createdBy: "C组管理员",
      createdAt: now(),
      updatedAt: now()
    },
    {
      id: "tpl-coal-audit",
      name: "煤库存审计报告模板",
      reportType: "COAL_INVENTORY_AUDIT",
      version: "v1.0",
      storageType: "MINIO",
      filePath: "minio://report-templates/templates/COAL_INVENTORY_AUDIT/tpl-coal-audit.docx",
      bucketName: "report-templates",
      objectName: "templates/COAL_INVENTORY_AUDIT/tpl-coal-audit.docx",
      originalFileName: "煤库存审计报告模板.docx",
      contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      fileSize: 22528,
      configJson: "{}",
      enabled: true,
      createdBy: "C组管理员",
      createdAt: now(),
      updatedAt: now()
    }
  ];
}

function seedAssets(): AssetRecord[] {
  return [
    {
      id: "asset-standard-001",
      name: "电力行业迎峰度夏检查数据表",
      category: "STANDARD_DOC",
      categoryLabel: "标准文档",
      fileType: "xlsx",
      storageType: "MINIO",
      filePath: "minio://report-assets/assets/STANDARD_DOC/asset-standard-001.xlsx",
      bucketName: "report-assets",
      objectName: "assets/STANDARD_DOC/asset-standard-001.xlsx",
      originalFileName: "电力行业迎峰度夏检查数据表.xlsx",
      contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      fileSize: 102400,
      sha256: "mock-asset-standard-001",
      description: "迎峰度夏检查依据和数据项。",
      tags: "标准,迎峰度夏",
      enabled: true,
      createdBy: "admin",
      createdAt: now(),
      updatedAt: now()
    },
    {
      id: "asset-report-data-001",
      name: "设备运行统计样表",
      category: "REPORT_DATA",
      categoryLabel: "报告数据",
      fileType: "xlsx",
      storageType: "MINIO",
      filePath: "minio://report-assets/assets/REPORT_DATA/asset-report-data-001.xlsx",
      bucketName: "report-assets",
      objectName: "assets/REPORT_DATA/asset-report-data-001.xlsx",
      originalFileName: "设备运行统计样表.xlsx",
      contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      fileSize: 40960,
      sha256: "mock-asset-report-data-001",
      description: "正文生成可参考的运行数据样表。",
      tags: "数据,设备",
      enabled: true,
      createdBy: "admin",
      createdAt: now(),
      updatedAt: now()
    }
  ];
}

function seedLlmConfigs(): LlmConfig[] {
  return [
    {
      id: "llm-main",
      provider: "OPENAI_COMPATIBLE",
      baseUrl: "https://api.example.com/v1",
      apiKeyConfigured: true,
      modelName: "deepseek-chat",
      timeoutSeconds: 120,
      enabled: true
    },
    {
      id: "llm-local",
      provider: "OLLAMA",
      baseUrl: "http://127.0.0.1:11434",
      apiKeyConfigured: false,
      modelName: "qwen2.5:14b",
      timeoutSeconds: 180,
      enabled: false
    }
  ];
}

function initialDb(): MockDb {
  return {
    reports: [seedReport()],
    templates: seedTemplates(),
    assets: seedAssets(),
    llmConfigs: seedLlmConfigs(),
    nextId: 1000
  };
}

function normalizeReportStatus(status: unknown): ReportStatus {
  if (status === "GENERATING" || status === "EXPORTING") return "CONTENT_GENERATING";
  const known: ReportStatus[] = [
    "DRAFT",
    "OUTLINE_READY",
    "CONTENT_GENERATING",
    "CONTENT_INCOMPLETE",
    "CONTENT_READY",
    "EXPORTED",
    "FAILED",
    "DELETED"
  ];
  return known.includes(status as ReportStatus) ? (status as ReportStatus) : "DRAFT";
}

function normalizeReport(report: ReportDetail): ReportDetail {
  return {
    ...report,
    status: normalizeReportStatus(report.status)
  };
}

function normalizeDb(db: Partial<MockDb>): MockDb {
  const initial = initialDb();
  return {
    reports: (db.reports ?? initial.reports).map(normalizeReport),
    templates: db.templates ?? initial.templates,
    assets: db.assets ?? initial.assets,
    llmConfigs: db.llmConfigs ?? initial.llmConfigs,
    nextId: db.nextId ?? initial.nextId
  };
}

function readDb(): MockDb {
  const raw = localStorage.getItem(DB_KEY);
  if (!raw) return initialDb();
  return normalizeDb(JSON.parse(raw) as Partial<MockDb>);
}

export const mockDb = {
  get data() {
    return readDb();
  },
  save(db: MockDb) {
    localStorage.setItem(DB_KEY, JSON.stringify(db));
  },
  reset() {
    localStorage.removeItem(DB_KEY);
  },
  nextId(db: MockDb) {
    db.nextId += 1;
    return db.nextId;
  },
  seedOutline,
  outlineToSections,
  makeFile
};
