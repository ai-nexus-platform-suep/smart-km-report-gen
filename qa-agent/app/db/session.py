"""异步数据库 Session 管理 (人员 B 独占)"""

from collections.abc import AsyncGenerator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import settings

engine = None
async_session_factory = None


def get_engine():
    global engine
    if engine is None:
        engine = create_async_engine(settings.database_url, echo=False)
    return engine


def get_session_factory():
    global async_session_factory
    if async_session_factory is None:
        async_session_factory = async_sessionmaker(get_engine(), class_=AsyncSession, expire_on_commit=False)
    return async_session_factory


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with get_session_factory()() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
