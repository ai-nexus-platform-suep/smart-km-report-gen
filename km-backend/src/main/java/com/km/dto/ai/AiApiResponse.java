package com.km.dto.ai;

import lombok.Data;

@Data
public class AiApiResponse<T> {
    private int code;
    private String message;
    private T data;
}
