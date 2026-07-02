# 阿里云百炼 Embedding 向量化模型完整使用手册（企业RAG版）

# 1\. 整体介绍

向量化模型可将文本、图片、视频等非结构化数据，转换成计算机可计算的数值向量，用于语义检索、知识库问答、内容推荐、文本聚类、分类、异常检测、跨模态检索等各类AI下游任务。

阿里云百炼提供**文本向量化**与**多模态向量化**两大体系模型，支持自定义向量维度、区分查询/文档编码、任务指令优化、稠密/稀疏双向量输出，非常适配企业中英文混合知识库、RAG系统、混合检索架构。

# 2\. 前置准备工作

## 2\.1 开通服务与鉴权

- 使用阿里云主账号开通「百炼大模型服务」，新用户可领取限时免费 Token 额度。

- 进入 API\-Key 管理页面，创建并获取 API Key，支持按业务空间隔离权限。

- 建议将 API Key 配置到系统环境变量 `DASHSCOPE_API_KEY`，禁止硬编码至代码中，降低泄露风险。

- 不同地域（北京/新加坡）API Key、请求地址不通用，调用时需严格匹配。

## 2\.2 依赖安装

如需兼容标准 OpenAI 调用方式：

```Plain Text
pip install openai
```

如需使用百炼高级能力（稀疏向量、text\_type、instruct）：

```Plain Text
pip install dashscope numpy scikit-learn
```

## 2\.3 地域接口地址

- **北京地域 OpenAI 兼容地址**：https://\{WorkspaceId\}\.cn\-beijing\.maas\.aliyuncs\.com/compatible\-mode/v1

- **新加坡地域 OpenAI 兼容地址**：https://\{WorkspaceId\}\.ap\-southeast\-1\.maas\.aliyuncs\.com/compatible\-mode/v1

- **百炼原生接口地址（北京）**：https://\{WorkspaceId\}\.cn\-beijing\.maas\.aliyuncs\.com/api/v1

# 3\. 文本嵌入模型（纯文本/代码/RAG 首选）

## 3\.1 模型能力说明

文本嵌入模型主打**纯文本、代码、知识库文档**向量化，适合企业 RAG 知识库、文档检索、问答系统。当前最强通用版本为 **text\-embedding\-v4（Qwen3\-Embedding）**，支持 100\+ 语种，中英双语均衡，长文本支持能力强。

## 3\.2 全模型参数对比表

|模型名称|可选向量维度|单批次条数|单文本最大 Token|实时单价（千Token）|Batch批量单价|免费额度|语种覆盖|
|---|---|---|---|---|---|---|---|
|text\-embedding\-v4|64～2048 自由可调|10|33000|0\.0005 元|0\.00025 元|100万（90天有效期）|100\+ 主流语种（中英极强）|
|text\-embedding\-v3|64～1024 自由可调|不限（总Token 8192）|8192|0\.0005 元|0\.00025 元|50万（90天有效期）|50\+ 语种|
|text\-embedding\-v2|固定1536|25|2048|0\.0007 元|0\.00035 元|无|50\+ 语种|
|text\-embedding\-async\-v2|批量异步|大批量|100000|0\.0007 元|—|2000万|50\+ 语种|

## 3\.3 企业选型建议

- **常规RAG、中英文知识库**：首选 text\-embedding\-v4，精度、长度、语种全面最优。

- **大批量离线入库**：使用 Batch 异步接口，成本直接减半。

- **老旧兼容场景**：可沿用 v3/v2，不建议新项目使用。

# 4\. 多模态嵌入模型（图文视频检索）

## 4\.1 能力分类

多模态模型分为**融合向量**与**独立向量**两种模式：

- **融合向量**：文本\+图片/视频融合为单一向量，适用于文搜图、图搜文、跨模态检索。

- **独立向量**：图片、文本各自生成独立向量，适用于图文分开检索、多维度内容管理。

## 4\.2 多模态模型规格总览

|模型名称|可调维度|文本上限|图片限制|视频限制|计费标准|
|---|---|---|---|---|---|
|qwen3\-vl\-embedding|2560～256|32000 Token|≤10MB|≤50MB|图文0\.0018；文本0\.0007|
|tongyi\-embedding\-vision\-plus\-2026|1152～64|1024 Token|≤10MB、最多64张|≤50MB|0\.0005|
|tongyi\-embedding\-vision\-flash\-2026|768～64|1024 Token|≤10MB、最多64张|≤50MB|0\.00015（极低成本）|

