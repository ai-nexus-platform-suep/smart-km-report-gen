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
