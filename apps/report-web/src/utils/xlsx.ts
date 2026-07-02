export interface ExcelSheetPreview {
  name: string;
  rows: string[][];
}

export interface ExcelPreview {
  sheets: ExcelSheetPreview[];
}

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder("utf-8");

export async function readXlsxPreview(blob: Blob): Promise<ExcelPreview> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  const files = await readZipXmlFiles(bytes);
  const sharedStrings = parseSharedStrings(files.get("xl/sharedStrings.xml") || "");
  const sheetMap = parseWorkbookSheets(files.get("xl/workbook.xml") || "", files.get("xl/_rels/workbook.xml.rels") || "");
  const sheets = sheetMap
    .map((sheet) => ({
      name: sheet.name,
      rows: parseSheetRows(files.get(`xl/${sheet.target}`) || "", sharedStrings)
    }))
    .filter((sheet) => sheet.rows.length > 0);

  return { sheets };
}

export async function assertValidXlsxBlob(blob: Blob) {
  const signature = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
  if (signature[0] === 0x50 && signature[1] === 0x4b) return;
  const preview = await blob.slice(0, 120).text().catch(() => "");
  throw new Error(preview ? `下载内容不是有效 Excel：${preview}` : "下载内容不是有效 Excel 文件。");
}

export function createSimpleXlsxBlob(fileName: string, rows: string[][]) {
  const safeRows = rows.length ? rows : [["素材名称", fileName], ["说明", "Mock Excel 文件"]];
  const sheetXml = worksheetXml(safeRows);
  const zip = createZipPackage({
    "[Content_Types].xml": contentTypesXml(),
    "_rels/.rels": rootRelsXml(),
    "xl/workbook.xml": workbookXml(),
    "xl/_rels/workbook.xml.rels": workbookRelsXml(),
    "xl/worksheets/sheet1.xml": sheetXml
  });
  return new Blob([zip], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
}

function parseWorkbookSheets(workbookXml: string, relsXml: string) {
  const workbook = parseXml(workbookXml);
  const rels = parseXml(relsXml);
  if (!workbook || !rels) return [];

  const relMap = new Map<string, string>();
  Array.from(rels.getElementsByTagNameNS("*", "Relationship")).forEach((rel) => {
    const id = rel.getAttribute("Id");
    const target = rel.getAttribute("Target");
    if (id && target) relMap.set(id, target.replace(/^\/?xl\//, ""));
  });

  return Array.from(workbook.getElementsByTagNameNS("*", "sheet"))
    .map((sheet, index) => {
      const name = sheet.getAttribute("name") || `Sheet${index + 1}`;
      const relationshipId = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id") || sheet.getAttribute("r:id") || "";
      return {
        name,
        target: relMap.get(relationshipId) || `worksheets/sheet${index + 1}.xml`
      };
    });
}

function parseSharedStrings(xml: string) {
  const doc = parseXml(xml);
  if (!doc) return [];
  return Array.from(doc.getElementsByTagNameNS("*", "si")).map((item) =>
    Array.from(item.getElementsByTagNameNS("*", "t")).map((node) => node.textContent || "").join("")
  );
}

function parseSheetRows(xml: string, sharedStrings: string[]) {
  const doc = parseXml(xml);
  if (!doc) return [];

  return Array.from(doc.getElementsByTagNameNS("*", "row"))
    .map((row) => {
      const values: string[] = [];
      Array.from(row.getElementsByTagNameNS("*", "c")).forEach((cell) => {
        const ref = cell.getAttribute("r") || "";
        const index = columnIndex(ref.replace(/\d+/g, ""));
        values[index] = cellValue(cell, sharedStrings);
      });
      return trimTrailing(values);
    })
    .filter((row) => row.some((cell) => cell.trim()));
}

function cellValue(cell: Element, sharedStrings: string[]) {
  const type = cell.getAttribute("t");
  if (type === "inlineStr") return Array.from(cell.getElementsByTagNameNS("*", "t")).map((node) => node.textContent || "").join("");
  const raw = Array.from(cell.getElementsByTagNameNS("*", "v"))[0]?.textContent || "";
  if (type === "s") return sharedStrings[Number(raw)] || "";
  return raw;
}

function columnIndex(column: string) {
  return column
    .toUpperCase()
    .split("")
    .reduce((sum, char) => sum * 26 + char.charCodeAt(0) - 64, 0) - 1;
}

function trimTrailing(values: string[]) {
  const next = [...values].map((value) => value || "");
  while (next.length && !next[next.length - 1]) next.pop();
  return next;
}

async function readZipXmlFiles(bytes: Uint8Array) {
  const entries = parseCentralDirectory(bytes);
  const result = new Map<string, string>();
  for (const entry of entries) {
    if (!/\.xml$/i.test(entry.name) && !/\.rels$/i.test(entry.name)) continue;
    const data = bytes.slice(entry.dataStart, entry.dataStart + entry.compressedSize);
    const content = entry.method === 0 ? data : await inflateRaw(data, entry.name);
    result.set(entry.name, textDecoder.decode(content));
  }
  return result;
}

function parseCentralDirectory(bytes: Uint8Array) {
  const eocdOffset = findEndOfCentralDirectory(bytes);
  if (eocdOffset < 0) throw new Error("无法读取 Excel 文件目录");
  const entryCount = readUint16(bytes, eocdOffset + 10);
  let offset = readUint32(bytes, eocdOffset + 16);
  const entries: Array<{ name: string; method: number; compressedSize: number; dataStart: number }> = [];

  for (let index = 0; index < entryCount; index += 1) {
    if (readUint32(bytes, offset) !== 0x02014b50) break;
    const method = readUint16(bytes, offset + 10);
    const compressedSize = readUint32(bytes, offset + 20);
    const fileNameLength = readUint16(bytes, offset + 28);
    const extraLength = readUint16(bytes, offset + 30);
    const commentLength = readUint16(bytes, offset + 32);
    const localHeaderOffset = readUint32(bytes, offset + 42);
    const name = textDecoder.decode(bytes.slice(offset + 46, offset + 46 + fileNameLength));
    const localNameLength = readUint16(bytes, localHeaderOffset + 26);
    const localExtraLength = readUint16(bytes, localHeaderOffset + 28);
    entries.push({
      name,
      method,
      compressedSize,
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
  if (!Decompression) throw new Error("当前浏览器不支持 Excel 压缩内容预览");
  try {
    const stream = new Blob([data]).stream().pipeThrough(new Decompression("deflate-raw"));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  } catch {
    throw new Error(`无法解压 Excel 内容：${name}`);
  }
}

function parseXml(xml: string) {
  if (!xml.trim()) return undefined;
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  return doc.documentElement.querySelector("parsererror") ? undefined : doc;
}

function worksheetXml(rows: string[][]) {
  const rowXml = rows
    .map(
      (row, rowIndex) =>
        `<row r="${rowIndex + 1}">${row
          .map((cell, cellIndex) => {
            const ref = `${columnName(cellIndex)}${rowIndex + 1}`;
            return `<c r="${ref}" t="inlineStr"><is><t>${escapeXml(cell)}</t></is></c>`;
          })
          .join("")}</row>`
    )
    .join("");
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${rowXml}</sheetData></worksheet>`;
}

function columnName(index: number) {
  let value = index + 1;
  let name = "";
  while (value > 0) {
    const mod = (value - 1) % 26;
    name = String.fromCharCode(65 + mod) + name;
    value = Math.floor((value - mod) / 26);
  }
  return name;
}

function contentTypesXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>`;
}

function rootRelsXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`;
}

function workbookXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="素材明细" sheetId="1" r:id="rId1"/></sheets></workbook>`;
}

function workbookRelsXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`;
}

function createZipPackage(files: Record<string, string>) {
  const entries = Object.entries(files).map(([name, content]) => ({ name, data: textEncoder.encode(content) }));
  const localParts: Uint8Array[] = [];
  const centralParts: Uint8Array[] = [];
  let offset = 0;

  entries.forEach((entry) => {
    const nameBytes = textEncoder.encode(entry.name);
    const checksum = crc32(entry.data);
    const localHeader = new Uint8Array(30 + nameBytes.length);
    writeUint32(localHeader, 0, 0x04034b50);
    writeUint16(localHeader, 4, 20);
    writeUint16(localHeader, 6, 0);
    writeUint16(localHeader, 8, 0);
    writeUint16(localHeader, 10, 0);
    writeUint16(localHeader, 12, 0);
    writeUint32(localHeader, 14, checksum);
    writeUint32(localHeader, 18, entry.data.length);
    writeUint32(localHeader, 22, entry.data.length);
    writeUint16(localHeader, 26, nameBytes.length);
    writeUint16(localHeader, 28, 0);
    localHeader.set(nameBytes, 30);
    localParts.push(localHeader, entry.data);

    const centralHeader = new Uint8Array(46 + nameBytes.length);
    writeUint32(centralHeader, 0, 0x02014b50);
    writeUint16(centralHeader, 4, 20);
    writeUint16(centralHeader, 6, 20);
    writeUint16(centralHeader, 8, 0);
    writeUint16(centralHeader, 10, 0);
    writeUint16(centralHeader, 12, 0);
    writeUint16(centralHeader, 14, 0);
    writeUint32(centralHeader, 16, checksum);
    writeUint32(centralHeader, 20, entry.data.length);
    writeUint32(centralHeader, 24, entry.data.length);
    writeUint16(centralHeader, 28, nameBytes.length);
    writeUint16(centralHeader, 30, 0);
    writeUint16(centralHeader, 32, 0);
    writeUint16(centralHeader, 34, 0);
    writeUint16(centralHeader, 36, 0);
    writeUint32(centralHeader, 38, 0);
    writeUint32(centralHeader, 42, offset);
    centralHeader.set(nameBytes, 46);
    centralParts.push(centralHeader);
    offset += localHeader.length + entry.data.length;
  });

  const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0);
  const endHeader = new Uint8Array(22);
  writeUint32(endHeader, 0, 0x06054b50);
  writeUint16(endHeader, 4, 0);
  writeUint16(endHeader, 6, 0);
  writeUint16(endHeader, 8, entries.length);
  writeUint16(endHeader, 10, entries.length);
  writeUint32(endHeader, 12, centralSize);
  writeUint32(endHeader, 16, offset);
  writeUint16(endHeader, 20, 0);
  return concatUint8Arrays([...localParts, ...centralParts, endHeader]);
}

function concatUint8Arrays(parts: Uint8Array[]) {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const output = new Uint8Array(total);
  let offset = 0;
  parts.forEach((part) => {
    output.set(part, offset);
    offset += part.length;
  });
  return output;
}

function escapeXml(value: string) {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function readUint16(bytes: Uint8Array, offset: number) {
  return bytes[offset] | (bytes[offset + 1] << 8);
}

function readUint32(bytes: Uint8Array, offset: number) {
  return (bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] << 24)) >>> 0;
}

function writeUint16(target: Uint8Array, offset: number, value: number) {
  target[offset] = value & 0xff;
  target[offset + 1] = (value >>> 8) & 0xff;
}

function writeUint32(target: Uint8Array, offset: number, value: number) {
  target[offset] = value & 0xff;
  target[offset + 1] = (value >>> 8) & 0xff;
  target[offset + 2] = (value >>> 16) & 0xff;
  target[offset + 3] = (value >>> 24) & 0xff;
}

const crcTable = Array.from({ length: 256 }, (_, index) => {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) {
    value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  }
  return value >>> 0;
});

function crc32(data: Uint8Array) {
  let crc = 0xffffffff;
  data.forEach((byte) => {
    crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  });
  return (crc ^ 0xffffffff) >>> 0;
}
