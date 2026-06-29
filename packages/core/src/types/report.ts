export type ReportStatus = 'DRAFT' | 'OUTLINE_READY' | 'GENERATING' | 'COMPLETED' | 'EXPORTED'

export type TemplateType = 'SUMMER_CHECK' | 'COAL_AUDIT'

export interface ReportRecord {
  id: number
  name: string
  type: TemplateType
  profession: string
  plantName: string
  year: number
  status: ReportStatus
  createdAt: string
}

export interface OutlineSection {
  id: string
  title: string
  level: number
  order: number
  content?: string
  children?: OutlineSection[]
}

export interface ReportTemplate {
  id: number
  name: string
  type: TemplateType
  description: string
  createdAt: string
}

export interface Material {
  id: number
  name: string
  fileType: string
  tags: string[]
  uploadedAt: string
}

export interface ReportSseEvent {
  type: 'outline' | 'section' | 'progress' | 'done' | 'error'
  sectionId?: string
  content?: string
  progress?: { done: number; total: number }
}
