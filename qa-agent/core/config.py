"""全局配置 (人员 A 先写定，B/C 只读)"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "mysql+aiomysql://root:1234@127.0.0.1:3306/smart-km-report-gen"
    jwt_secret_key: str = "change-me"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440
    llm_api_url: str = "https://api.deepseek.com"
    llm_api_key: str = ""
    llm_model_name: str = "deepseek-chat"
    llm_timeout: int = 60
    embedding_api_url: str = ""
    embedding_api_key: str = ""
    embedding_model_name: str = ""
    vector_db_url: str = ""
    knowledge_search_url: str = ""
    java_config_base_url: str = ""
    java_llm_config_path: str = "/api/admin/qa/llm-config"
    java_retrieval_config_path: str = "/api/admin/qa/retrieval-config"

    default_top_k: int = 5
    default_similarity_threshold: float = 0.7
    default_rerank_threshold: float = 0.0
    retrieval_timeout: int = 10

    model_config = {"env_file": (".env", "qa-agent/.env"), "extra": "ignore"}


settings = Settings()
