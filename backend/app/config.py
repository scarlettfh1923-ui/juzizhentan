from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    ai_base_url: str = "https://api.deepseek.com/v1"
    ai_api_key: str = ""
    ai_model: str = "deepseek-chat"
    ai_timeout_seconds: float = 60.0
    ai_max_retries: int = 2
    ai_max_input_chars: int = 8000
    ai_max_output_tokens: int = 4096

    search_provider: str = "ddg"  # ddg | none | serper | brave | tavily
    serper_api_key: str = ""
    brave_api_key: str = ""
    tavily_api_key: str = ""
    search_timeout_seconds: float = 20.0

    host: str = "0.0.0.0"
    port: int = 8765
    cache_ttl_seconds: int = 3600
    rate_limit_per_minute: int = 30


@lru_cache
def get_settings() -> Settings:
    return Settings()
