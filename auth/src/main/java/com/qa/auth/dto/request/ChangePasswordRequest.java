package com.qa.auth.dto.request;

<<<<<<< Updated upstream
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
=======
import com.qa.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
>>>>>>> Stashed changes
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
<<<<<<< Updated upstream
    @Size(min = 6, max = 64, message = "新密码长度应为 6-64 位")
=======
    @StrongPassword
>>>>>>> Stashed changes
    private String newPassword;
}
