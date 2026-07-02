"""全局配置 (人员 A 先写定，B/C 只读)"""

from pathlib import Path

from pydantic_settings import BaseSettings

_ENV_FILE = str(Path(__file__).resolve().parent.parent.parent / ".env")


class Settings(BaseSettings):
<<<<<<< Updated upstream
    database_url: str = "mysql+aiomysql://root:1234@127.0.0.1:3306/smart_km_report_gen"
    jwt_secret_key: str = "change-me"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440
=======
    # ---- 数据库 ----
    database_url: str = "mysql+aiomysql://root:1234@127.0.0.1:3306/smart_km_report_gen"

    # ---- JWT ----
    jwt_secret_key: str = "change-me"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440

    # ---- LLM (DeepSeek) ----
>>>>>>> Stashed changes
    llm_api_url: str = "https://api.deepseek.com"
    llm_api_key: str = ""
    llm_model_name: str = "deepseek-chat"
    llm_timeout: int = 60
<<<<<<< Updated upstream
    embedding_api_url: str = ""
    embedding_api_key: str = ""
    embedding_model_name: str = ""
    reranker_api_url: str = ""
    reranker_api_key: str = ""
    reranker_model_name: str = ""
    vector_db_url: str = ""
    knowledge_search_url: str = ""
=======

    # ---- Embedding（可选）----
    embedding_api_url: str = ""
    embedding_api_key: str = ""
    embedding_model_name: str = ""

    # ---- Reranker（可选）----
    reranker_api_url: str = ""
    reranker_api_key: str = ""
    reranker_model_name: str = ""

    # ---- 向量数据库 ----
    vector_db_url: str = ""

    # ---- 知识库检索服务 ----
    knowledge_search_url: str = ""

    # ---- Java 后端配置服务 ----
>>>>>>> Stashed changes
    java_config_base_url: str = ""
    java_llm_config_path: str = "/api/admin/qa/llm-config"
    java_retrieval_config_path: str = "/api/admin/qa/retrieval-config"

<<<<<<< Updated upstream
=======
    # ---- 检索参数 ----
>>>>>>> Stashed changes
    default_top_k: int = 5
    default_similarity_threshold: float = 0.7
    default_rerank_threshold: float = 0.0
    retrieval_timeout: int = 10

    model_config = {"env_file": _ENV_FILE, "extra": "ignore"}


settings = Settings()
