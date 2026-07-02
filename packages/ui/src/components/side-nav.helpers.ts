export interface MenuPathItem {
  path: string
  children?: MenuPathItem[]
}

function collectPaths(items: MenuPathItem[]): string[] {
  return items.flatMap((item) => [
    item.path,
    ...(item.children ? collectPaths(item.children) : []),
  ])
}

function matchesPath(currentPath: string, menuPath: string) {
  if (currentPath === menuPath) return true
  if (menuPath === '/') return false
  return currentPath.startsWith(`${menuPath}/`)
}

export function resolveActiveMenuPath(currentPath: string, items: MenuPathItem[]) {
  return collectPaths(items)
    .filter((path) => matchesPath(currentPath, path))
    .sort((a, b) => b.length - a.length)[0] || currentPath
}