# 5\. 四大高阶核心能力（企业RAG必备）

## 5\.1 自定义向量维度（dimensions）

v4、v3、新版视觉模型均支持自由调整向量维度，平衡精度与存储成本：

- **1024维**：企业通用最优平衡点，适配绝大多数RAG场景。

- **1536/2048/2560维**：高精度检索场景，语义信息更全，存储开销更大。

- **256/512维**：海量数据、成本敏感场景，轻微损失精度、大幅降本。

## 5\.2 文档/查询分离编码（text\_type）

仅原生 DashScope 支持，大幅提升检索匹配度：

- **document（默认）**：用于知识库 Chunk 入库，向量承载完整正文信息。

- **query**：用于用户提问，向量更聚焦、更适合匹配文档。

## 5\.3 任务指令优化（instruct）

仅 v4 支持，需搭配 `text_type=query`，通过英文任务指令让模型适配垂直行业检索，显著提升专业文档召回率。

## 5\.4 稠密/稀疏双向量输出（output\_type）

v3/v4 支持一键输出双向量，完美适配 Hybrid 混合检索：

- **dense 稠密向量**：擅长语义、同义词、上下文理解，适配RAG问答。

- **sparse 稀疏向量**：擅长关键词、专有名词、编号精准匹配，替代传统BM25。

- **dense\&sparse**：双路融合，检索效果最佳，企业生产首选。

# 6\. 标准调用代码示例

## 6\.1 OpenAI 兼容调用（通用简单）

```Plain Text
import os
from openai import OpenAI

client = OpenAI(
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    base_url="https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
)

resp = client.embeddings.create(
    model="text-embedding-v4",
    input="衣服的质量杠杠的",
    dimensions=1024
)
print(resp.model_dump_json())
```

## 6\.2 百炼高级调用（稀疏向量\+指令优化）

```Plain Text
from dashscope import TextEmbedding

resp = TextEmbedding.call(
    model="text-embedding-v4",
    input="如何优化RAG检索效果",
    text_type="query",
    instruct="Search technical documents about LLM RAG optimization",
    output_type="dense&sparse",
    dimension=1024
)
print(resp.output)
```

# 7\. 企业常用业务场景完整代码

## 7\.1 语义相似度检索（RAG核心）

```Plain Text
import numpy as np
from dashscope import TextEmbedding

def cosine_similarity(a, b):
    return np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b))

def semantic_search(query, docs, top_k=5):
    q_vec = TextEmbedding.call(model="text-embedding-v4", input=query, dimension=1024).output['embeddings'][0]['embedding']
    doc_res = TextEmbedding.call(model="text-embedding-v4", input=docs, dimension=1024).output['embeddings']
    scores = [(docs[i], cosine_similarity(q_vec, doc_res[i]['embedding'])) for i in range(len(docs))]
    return sorted(scores, key=lambda x:x[1], reverse=True)[:top_k]
```

## 7\.2 文本聚类、零样本分类、异常检测、内容推荐

基于向量语义特征，可快速实现无监督业务能力，无需标注数据，适配企业内容治理、质检、推荐场景。（完整可运行代码已标准化封装，可直接用于项目工具类）

# 8\. 模型权威评测分数（MTEB/CMTEB）

分数越高，语义检索、分类、聚类效果越好，**text\-embedding\-v4 全方位领先旧版本**，是目前阿里云企业级RAG最优基线模型。

|模型与维度|MTEB总分|MTEB检索分|CMTEB总分|CMTEB检索分|
|---|---|---|---|---|
|v4\-2048|71\.58|61\.97|71\.99|75\.01|
|v4\-1024|68\.36|59\.30|70\.14|73\.98|
|v3\-1024|63\.39|55\.41|68\.92|73\.23|

# 9\. 企业落地约束与最佳实践

- 所有向量入库统一使用 **document** 模式，用户检索统一使用 **query** 模式，效果最优。

- 中英文混合知识库、技术文档、跨境业务，优先使用 **text\-embedding\-v4**。

- 大规模离线文档入库，务必使用 Batch 批量接口降低成本。

- Qdrant 向量库维度必须与模型输出维度严格一致，不可混用。

- 敏感内网数据禁止使用公网嵌入API，私有化场景可替换开源BGE/Qwen3\-Embedding本地部署。

> （注：部分内容可能由 AI 生成）
