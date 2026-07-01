package com.powerreport.admin.service;

import com.powerreport.admin.dto.AssetFileResource;
import com.powerreport.admin.dto.AssetImportResultResponse;
import com.powerreport.admin.dto.AssetPageResponse;
import com.powerreport.admin.dto.AssetResponse;
import com.powerreport.admin.dto.AssetUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

public interface AssetService {

    AssetPageResponse list(Integer page, Integer size, String category, Boolean enabled, String keyword);

    AssetResponse upload(MultipartFile file, String name, String category, String description,
                         String tags, Boolean enabled, String username);

    AssetResponse detail(String assetId);

    AssetResponse update(String assetId, AssetUpdateRequest request);

    AssetFileResource loadFile(String assetId);

    void delete(String assetId);

    AssetImportResultResponse importSeed(String username);
}
