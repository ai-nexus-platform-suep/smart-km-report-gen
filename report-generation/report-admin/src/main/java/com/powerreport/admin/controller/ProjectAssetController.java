package com.powerreport.admin.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.admin.dto.AssetFileResource;
import com.powerreport.admin.dto.AssetImportResultResponse;
import com.powerreport.admin.dto.AssetPageResponse;
import com.powerreport.admin.dto.AssetResponse;
import com.powerreport.admin.dto.AssetUpdateRequest;
import com.powerreport.admin.service.AssetService;
import com.powerreport.enums.AssetCategory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/assets")
public class ProjectAssetController {

    private final AssetService assetService;

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, String>>> categories() {
        List<Map<String, String>> categories = Arrays.stream(AssetCategory.values())
                .map(category -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("value", category.name());
                    item.put("label", category.getLabel());
                    return item;
                })
                .collect(Collectors.toList());
        return ApiResponse.success(categories);
    }

    @GetMapping
    public ApiResponse<AssetPageResponse> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(assetService.list(page, size, category, enabled, keyword));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestHeader(value = "X-Username", defaultValue = "local_user") String username
    ) {
        return ApiResponse.success(assetService.upload(file, name, category, description, tags, enabled, username));
    }

    @GetMapping("/{assetId}")
    public ApiResponse<AssetResponse> detail(@PathVariable String assetId) {
        return ApiResponse.success(assetService.detail(assetId));
    }

    @PutMapping("/{assetId}")
    public ApiResponse<AssetResponse> update(
            @PathVariable String assetId,
            @RequestBody AssetUpdateRequest request
    ) {
        return ApiResponse.success(assetService.update(assetId, request));
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> delete(@PathVariable String assetId) {
        assetService.delete(assetId);
        return ApiResponse.success();
    }

    @GetMapping("/{assetId}/download")
    public ResponseEntity<Resource> download(@PathVariable String assetId) {
        AssetFileResource file = assetService.loadFile(assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(file.resource());
    }

    @PostMapping("/import-seed")
    public ApiResponse<AssetImportResultResponse> importSeed(
            @RequestHeader(value = "X-Username", defaultValue = "local_user") String username
    ) {
        return ApiResponse.success(assetService.importSeed(username));
    }
}
