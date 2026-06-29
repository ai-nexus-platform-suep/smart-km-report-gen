import re

p = r"E:\ideaProject\04\smart-km-report-gen\km-backend\src\main\java\com\km\client\KmAiClient.java"
with open(p, "r", encoding="utf-8") as f:
    c = f.read()

# Replace the post method
old_start = "    @SuppressWarnings(\"unchecked\")"
old_end = "    }"
search_start = c.find(old_start)
if search_start >= 0:
    # Find the matching end (the closing brace of the method)
    brace_count = 0
    end_pos = search_start
    for i, ch in enumerate(c[search_start:]):
        if ch == "{":
            brace_count += 1
        elif ch == "}":
            brace_count -= 1
        if brace_count == 0 and i > 0:
            end_pos = search_start + i + 1
            break
    
    new_method = '''    private <T> AiApiResponse<T> post(String path, Object request) {
        String url = baseUrl + path;
        try {
            ResponseEntity<String> rawResp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request), String.class);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(rawResp.getBody());
            int code = root.get("code").asInt();
            String message = root.get("message").asText();
            if (code != 0) {
                log.warn("AI service error: code={}, msg={}, url={}", code, message, url);
                return null;
            }
            com.fasterxml.jackson.databind.JavaType apiType = mapper.getTypeFactory()
                .constructParametricType(AiApiResponse.class, Object.class);
            AiApiResponse<?> raw = mapper.readValue(rawResp.getBody(), apiType);
            Object rawData = raw.getData();
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            T data = mapper.treeToValue(dataNode, mapper.getTypeFactory().constructType(Object.class));
            // Use reflection to get the proper type - simpler: just let Jackson figure it out
            String jsonStr = rawResp.getBody();
            // Parse: get data field as generic JsonNode, then convert to target type
            @SuppressWarnings("unchecked")
            AiApiResponse<T> typed = (AiApiResponse<T>)raw;
            return typed;
        } catch (Exception e) {
            log.warn("AI call failed: {}, url={}", e.getMessage(), url);
            throw e;
        }
    }'''
    
    c = c[:search_start] + new_method + c[end_pos:]
    with open(p, "w", encoding="utf-8") as f:
        f.write(c)
    print("KmAiClient.post() fixed")
else:
    print("Method not found")
