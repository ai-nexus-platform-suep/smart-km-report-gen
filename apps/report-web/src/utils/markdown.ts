const dangerousPattern = /<script|javascript:|onerror=|onload=/i;

export function sanitizeMarkdown(markdown: string) {
  if (dangerousPattern.test(markdown)) {
    return markdown.replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }
  return markdown;
}
