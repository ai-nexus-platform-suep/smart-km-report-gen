package com.powerreport.gateway.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网关全局异常处理
 */
@Slf4j
@Order(-1)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        int status;
        String message;

        if (ex instanceof WebExchangeBindException bindException) {
            status = HttpStatus.BAD_REQUEST.value();
            message = bindException.getBindingResult().getAllErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof ResponseStatusException statusException) {
            status = statusException.getStatusCode().value();
            message = statusException.getReason();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "服务器内部错误";
        }

        log.error("Gateway error: status={}, message={}", status, message, ex);

        // 使用 ObjectMapper 序列化 Map 生成标准 JSON，避免手动拼接导致特殊字符破坏 JSON 格式
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("code", status);
        bodyMap.put("message", message);
        bodyMap.put("data", null);

        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(bodyMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            bodyBytes = ("{\"code\":500,\"message\":\"服务器内部错误\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
        return response.writeWith(Mono.just(buffer));
    }
}
