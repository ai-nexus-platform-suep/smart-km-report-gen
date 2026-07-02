package com.powerreport.admin.service.support;

import com.powerreport.admin.dto.TemplateOutlineNodeDto;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class TemplateDocxOutlineParser {

    private static final int MAX_LEVEL = 5;
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(\\d+(?:\\.\\d+){0,4})(?:[\\s\\u3000.．、:：-]+)(.+)$"
    );
    private static final Pattern HEADING_LEVEL = Pattern.compile("(?:heading|title|标题)\\s*(\\d)", Pattern.CASE_INSENSITIVE);

    public List<TemplateOutlineNodeDto> parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return parse(inputStream);
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to parse template DOCX outline", ex);
        }
    }

    public List<TemplateOutlineNodeDto> parse(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<FlatOutlineNode> flatNodes = new ArrayList<>();
            int[] counters = new int[MAX_LEVEL + 1];

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = cleanText(paragraph.getText());
                if (!StringUtils.hasText(text)) {
                    continue;
                }

                ParsedHeading parsedHeading = parseNumberedHeading(text);
                if (parsedHeading == null) {
                    Integer styleLevel = resolveStyleLevel(document, paragraph);
                    if (styleLevel == null) {
                        continue;
                    }
                    int level = clampLevel(styleLevel);
                    String number = nextNumber(counters, level);
                    parsedHeading = new ParsedHeading(number, text, level);
                }

                flatNodes.add(new FlatOutlineNode(
                        parsedHeading.number(),
                        parsedHeading.title(),
                        parsedHeading.level()
                ));
            }
            return buildTree(flatNodes);
        }
    }

    private ParsedHeading parseNumberedHeading(String text) {
        Matcher matcher = NUMBERED_HEADING.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String number = matcher.group(1);
        String title = cleanText(matcher.group(2));
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return new ParsedHeading(number, title, clampLevel(number.split("\\.").length));
    }

    private Integer resolveStyleLevel(XWPFDocument document, XWPFParagraph paragraph) {
        Integer fromStyleId = parseLevel(paragraph.getStyleID());
        if (fromStyleId != null) {
            return fromStyleId;
        }
        if (document.getStyles() == null || !StringUtils.hasText(paragraph.getStyleID())) {
            return null;
        }
        XWPFStyle style = document.getStyles().getStyle(paragraph.getStyleID());
        return style == null ? null : parseLevel(style.getName());
    }

    private Integer parseLevel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = HEADING_LEVEL.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int level = Integer.parseInt(matcher.group(1));
        return level >= 1 && level <= MAX_LEVEL ? level : null;
    }

    private String nextNumber(int[] counters, int level) {
        counters[level]++;
        for (int i = level + 1; i < counters.length; i++) {
            counters[i] = 0;
        }
        for (int i = 1; i < level; i++) {
            if (counters[i] == 0) {
                counters[i] = 1;
            }
        }

        List<String> parts = new ArrayList<>();
        for (int i = 1; i <= level; i++) {
            parts.add(String.valueOf(counters[i]));
        }
        return String.join(".", parts);
    }

    private List<TemplateOutlineNodeDto> buildTree(List<FlatOutlineNode> flatNodes) {
        List<TemplateOutlineNodeDto> roots = new ArrayList<>();
        List<TemplateOutlineNodeDto> stack = new ArrayList<>();

        for (FlatOutlineNode flatNode : flatNodes) {
            TemplateOutlineNodeDto node = new TemplateOutlineNodeDto();
            node.setId(UUID.randomUUID().toString());
            node.setNumber(flatNode.number());
            node.setTitle(flatNode.title());
            node.setLevel(flatNode.level());
            node.setPromptHint("Based on template section: " + flatNode.title());

            while (stack.size() >= flatNode.level()) {
                stack.remove(stack.size() - 1);
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.get(stack.size() - 1).getChildren().add(node);
            }
            stack.add(node);
        }
        return roots;
    }

    private String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private int clampLevel(int level) {
        return Math.max(1, Math.min(level, MAX_LEVEL));
    }

    private record ParsedHeading(String number, String title, int level) {
    }

    private record FlatOutlineNode(String number, String title, int level) {
    }
}
