<template>
  <div ref="treeRef" class="outline-tree">
    <template v-for="node in visibleNodes" :key="node.id">
      <button
        :data-node-id="String(node.id)"
        class="outline-node"
        :class="{ active: sameId(node.id, selectedId), dragging: sameId(node.id, draggingId) }"
        :style="{ paddingLeft: `${12 + (node.level - 1) * 20}px` }"
        type="button"
        :draggable="draggable"
        @dragstart="handleDragStart(node.id)"
        @dragend="draggingId = undefined"
        @dragover.prevent
        @drop.prevent="handleDrop(node.id)"
        @click="$emit('select', node.id)"
      >
        <span
          v-if="collapsible && hasChildren(node.id)"
          class="collapse-toggle"
          :class="{ collapsed: collapsedSet.has(String(node.id)) }"
          @click.stop="toggleCollapse(node.id)"
        />
        <span v-else class="collapse-spacer" />
        <span class="status-dot" :class="statusClass(node.id)" />
        <span class="node-number">{{ node.number }}</span>
        <span v-if="hasTable(node.id)" class="table-mark">表</span>
        <span class="node-title">{{ node.title }}</span>
      </button>

      <div
        v-for="table in tableItemsFor(node.id)"
        :key="`${node.id}-${table}`"
        class="table-child-row"
        :style="{ paddingLeft: `${44 + (node.level - 1) * 20}px` }"
      >
        <span class="table-icon">▦</span>
        <span>{{ table }}</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import type { EntityId, OutlineNode, ReportSection } from "@/types/domain";

const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);

const props = defineProps<{
  nodes: OutlineNode[];
  sections?: ReportSection[];
  selectedId?: EntityId;
  draggable?: boolean;
  collapsible?: boolean;
  tableNodeIds?: EntityId[];
  tableItems?: Record<string, string[]>;
}>();

const treeRef = ref<HTMLElement>();
const draggingId = ref<EntityId>();
const collapsedSet = ref(new Set<string>());

const emit = defineEmits<{
  select: [id: EntityId];
  move: [payload: { dragId: EntityId; targetId: EntityId }];
  toggle: [id: EntityId, collapsed: boolean];
}>();

const visibleNodes = computed(() =>
  props.nodes.filter((node) => {
    let parentId = node.parentId;
    while (parentId) {
      if (collapsedSet.value.has(String(parentId))) return false;
      parentId = props.nodes.find((item) => sameId(item.id, parentId))?.parentId;
    }
    return true;
  })
);

function statusClass(nodeId: EntityId) {
  const section = props.sections?.find((item) => sameId(item.outlineNodeId, nodeId));
  if (!section) return "blue";
  if (section.status === "GENERATING") return "generating";
  if (section.status === "GENERATED") return "success";
  if (section.status === "USER_EDITED") return "warning";
  if (section.status === "FAILED") return "danger";
  return "";
}

function tableItemsFor(nodeId: EntityId) {
  return props.tableItems?.[String(nodeId)] ?? [];
}

function hasChildren(nodeId: EntityId) {
  return props.nodes.some((item) => sameId(item.parentId, nodeId));
}

function hasTable(nodeId: EntityId) {
  if (tableItemsFor(nodeId).length > 0) return true;
  if (props.tableNodeIds?.some((id) => sameId(id, nodeId))) return true;
  return Boolean(props.sections?.find((item) => sameId(item.outlineNodeId, nodeId) && item.tableJson));
}

function toggleCollapse(nodeId: EntityId) {
  const next = new Set(collapsedSet.value);
  const key = String(nodeId);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  collapsedSet.value = next;
  emit("toggle", nodeId, next.has(key));
}

function handleDragStart(nodeId: EntityId) {
  if (!props.draggable) return;
  draggingId.value = nodeId;
}

function handleDrop(targetId: EntityId) {
  if (!props.draggable || !draggingId.value || sameId(draggingId.value, targetId)) return;
  emit("move", { dragId: draggingId.value, targetId });
  draggingId.value = undefined;
}

watch(
  () => props.selectedId,
  async (selectedId) => {
    await nextTick();
    const activeNode = Array.from(treeRef.value?.querySelectorAll<HTMLButtonElement>(".outline-node") ?? []).find(
      (item) => item.dataset.nodeId === String(selectedId)
    );
    activeNode?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }
);
</script>

<style scoped>
.outline-tree {
  display: grid;
  padding: 8px;
}

.outline-node {
  display: grid;
  grid-template-columns: 16px 8px 46px auto minmax(0, 1fr);
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

.outline-node.dragging {
  opacity: 0.48;
}

.collapse-toggle {
  width: 0;
  height: 0;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
  border-left: 7px solid var(--text-secondary);
  transition: transform 160ms var(--ease-standard);
}

.collapse-toggle:not(.collapsed) {
  transform: rotate(90deg);
}

.collapse-spacer {
  width: 12px;
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

.table-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 5px;
  border: 1px solid rgba(30, 107, 255, 0.22);
  border-radius: 4px;
  color: var(--accent-blue);
  background: rgba(30, 107, 255, 0.06);
  font-size: 12px;
  font-weight: 800;
}

.table-child-row {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-height: 32px;
  color: var(--text-secondary);
  font-size: 14px;
}

.table-icon {
  color: var(--text-secondary);
  font-size: 15px;
}
</style>
