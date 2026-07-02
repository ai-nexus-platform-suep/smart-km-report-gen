export interface NavigationLocation {
  path: string
  query?: Record<string, string>
}

function isPlatformKmPath(currentPath: string) {
  return currentPath === '/km' || currentPath.startsWith('/km/')
}

function isStandaloneDocumentPath(currentPath: string) {
  return currentPath === '/documents' || currentPath.startsWith('/documents/')
}

export function getKnowledgeBaseListPath(currentPath: string) {
  if (isStandaloneDocumentPath(currentPath)) return '/documents'
  return isPlatformKmPath(currentPath) ? '/km/bases' : '/knowledge'
}

export function getKnowledgeBaseCreatePath(currentPath: string) {
  return isPlatformKmPath(currentPath) ? '/km/bases/create' : '/knowledge/create'
}

export function getKnowledgeBaseEditPath(currentPath: string, id: string | number) {
  return isPlatformKmPath(currentPath) ? `/km/bases/${id}` : `/knowledge/${id}`
}

export function buildDocumentListLocation(
  currentPath: string,
  kbId: string | number,
  kbName?: string,
): NavigationLocation {
  const location: NavigationLocation = {
    path: isPlatformKmPath(currentPath)
      ? `/km/documents/${kbId}`
      : isStandaloneDocumentPath(currentPath)
        ? `/documents/${kbId}`
        : `/knowledge/${kbId}/documents`,
  }

  if (kbName) {
    location.query = { name: kbName }
  }

  return location
}
