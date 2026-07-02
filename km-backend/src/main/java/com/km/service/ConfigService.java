package com.km.service;

import com.km.dto.request.EmbeddingConfigRequest;
import com.km.dto.request.ParserConfigRequest;
import com.km.dto.request.RerankConfigRequest;
import com.km.vo.ConfigTestResultVO;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;

public interface ConfigService {

    EmbeddingConfigVO getEmbeddingConfig();

    EmbeddingConfigVO getEmbeddingConfigInternal();

    EmbeddingConfigVO updateEmbeddingConfig(EmbeddingConfigRequest request);

    RerankConfigVO getRerankConfig();

    RerankConfigVO getRerankConfigInternal();

    RerankConfigVO updateRerankConfig(RerankConfigRequest request);

    ParserConfigVO getParserConfig();

    ParserConfigVO updateParserConfig(ParserConfigRequest request);

    ConfigTestResultVO testEmbeddingConfig();

    ConfigTestResultVO testRerankConfig();
}
