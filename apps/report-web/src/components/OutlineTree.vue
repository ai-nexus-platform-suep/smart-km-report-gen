<template>
  <div ref="treeRef" class="outline-tree">
    <button
      v-for="node in nodes"
      :key="node.id"
      class="outline-node"
      :class="{ active: sameId(node.id, selectedId) }"
      :style="{ paddingLeft: `${12 + (node.level - 1) * 20}px` }"
      type="button"
      @click="$emit('select', node.id)"
    >
      <span class="status-dot" :class="statusClass(node.id)" />
      <span class="node-number">{{ node.number }}</span>
      <span class="node-title">{{ node.title }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { EntityId, OutlineNode, ReportSection } from "@/types/domain";

const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);

const props = defineProps<{
  nodes: OutlineNode[];
  sections?: ReportSection[];
  selectedId?: EntityId;
}>();

defineEmits<{
  select: [id: EntityId];
}>();

function statusClass(nodeId: EntityId) {
  const section = props.sections?.find((item) => sameId(item.outlineNodeId, nodeId));
  if (!section) return "blue";
  if (section.status === "GENERATING") return "generating";
  if (section.status === "GENERATED") return "success";
  if (section.status === "USER_EDITED") return "warning";
  if (section.status === "FAILED") return "danger";
  return "";
}
</script>

<style scoped>
.outline-tree {
  display: grid;
  padding: 8px;
}

.outline-node {
  display: grid;
  grid-template-columns: 8px 42px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-height: 38px;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0;
  color: var(--text-primary);
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 160ms var(--ease-standard),
    background 160ms var(--ease-standard);
}

.outline-node:hover {
  background: var(--accent-blue-soft);
}

.outline-node.active {
  border-left-color: var(--accent-blue);
  background: var(--accent-blue-soft);
}

.node-number {
  color: var(--accent-blue);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
}

.node-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
