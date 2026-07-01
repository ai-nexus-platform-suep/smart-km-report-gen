import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { baseRoutes, createAuthGuard } from "@platform/ui";

const moduleRoutes: RouteRecordRaw[] = [
  {
    path: "/",
    redirect: "/reports"
  },
  {
    path: "/reports",
    name: "ReportList",
    component: () => import("../pages/reports/ReportListPage.vue"),
    meta: { title: "报告记录" }
  },
  {
    path: "/reports/new",
    name: "ReportCreate",
    component: () => import("../pages/reports/NewReportPage.vue"),
    meta: { title: "新建报告" }
  },
  {
    path: "/reports/:id/outline",
    name: "ReportOutline",
    component: () => import("../pages/reports/OutlinePage.vue"),
    meta: { title: "大纲编辑" }
  },
  {
    path: "/reports/:id/workspace",
    name: "ReportWorkspace",
    component: () => import("../pages/reports/WorkspacePage.vue"),
    meta: { title: "正文工作台" }
  },
  {
    path: "/reports/:id/view",
    name: "ReportView",
    component: () => import("../pages/reports/ReportViewPage.vue"),
    meta: { title: "报告正文查看" }
  },
  {
    path: "/reports/:id/export",
    name: "ReportExport",
    component: () => import("../pages/reports/ExportPage.vue"),
    meta: { title: "报告导出" }
  },
  {
    path: "/admin/dashboard",
    name: "AdminDashboard",
    component: () => import("../pages/admin/AdminDashboardPage.vue"),
    meta: { title: "趋势监控", admin: true }
  },
  {
    path: "/admin/templates",
    name: "TemplateAdmin",
    component: () => import("../pages/admin/TemplateAdminPage.vue"),
    meta: { title: "模板管理", admin: true }
  },
  {
    path: "/admin/llm-configs",
    name: "LlmConfig",
    component: () => import("../pages/admin/LlmConfigPage.vue"),
    meta: { title: "模型配置", admin: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes]
});

createAuthGuard(router);

export default router;
