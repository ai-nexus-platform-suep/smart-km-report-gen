import type { EntityId, OutlineNode } from "@/types/domain";

const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b);
const parentKey = (id?: EntityId) => (id === undefined ? "ROOT" : String(id));

export function renumberOutline(nodes: OutlineNode[]) {
  const sorted = [...nodes].sort((a, b) => a.level - b.level || a.sortOrder - b.sortOrder);
  const byParent = new Map<string, OutlineNode[]>();

  for (const node of sorted) {
    const key = parentKey(node.parentId);
    const list = byParent.get(key) ?? [];
    list.push(node);
    byParent.set(key, list);
  }

  const result: OutlineNode[] = [];
  const walk = (parentId: EntityId | undefined, prefix: number[] = []) => {
    const children = byParent.get(parentKey(parentId)) ?? [];
    children
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .forEach((child, index) => {
        const number = [...prefix, index + 1].join(".");
        const next = { ...child, number, sortOrder: index + 1 };
        result.push(next);
        walk(child.id, [...prefix, index + 1]);
      });
  };

  walk(undefined);
  return result;
}

export function moveOutlineNode(nodes: OutlineNode[], nodeId: EntityId, direction: -1 | 1) {
  const target = nodes.find((node) => sameId(node.id, nodeId));
  if (!target) return nodes;

  const siblings = nodes
    .filter((node) => sameId(node.parentId, target.parentId))
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const index = siblings.findIndex((node) => sameId(node.id, nodeId));
  const swapIndex = index + direction;

  if (swapIndex < 0 || swapIndex >= siblings.length) return nodes;

  const first = siblings[index];
  const second = siblings[swapIndex];

  return renumberOutline(
    nodes.map((node) => {
      if (sameId(node.id, first.id)) return { ...node, sortOrder: second.sortOrder };
      if (sameId(node.id, second.id)) return { ...node, sortOrder: first.sortOrder };
      return node;
    })
  );
}

export function hasChildNodes(nodes: OutlineNode[], nodeId: EntityId) {
  return nodes.some((node) => sameId(node.parentId, nodeId));
}

export function isContentOutlineNode(nodes: OutlineNode[], node: OutlineNode) {
  return node.level > 1 && !hasChildNodes(nodes, node.id);
}

export function contentOutlineNodes(nodes: OutlineNode[]) {
  return nodes.filter((node) => isContentOutlineNode(nodes, node));
}

export function countContentOutlineNodes(nodes: OutlineNode[]) {
  return contentOutlineNodes(nodes).length;
}

export function moveOutlineNodeToTarget(nodes: OutlineNode[], dragId: EntityId, targetId: EntityId) {
  if (sameId(dragId, targetId)) return nodes;

  const dragNode = nodes.find((node) => sameId(node.id, dragId));
  const targetNode = nodes.find((node) => sameId(node.id, targetId));
  if (!dragNode || !targetNode) return nodes;

  const descendantIds = new Set<string>();
  let changed = true;
  while (changed) {
    changed = false;
    nodes.forEach((node) => {
      const isDirectChild = sameId(node.parentId, dragId);
      const isNestedChild = node.parentId && descendantIds.has(String(node.parentId));
      if ((isDirectChild || isNestedChild) && !descendantIds.has(String(node.id))) {
        descendantIds.add(String(node.id));
        changed = true;
      }
    });
  }
  if (descendantIds.has(String(targetId))) return nodes;

  const levelDelta = targetNode.level - dragNode.level;

  return renumberOutline(
    nodes.map((node) => {
      if (sameId(node.id, dragId)) {
        return {
          ...node,
          parentId: targetNode.parentId,
          level: targetNode.level,
          sortOrder: targetNode.sortOrder - 0.5
        };
      }

      if (descendantIds.has(String(node.id))) {
        return {
          ...node,
          level: Math.max(1, node.level + levelDelta)
        };
      }

      if (sameId(node.parentId, targetNode.parentId) && node.sortOrder >= targetNode.sortOrder) {
        return {
          ...node,
          sortOrder: node.sortOrder + 1
        };
      }

      return node;
    })
  );
}
