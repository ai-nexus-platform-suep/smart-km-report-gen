export interface DocxPreviewBlock {
  type: "paragraph" | "table";
  text?: string;
  rows?: string[][];
}

export interface DocxPreviewSection {
  name: string;
  blocks: DocxPreviewBlock[];
}

export interface DocxPreview {
  sections: DocxPreviewSection[];
}

const decoder = new TextDecoder("utf-8");

export async function readDocxPreview(blob: Blob): Promise<DocxPreview> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  const files = await readZipFiles(bytes);
  const parts = [
    { path: "word/document.xml", name: "正文" },
    ...Array.from(files.keys())
      .filter((path) => /^word\/header\d+\.xml$/i.test(path))
      .sort()
      .map((path) => ({ path, name: `页眉 ${path.match(/\d+/)?.[0] || ""}`.trim() })),
    ...Array.from(files.keys())
      .filter((path) => /^word\/footer\d+\.xml$/i.test(path))
      .sort()
      .map((path) => ({ path, name: `页脚 ${path.match(/\d+/)?.[0] || ""}`.trim() })),
    ...["word/footnotes.xml", "word/endnotes.xml"]
      .filter((path) => files.has(path))
      .map((path) => ({ path, name: path.includes("footnotes") ? "脚注" : "尾注" }))
  ];

  const sections = parts
    .map((part) => ({
      name: part.name,
      blocks: extractBlocks(files.get(part.path) || "")
    }))
    .filter((section) => section.blocks.length > 0);

  return { sections };
}

async function readZipFiles(bytes: Uint8Array) {
  const entries = parseCentralDirectory(bytes);
  const result = new Map<string, string>();

  for (const entry of entries) {
    if (!/\.xml$/i.test(entry.name)) continue;
    const data = bytes.slice(entry.dataStart, entry.dataStart + entry.compressedSize);
    const content = entry.method === 0 ? data : await inflateRaw(data, entry.name);
    result.set(entry.name, decoder.decode(content));
  }

  return result;
}

function parseCentralDirectory(bytes: Uint8Array) {
  const eocdOffset = findEndOfCentralDirectory(bytes);
  if (eocdOffset < 0) throw new Error("无法读取 DOCX 包目录");

  const entryCount = readUint16(bytes, eocdOffset + 10);
  const centralOffset = readUint32(bytes, eocdOffset + 16);
  const entries: Array<{
    name: string;
    method: number;
    compressedSize: number;
    localHeaderOffset: number;
    dataStart: number;
  }> = [];
  let offset = centralOffset;

  for (let index = 0; index < entryCount; index += 1) {
    if (readUint32(bytes, offset) !== 0x02014b50) break;

    const method = readUint16(bytes, offset + 10);
    const compressedSize = readUint32(bytes, offset + 20);
    const fileNameLength = readUint16(bytes, offset + 28);
    const extraLength = readUint16(bytes, offset + 30);
    const commentLength = readUint16(bytes, offset + 32);
    const localHeaderOffset = readUint32(bytes, offset + 42);
    const name = decoder.decode(bytes.slice(offset + 46, offset + 46 + fileNameLength));

    const localNameLength = readUint16(bytes, localHeaderOffset + 26);
    const localExtraLength = readUint16(bytes, localHeaderOffset + 28);
    entries.push({
      name,
      method,
      compressedSize,
      localHeaderOffset,
      dataStart: localHeaderOffset + 30 + localNameLength + localExtraLength
    });

    offset += 46 + fileNameLength + extraLength + commentLength;
  }

  return entries;
}

function findEndOfCentralDirectory(bytes: Uint8Array) {
  for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 66000); offset -= 1) {
    if (readUint32(bytes, offset) === 0x06054b50) return offset;
  }
  return -1;
}

async function inflateRaw(data: Uint8Array, name: string) {
  const Decompression = (globalThis as unknown as { DecompressionStream?: new (format: string) => TransformStream }).DecompressionStream;
  if (!Decompression) throw new Error("当前浏览器不支持 DOCX 压缩内容预览");

  try {
    const stream = new Blob([data]).stream().pipeThrough(new Decompression("deflate-raw"));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  } catch {
    throw new Error(`无法解压模板内容：${name}`);
  }
}

function extractBlocks(xml: string): DocxPreviewBlock[] {
  if (!xml.trim()) return [];
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  const root = doc.documentElement;
  if (!root || root.querySelector("parsererror")) return [];
  return children(root)
    .flatMap((node) => extractNodeBlocks(node))
    .filter((block) => block.type === "table" || Boolean(block.text?.trim()));
}

function extractNodeBlocks(node: Element): DocxPreviewBlock[] {
  if (node.localName === "p") {
    const text = extractText(node).replace(/\s+\n/g, "\n").trim();
    return text ? [{ type: "paragraph", text }] : [];
  }

  if (node.localName === "tbl") {
    const rows = children(node)
      .filter((child) => child.localName === "tr")
      .map((row) =>
        children(row)
          .filter((cell) => cell.localName === "tc")
          .map((cell) => extractText(cell).replace(/\s+/g, " ").trim())
      )
      .filter((row) => row.some(Boolean));
    return rows.length ? [{ type: "table", rows }] : [];
  }

  return children(node).flatMap((child) => extractNodeBlocks(child));
}

function extractText(node: Element): string {
  if (node.localName === "t") return node.textContent || "";
  if (node.localName === "tab") return "\t";
  if (node.localName === "br" || node.localName === "cr") return "\n";
  return children(node).map((child) => extractText(child)).join("");
}

function children(node: Element) {
  return Array.from(node.childNodes).filter((child): child is Element => child.nodeType === Node.ELEMENT_NODE);
}

function readUint16(bytes: Uint8Array, offset: number) {
  return bytes[offset] | (bytes[offset + 1] << 8);
}

function readUint32(bytes: Uint8Array, offset: number) {
  return (bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] << 24)) >>> 0;
}
