package com.myenglish.qachat.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionReq {

    @Size(max = 200, message = "标题长度不能超过200")
    private String title;
}
