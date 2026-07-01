package com.powerreport.admin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TemplateVisualConfigDto {

    private FontConfig fonts = new FontConfig();
    private MarginConfig margins = new MarginConfig();
    private ParagraphConfig paragraph = new ParagraphConfig();
    private CaptionConfig caption = new CaptionConfig();
    private List<TemplateOutlineNodeDto> outline = new ArrayList<>();

    @Data
    public static class FontConfig {
        private String titleFont = "黑体";
        private String bodyFont = "仿宋_GB2312";
        private Integer titleSize = 22;
        private Integer heading1Size = 16;
        private Integer heading2Size = 14;
        private Integer bodySize = 12;
    }

    @Data
    public static class MarginConfig {
        private Double topCm = 2.54;
        private Double bottomCm = 2.54;
        private Double leftCm = 2.79;
        private Double rightCm = 2.54;
    }

    @Data
    public static class ParagraphConfig {
        private Double lineSpacing = 1.5;
        private Integer firstLineIndentChars = 2;
    }

    @Data
    public static class CaptionConfig {
        private String figureNumberingMode = "GLOBAL";
        private String tableNumberingMode = "GLOBAL";
    }
}
