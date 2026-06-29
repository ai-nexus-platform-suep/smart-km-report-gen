"""QaSession, QaMessage ORM 定义 (人员 B 先写定，A/C 只读)"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class QaSession(Base):
    __tablename__ = "qa_session"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    title: Mapped[str] = mapped_column(String(200), nullable=False, default="新对话")
    status: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    message_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_message_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False), server_default=func.now()
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False), server_default=func.now(), onupdate=func.now()
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)

    messages: Mapped[list["QaMessage"]] = relationship(
        back_populates="session", cascade="all, delete-orphan"
    )


class QaMessage(Base):
    __tablename__ = "qa_message"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    session_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("qa_session.id"), nullable=False
    )
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    seq: Mapped[int] = mapped_column(Integer, nullable=False)
    role: Mapped[str] = mapped_column(String(20), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    intent_type: Mapped[str | None] = mapped_column(String(50), nullable=True)
    thinking_steps: Mapped[str | None] = mapped_column(Text, nullable=True)
    citations: Mapped[str | None] = mapped_column(Text, nullable=True)
    generate_status: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    token_usage: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False), server_default=func.now(), onupdate=func.now()
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)

    session: Mapped["QaSession"] = relationship(back_populates="messages")


Conversation = QaSession
Message = QaMessage
