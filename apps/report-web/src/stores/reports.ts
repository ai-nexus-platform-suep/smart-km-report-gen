import { defineStore } from "pinia";
import { computed, ref } from "vue";
import * as reportApi from "@/api/reports";
import type { CreateReportPayload, EntityId, GenerateStreamEvent, OutlineNode, PageResult, Report, ReportDetail, ReportQuery } from "@/types/domain";
import { assertValidDocxBlob, normalizeDocxFileName } from "@/utils/docx";

const defaultQuery: ReportQuery = {
  page: 1,
  pageSize: 8,
  keyword: "",
  specialty: null,
  type: null,
  status: null,
  year: null
};

const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);

export const useReportStore = defineStore("reports", () => {
  const list = ref<PageResult<Report>>({ items: [], page: 1, pageSize: 8, total: 0 });
  const query = ref<ReportQuery>({ ...defaultQuery });
  const current = ref<ReportDetail>();
  const loading = ref(false);
  const streaming = ref(false);
  const streamMessage = ref("未连接");
  const lastEvent = ref<GenerateStreamEvent>();
  let streamController: { close: () => void } | undefined;

  const progressPercent = computed(() => {
    if (!current.value?.totalSections) return 0;
    return Math.round((current.value.completedSections / current.value.totalSections) * 100);
  });

  async function fetchList(nextQuery?: Partial<ReportQuery>) {
    query.value = { ...query.value, ...nextQuery };
    loading.value = true;
    try {
      list.value = await reportApi.listReports(query.value);
    } finally {
      loading.value = false;
    }
  }

  async function create(payload: CreateReportPayload) {
    const report = await reportApi.createReport(payload);
    await fetchList();
    return report;
  }

  async function fetchDetail(id: EntityId) {
    loading.value = true;
    try {
      current.value = await reportApi.getReport(id);
      return current.value;
    } finally {
      loading.value = false;
    }
  }

  async function remove(id: EntityId) {
    await reportApi.deleteReport(id);
    await fetchList();
  }

  async function generateOutline(id: EntityId) {
    loading.value = true;
    try {
      current.value = await reportApi.generateOutline(id);
      return current.value;
    } finally {
      loading.value = false;
    }
  }

  async function saveOutline(id: EntityId, outline: OutlineNode[]) {
    current.value = await reportApi.saveOutline(id, outline);
    return current.value;
  }

  async function startGenerate(id: EntityId) {
    stopStream();
    await reportApi.startGenerate(id);
    if (!current.value || !sameId(current.value.id, id)) await fetchDetail(id);
    if (current.value) current.value.status = "CONTENT_GENERATING";
    streaming.value = true;
    streamMessage.value = "SSE CONNECTED";
    streamController = reportApi.createGenerateStream(
      id,
      (event) => {
        lastEvent.value = event;
        if (current.value) reportApi.applyStreamEvent(current.value, event);
        if (event.type === "section_started") streamMessage.value = `AI WRITING / ${event.number}`;
        if (event.type === "task_completed") streamMessage.value = "GENERATION COMPLETE";
        if (event.type === "error") streamMessage.value = event.message;
      },
      () => {
        streaming.value = false;
      }
    );
  }

  function stopStream() {
    streamController?.close();
    streamController = undefined;
    streaming.value = false;
  }

  async function saveSection(reportId: EntityId, sectionId: EntityId, content: string) {
    const section = await reportApi.saveSection(reportId, sectionId, content);
    if (current.value) {
      const index = current.value.sections.findIndex((item) => sameId(item.id, sectionId));
      if (index >= 0) current.value.sections[index] = section;
    }
  }

  async function regenerateSection(reportId: EntityId, sectionId: EntityId) {
    const section = await reportApi.regenerateSection(reportId, sectionId);
    if (current.value) {
      const index = current.value.sections.findIndex((item) => sameId(item.id, sectionId));
      if (index >= 0) current.value.sections[index] = section;
    }
  }

  async function exportDocx(reportId: EntityId) {
    const file = await reportApi.exportDocx(reportId);
    if (current.value && sameId(current.value.id, reportId)) {
      current.value.status = "EXPORTED";
      current.value.files = [file, ...current.value.files.filter((item) => !sameId(item.id, file.id))];
      current.value.updatedAt = new Date().toISOString();
    }
    return file;
  }

  async function downloadFile(reportId: EntityId, fileId: EntityId, fileName: string) {
    const blob = await reportApi.downloadFile(reportId, fileId);
    await assertValidDocxBlob(blob);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = normalizeDocxFileName(fileName);
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return {
    list,
    query,
    current,
    loading,
    streaming,
    streamMessage,
    lastEvent,
    progressPercent,
    fetchList,
    create,
    fetchDetail,
    remove,
    generateOutline,
    saveOutline,
    startGenerate,
    stopStream,
    saveSection,
    regenerateSection,
    exportDocx,
    downloadFile
  };
});
