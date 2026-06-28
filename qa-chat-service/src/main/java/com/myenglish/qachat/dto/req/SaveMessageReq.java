package com.myenglish.qachat.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveMessageReq {

    @NotBlank(message = "role 不能为空")
    @Pattern(regexp = "user|assistant|system", message = "role 必须为 user、assistant 或 system")
    private String role;

    @NotBlank(message = "content 不能为空")
    private String content;

    @Size(max = 50, message = "intentType 长度不能超过50")
    private String intentType;

    /** JSON 字符串 */
    private String thinkingSteps;

    /** JSON 字符串 */
    private String citations;

    /** 0=生成中 1=已完成 2=失败，默认 1 */
    @Min(value = 0, message = "generateStatus 取值范围为 0~2")
    @Max(value = 2, message = "generateStatus 取值范围为 0~2")
    private Integer generateStatus;

    private Integer tokenUsage;
}
