<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AppLayout } from '@platform/ui'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'
import { Collection, Search, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)

const navItems: NavItem[] = [
  { path: '/knowledge', title: '知识库管理', icon: Collection },
  { path: '/search', title: '知识检索', icon: Search },
  {
    path: '/admin',
    title: '管理后台',
    icon: Setting,
    admin: true,
    children: [
      { path: '/admin/km/dashboard', title: 'KM统计' },
      { path: '/admin/km/embed', title: '嵌入模型' },
      { path: '/admin/km/rerank', title: '重排序' },
      { path: '/admin/km/parser', title: '解析器' },
    ],
  },
]
</script>

<template>
  <AppLayout v-if="showLayout" :nav-items="navItems">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>



<style>
/* ========= 深色模式 Element Plus 变量覆盖 ========= */
[data-theme='dark'] {
  /* 核心填充色 — 输入框、选择框、下拉菜单等的背景 */
  --el-fill-color-blank: var(--bg-container);
  --el-fill-color: var(--bg-hover);
  --el-fill-color-light: var(--bg-hover);
  --el-fill-color-lighter: var(--bg-hover);
  --el-bg-color: var(--bg-container);
  --el-bg-color-overlay: var(--bg-container);
  --el-border-color: var(--border-color);
  --el-border-color-light: var(--border-color);
  --el-border-color-lighter: var(--border-color);
  --el-text-color-primary: var(--text-primary);
  --el-text-color-regular: var(--text-secondary);
  --el-text-color-placeholder: var(--text-tertiary);

  /* 表格 */
  --el-table-bg-color: var(--bg-container);
  --el-table-tr-bg-color: var(--bg-container);
  --el-table-header-bg-color: var(--bg-hover);
  --el-table-row-hover-bg-color: var(--bg-hover);

  /* 卡片 */
  --el-card-bg-color: var(--bg-container);
  --el-card-border-color: var(--border-color);

  /* 输入框 */
  --el-input-bg-color: var(--bg-container);
  --el-input-border-color: var(--border-color);
  --el-input-text-color: var(--text-primary);

  /* 下拉弹出层 */
  --el-select-dropdown-bg-color: var(--bg-container);
  --el-popper-bg-color: var(--bg-container);

  /* 分页 */
  --el-pagination-button-bg-color: var(--bg-container);
  --el-pagination-text-color: var(--text-primary);

  /* 对话框 */
  --el-dialog-bg-color: var(--bg-container);
  --el-drawer-bg-color: var(--bg-container);
  --el-overlay-color-lighter: var(--bg-mask);

  /* 标签 */
  --el-tag-bg-color: var(--bg-hover);
}

/* 直接覆盖组件 class（解决 CSS 变量被覆盖的问题） */
[data-theme='dark'] .el-input__wrapper,
[data-theme='dark'] .el-input__inner,
[data-theme='dark'] .el-select .el-input__wrapper,
[data-theme='dark'] .el-select__tags,
[data-theme='dark'] .el-select-dropdown,
[data-theme='dark'] .el-select-dropdown__list,
[data-theme='dark'] .el-popper,
[data-theme='dark'] .el-pagination button,
[data-theme='dark'] .el-pagination .btn-prev,
[data-theme='dark'] .el-pagination .btn-next,
[data-theme='dark'] .el-pagination .el-pager li {
  background-color: var(--bg-container) !important;
}

[data-theme='dark'] .el-select-dropdown__item.hover,
[data-theme='dark'] .el-select-dropdown__item:hover {
  background-color: var(--bg-hover) !important;
}

[data-theme='dark'] .el-pagination .el-pager li.is-active {
  background-color: var(--color-primary) !important;
}

[data-theme='dark'] .el-collapse-item__header,
[data-theme='dark'] .el-collapse-item__wrap {
  background-color: var(--bg-container);
}





/* Tooltip & Popper 深色模式 */
[data-theme='dark'] .el-popper,
[data-theme='dark'] .el-tooltip__popper,
[data-theme='dark'] .el-table__body-inner .el-tooltip__popper,
[data-theme='dark'] .el-table .el-tooltip__popper {
  background: var(--bg-container) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3) !important;
}
/* 箭头-白色四边形问题：覆盖 popper__arrow 的 border 颜色 */
[data-theme='dark'] .el-popper .popper__arrow,
[data-theme='dark'] .el-tooltip__popper .popper__arrow {
  border-width: 6px !important;
}
[data-theme='dark'] .el-popper[x-placement^=top] .popper__arrow,
[data-theme='dark'] .el-tooltip__popper[x-placement^=top] .popper__arrow {
  border-top-color: var(--border-color) !important;
  border-bottom-color: transparent !important;
}
[data-theme='dark'] .el-popper[x-placement^=top] .popper__arrow::after,
[data-theme='dark'] .el-tooltip__popper[x-placement^=top] .popper__arrow::after {
  border-top-color: var(--bg-container) !important;
  border-bottom-color: transparent !important;
}
[data-theme='dark'] .el-popper[x-placement^=bottom] .popper__arrow,
[data-theme='dark'] .el-tooltip__popper[x-placement^=bottom] .popper__arrow {
  border-bottom-color: var(--border-color) !important;
  border-top-color: transparent !important;
}
[data-theme='dark'] .el-popper[x-placement^=bottom] .popper__arrow::after,
[data-theme='dark'] .el-tooltip__popper[x-placement^=bottom] .popper__arrow::after {
  border-bottom-color: var(--bg-container) !important;
  border-top-color: transparent !important;
}


/* 深色模式额外覆盖 - 搜索页、select下拉、input组 */
[data-theme='dark'] .el-select-dropdown,
[data-theme='dark'] .el-select-dropdown__list {
  background-color: var(--bg-container) !important;
  border-color: var(--border-color) !important;
}
[data-theme='dark'] .el-select-dropdown__item {
  color: var(--text-primary) !important;
}
[data-theme='dark'] .el-select-dropdown__item.hover,
[data-theme='dark'] .el-select-dropdown__item:hover {
  background-color: var(--bg-hover) !important;
}
[data-theme='dark'] .el-input-group__prepend,
[data-theme='dark'] .el-input-group__append {
  background-color: var(--bg-container) !important;
  border-color: var(--border-color) !important;
}
[data-theme='dark'] .search-box {
  background-color: var(--bg-hover) !important;
  border-color: var(--border-color) !important;
}
[data-theme='dark'] .el-empty__description p {
  color: var(--text-secondary) !important;
}

[data-theme='dark'] .kb-hint {
  background: var(--bg-hover) !important;
  color: var(--text-secondary) !important;
}

</style>