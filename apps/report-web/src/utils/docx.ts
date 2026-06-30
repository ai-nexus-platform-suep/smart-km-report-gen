import type { ReportDetail, ReportSection, TableBlock } from "@/types/domain";

export const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

const encoder = new TextEncoder();
const INVALID_FILE_NAME_CHARS = /[\\/:*?"<>|]/g;

export function normalizeDocxFileName(input?: string, fallback = "report") {
  const base = (input || fallback)
    .replace(INVALID_FILE_NAME_CHARS, "_")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^\.+|\.+$/g, "");
  const safeBase = base || fallback;
  return /\.docx$/i.test(safeBase) ? safeBase : `${safeBase}.docx`;
}

export async function assertValidDocxBlob(blob: Blob) {
  const signature = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
  const isZipPackage = signature[0] === 0x50 && signature[1] === 0x4b;

  if (isZipPackage) return;

  let preview = "";
  try {
    preview = (await blob.slice(0, 160).text()).trim();
  } catch {
    preview = "";
  }

  throw new Error(preview ? `下载内容不是有效 DOCX：${preview}` : "下载内容不是有效 DOCX，请检查后端下载接口响应。");
}

export async function createReportDocxBlob(report: ReportDetail) {
  const documentXml = buildDocumentXml(report);
  const zip = createZipPackage({
    "[Content_Types].xml": contentTypesXml(),
    "_rels/.rels": rootRelsXml(),
    "word/_rels/document.xml.rels": documentRelsXml(),
    "word/styles.xml": stylesXml(),
    "word/document.xml": documentXml
  });

  return new Blob([zip], { type: DOCX_MIME });
}

function buildDocumentXml(report: ReportDetail) {
  const body = [
    paragraph(report.name, "Title"),
    paragraph(`报告主题：${report.subject}`),
    paragraph(`所属电厂：${report.powerPlant}    专业：${report.specialty}    年份：${report.reportYear}`),
    paragraph(`生成时间：${new Date().toLocaleString()}`),
    ...report.sections.flatMap(sectionToXml),
    '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>'
  ].join("");

  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>${body}</w:body>
</w:document>`;
}

function sectionToXml(section: ReportSection) {
  const parts: string[] = [paragraph(`${section.number} ${section.title}`, section.number.includes(".") ? "Heading2" : "Heading1")];
  const markdownLines = section.contentMarkdown
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !/^\|/.test(line));

  if (markdownLines.length === 0) {
    parts.push(paragraph("本章节暂无正文内容。"));
  } else {
    markdownLines.forEach((line) => {
      if (/^#{1,6}\s+/.test(line)) {
        parts.push(paragraph(line.replace(/^#{1,6}\s+/, ""), "Heading2"));
      } else if (/^[-*]\s+/.test(line)) {
        parts.push(paragraph(`• ${line.replace(/^[-*]\s+/, "")}`));
      } else {
        parts.push(paragraph(line));
      }
    });
  }

  if (section.tableJson) parts.push(table(section.tableJson));
  return parts;
}

function paragraph(text: string, style?: "Title" | "Heading1" | "Heading2") {
  const styleXml = style ? `<w:pPr><w:pStyle w:val="${style}"/></w:pPr>` : "";
  return `<w:p>${styleXml}<w:r><w:t xml:space="preserve">${escapeXml(text)}</w:t></w:r></w:p>`;
}

function table(block: TableBlock) {
  const rows = [block.columns, ...block.rows]
    .map(
      (row) =>
        `<w:tr>${row
          .map(
            (cell) =>
              `<w:tc><w:tcPr><w:tcW w:w="2400" w:type="dxa"/></w:tcPr>${paragraph(cell)}</w:tc>`
          )
          .join("")}</w:tr>`
    )
    .join("");

  return `<w:tbl><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:color="B8C2D6"/><w:left w:val="single" w:sz="4" w:color="B8C2D6"/><w:bottom w:val="single" w:sz="4" w:color="B8C2D6"/><w:right w:val="single" w:sz="4" w:color="B8C2D6"/><w:insideH w:val="single" w:sz="4" w:color="B8C2D6"/><w:insideV w:val="single" w:sz="4" w:color="B8C2D6"/></w:tblBorders></w:tblPr>${rows}</w:tbl>`;
}

function escapeXml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function contentTypesXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>`;
}

function rootRelsXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;
}

function documentRelsXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>`;
}

function stylesXml() {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
  <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="160" w:after="80"/></w:pPr><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style>
</w:styles>`;
}

function createZipPackage(files: Record<string, string>) {
  const entries = Object.entries(files).map(([name, content]) => ({ name, data: encoder.encode(content) }));
  const localParts: Uint8Array[] = [];
  const centralParts: Uint8Array[] = [];
  let offset = 0;

  entries.forEach((entry) => {
    const nameBytes = encoder.encode(entry.name);
    const checksum = crc32(entry.data);
    const { date, time } = dosDateTime(new Date());
    const localHeader = new Uint8Array(30 + nameBytes.length);

    writeUint32(localHeader, 0, 0x04034b50);
    writeUint16(localHeader, 4, 20);
    writeUint16(localHeader, 6, 0);
    writeUint16(localHeader, 8, 0);
    writeUint16(localHeader, 10, time);
    writeUint16(localHeader, 12, date);
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
    writeUint16(centralHeader, 12, time);
    writeUint16(centralHeader, 14, date);
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

function dosDateTime(date: Date) {
  const year = Math.max(date.getFullYear(), 1980);
  return {
    time: (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2),
    date: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate()
  };
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
