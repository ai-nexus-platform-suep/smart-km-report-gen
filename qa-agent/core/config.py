"""全局配置 (人员 A 先写定，B/C 只读)"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "mysql+aiomysql://root:1234@127.0.0.1:3306/smart-km-report-gen"
    jwt_secret_key: str = "change-me"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440
    llm_api_url: str = "https://api.openai.com/v1"
    llm_api_key: str = ""
    llm_model_name: str = "gpt-4o-mini"
    llm_timeout: int = 60
    embedding_api_url: str = "https://api.openai.com/v1"
    embedding_api_key: str = ""
    embedding_model_name: str = "text-embedding-3-small"
    vector_db_url: str = ""

    default_top_k: int = 5
    default_similarity_threshold: float = 0.7

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
