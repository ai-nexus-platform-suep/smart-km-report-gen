package com.qa.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "昵称最多 50 个字符")
    private String nickname;

    @Size(max = 50, message = "真实姓名最多 50 个字符")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100)
    private String email;

    @Size(max = 20, message = "手机号最多 20 个字符")
    private String phone;

    @Size(max = 500, message = "头像 URL 过长")
    private String avatar;

    @Min(value = 0, message = "性别取值 0=未知 1=男 2=女")
    @Max(value = 2, message = "性别取值 0=未知 1=男 2=女")
    private Integer gender;
}
