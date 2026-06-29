"""Document text extraction from various file formats."""
import io
import pypdf
import docx
import pptx
import openpyxl

class DocumentParser:
    SUPPORTED_TYPES = {
        "application/pdf": "pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation": "pptx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "xlsx",
        "text/plain": "txt",
        "text/markdown": "md",
    }
    def parse(self, content: bytes, mime_type: str) -> str:
        ext = self.SUPPORTED_TYPES.get(mime_type, "")
        if ext == "pdf":
            return self._parse_pdf(content)
        elif ext == "docx":
            return self._parse_docx(content)
        elif ext == "pptx":
            return self._parse_pptx(content)
        elif ext == "xlsx":
            return self._parse_xlsx(content)
        else:
            return content.decode("utf-8", errors="replace")
    def _parse_pdf(self, content: bytes) -> str:
        reader = pypdf.PdfReader(io.BytesIO(content))
        return "\n\n".join(p.extract_text() for p in reader.pages if p.extract_text())
    def _parse_docx(self, content: bytes) -> str:
        doc = docx.Document(io.BytesIO(content))
        return "\n\n".join(p.text for p in doc.paragraphs if p.text.strip())
    def _parse_pptx(self, content: bytes) -> str:
        prs = pptx.Presentation(io.BytesIO(content))
        texts = []
        for slide in prs.slides:
            for shape in slide.shapes:
                if shape.has_text_frame:
                    for para in shape.text_frame.paragraphs:
                        if para.text.strip():
                            texts.append(para.text)
        return "\n\n".join(texts)
    def _parse_xlsx(self, content: bytes) -> str:
        wb = openpyxl.load_workbook(io.BytesIO(content), read_only=True)
        rows = []
        for sheet in wb.worksheets:
            for row in sheet.iter_rows(values_only=True):
                cells = [str(c) if c is not None else "" for c in row]
                line = "\t".join(cells)
                if line.strip():
                    rows.append(f"[{sheet.title}] {line}")
        return "\n".join(rows)
