import { http, HttpResponse, delay } from 'msw'
import { API_REPORT } from '@platform/core'
import type { ReportRecord, ReportTemplate, Material, OutlineSection } from '@platform/core/types'

const now = new Date().toISOString()

const mockReports: ReportRecord[] = [
  {
    id: 1,
    name: '2025年迎峰度夏检查报告',
    type: 'SUMMER_CHECK',
    profession: '汽机专业',
    plantName: '华能某电厂',
    year: 2025,
    status: 'COMPLETED',
    createdAt: now,
  },
  {
    id: 2,
    name: '2025年第一季度煤库存审计报告',
    type: 'COAL_AUDIT',
    profession: '燃料专业',
    plantName: '大唐某电厂',
    year: 2025,
    status: 'GENERATING',
    createdAt: now,
  },
  {
    id: 3,
    name: '2024年迎峰度夏检查报告',
    type: 'SUMMER_CHECK',
    profession: '锅炉专业',
    plantName: '华能某电厂',
    year: 2024,
    status: 'EXPORTED',
    createdAt: now,
  },
]

const mockOutline: OutlineSection[] = [
  { id: '1', title: '概述', level: 1, order: 1, content: '本报告针对2025年迎峰度夏期间...' },
  {
    id: '2', title: '检查内容', level: 1, order: 2, children: [
      { id: '2-1', title: '汽轮机本体检查', level: 2, order: 1, content: '对汽轮机高、中、低压缸进行了全面检查...' },
      { id: '2-2', title: '辅助系统检查', level: 2, order: 2, content: '包括循环水系统、凝结水系统、给水系统等...' },
      { id: '2-3', title: '电气设备检查', level: 2, order: 3 },
    ],
  },
  { id: '3', title: '检查结果与建议', level: 1, order: 3 },
  { id: '4', title: '附录', level: 1, order: 4 },
]

const mockTemplates: ReportTemplate[] = [
  { id: 1, name: '迎峰度夏标准模板', type: 'SUMMER_CHECK', description: '适用于夏季用电高峰期设备检查报告', createdAt: now },
  { id: 2, name: '煤库存审计模板', type: 'COAL_AUDIT', description: '适用于燃煤电厂库存审计报告', createdAt: now },
]

const mockMaterials: Material[] = [
  { id: 1, name: '汽轮机检修记录_2024.pdf', fileType: 'pdf', tags: ['汽轮机', '检修'], uploadedAt: now },
  { id: 2, name: '锅炉运行数据_2024.xlsx', fileType: 'xlsx', tags: ['锅炉', '运行数据'], uploadedAt: now },
  { id: 3, name: '电气设备台账_2024.docx', fileType: 'docx', tags: ['电气', '台账'], uploadedAt: now },
]

export const reportHandlers = [
  // 报告列表
  http.get(API_REPORT.REPORT.LIST, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: mockReports, total: mockReports.length, page: 1, pageSize: 10 },
    })
  }),

  // 创建报告
  http.post(API_REPORT.REPORT.CREATE, async () => {
    await delay(600)
    return HttpResponse.json({
      code: 200, message: '创建成功',
      data: { id: Date.now(), name: '新报告', type: 'SUMMER_CHECK', profession: '', plantName: '', year: 2025, status: 'DRAFT', createdAt: new Date().toISOString() },
    })
  }),

  // 报告大纲
  http.get(API_REPORT.REPORT.OUTLINE, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: 'ok', data: mockOutline })
  }),

  // 生成报告内容
  http.post(API_REPORT.REPORT.GENERATE, async () => {
    await delay(800)
    return HttpResponse.json({ code: 200, message: '生成成功', data: null })
  }),

  // 导出 DOCX
  http.post(API_REPORT.REPORT.EXPORT, async () => {
    await delay(1000)
    return HttpResponse.json({ code: 200, message: '导出成功', data: { downloadUrl: '/mock/report.docx' } })
  }),

  // 删除报告
  http.delete(API_REPORT.REPORT.DELETE, async () => {
    await delay(400)
    return HttpResponse.json({ code: 200, message: '删除成功', data: null })
  }),

  // 模板列表
  http.get(API_REPORT.TEMPLATE.LIST, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: mockTemplates, total: mockTemplates.length, page: 1, pageSize: 10 },
    })
  }),

  // 上传模板
  http.post(API_REPORT.TEMPLATE.UPLOAD, async () => {
    await delay(600)
    return HttpResponse.json({ code: 200, message: '上传成功', data: null })
  }),

  // 删除模板
  http.delete(API_REPORT.TEMPLATE.DELETE, async () => {
    await delay(400)
    return HttpResponse.json({ code: 200, message: '删除成功', data: null })
  }),

  // 素材列表
  http.get(API_REPORT.MATERIAL.LIST, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: mockMaterials, total: mockMaterials.length, page: 1, pageSize: 10 },
    })
  }),

  // 上传素材
  http.post(API_REPORT.MATERIAL.UPLOAD, async () => {
    await delay(600)
    return HttpResponse.json({ code: 200, message: '上传成功', data: null })
  }),

  // 删除素材
  http.delete(API_REPORT.MATERIAL.DELETE, async () => {
    await delay(400)
    return HttpResponse.json({ code: 200, message: '删除成功', data: null })
  }),

  // Admin: LLM 配置
  http.get(API_REPORT.ADMIN.LLM_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { apiBase: 'https://api.openai.com/v1', apiKey: 'sk-****', modelName: 'gpt-4', timeout: 60000 },
    })
  }),

  http.put(API_REPORT.ADMIN.LLM_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '保存成功', data: null })
  }),

  // Admin: 统计数据
  http.get(API_REPORT.ADMIN.STATS, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { templateCount: 2, reportCount: 3 },
    })
  }),

  // Admin: 趋势数据
  http.get(API_REPORT.ADMIN.TREND, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: Array.from({ length: 30 }, (_, i) => ({ date: `2025-06-${String(i + 1).padStart(2, '0')}`, count: Math.floor(Math.random() * 5) })),
    })
  }),
]
