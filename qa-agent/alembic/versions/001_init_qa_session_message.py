"""init qa_session and qa_message tables

Revision ID: 001
Revises:
Create Date: 2026-06-28

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "qa_session",
        sa.Column("id", sa.BigInteger(), nullable=False, comment="SessionId，雪花ID"),
        sa.Column("user_id", sa.BigInteger(), nullable=False, comment="用户ID"),
        sa.Column(
            "title",
            sa.String(length=200),
            nullable=False,
            server_default="新对话",
            comment="会话标题",
        ),
        sa.Column(
            "status",
            sa.Integer(),
            nullable=False,
            server_default="1",
            comment="状态: 1=正常 0=已删除",
        ),
        sa.Column(
            "message_count",
            sa.Integer(),
            nullable=False,
            server_default="0",
            comment="消息条数",
        ),
        sa.Column(
            "last_message_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
            comment="最后一条消息时间",
        ),
        sa.Column(
            "created_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
            comment="创建时间",
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
            comment="更新时间",
        ),
        sa.Column("deleted_at", sa.DateTime(), nullable=True, comment="删除时间"),
        sa.PrimaryKeyConstraint("id"),
        mysql_comment="智能问答会话表",
        mysql_engine="InnoDB",
        mysql_charset="utf8mb4",
    )
    op.create_index(
        "idx_user_list",
        "qa_session",
        ["user_id", "status", "last_message_at", "id"],
        unique=False,
    )
    op.create_index(
        "idx_status_last_msg",
        "qa_session",
        ["status", "last_message_at", "id"],
        unique=False,
    )

    op.create_table(
        "qa_message",
        sa.Column("id", sa.BigInteger(), nullable=False, comment="MessageId，雪花ID"),
        sa.Column(
            "session_id",
            sa.BigInteger(),
            nullable=False,
            comment="SessionId，关联 qa_session.id",
        ),
        sa.Column("user_id", sa.BigInteger(), nullable=False, comment="用户ID"),
        sa.Column("seq", sa.Integer(), nullable=False, comment="会话内消息序号"),
        sa.Column("role", sa.String(length=20), nullable=False, comment="角色"),
        sa.Column("content", sa.Text(), nullable=False, comment="消息正文"),
        sa.Column("intent_type", sa.String(length=50), nullable=True, comment="意图类型"),
        sa.Column("thinking_steps", sa.Text(), nullable=True, comment="思考过程 JSON"),
        sa.Column("citations", sa.Text(), nullable=True, comment="引用溯源 JSON"),
        sa.Column(
            "generate_status",
            sa.Integer(),
            nullable=False,
            server_default="1",
            comment="生成状态: 0=生成中 1=已完成 2=失败",
        ),
        sa.Column("token_usage", sa.Integer(), nullable=True, comment="Token 消耗"),
        sa.Column(
            "status",
            sa.Integer(),
            nullable=False,
            server_default="1",
            comment="状态: 1=正常 0=已删除",
        ),
        sa.Column(
            "created_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
            comment="创建时间",
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
            comment="更新时间",
        ),
        sa.Column("deleted_at", sa.DateTime(), nullable=True, comment="删除时间"),
        sa.ForeignKeyConstraint(
            ["session_id"],
            ["qa_session.id"],
            name="fk_message_session",
            onupdate="RESTRICT",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("session_id", "seq", name="uk_session_seq"),
        mysql_comment="智能问答消息表",
        mysql_engine="InnoDB",
        mysql_charset="utf8mb4",
    )
    op.create_index(
        "idx_session_list",
        "qa_message",
        ["session_id", "status", "seq"],
        unique=False,
    )
    op.create_index(
        "idx_user_session",
        "qa_message",
        ["user_id", "session_id"],
        unique=False,
    )
    op.create_index(
        "idx_knowledge_qa_stat",
        "qa_message",
        ["intent_type", "role", "status", "created_at"],
        unique=False,
    )
    op.create_index(
        "idx_status_created",
        "qa_message",
        ["status", "created_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("idx_status_created", table_name="qa_message")
    op.drop_index("idx_knowledge_qa_stat", table_name="qa_message")
    op.drop_index("idx_user_session", table_name="qa_message")
    op.drop_index("idx_session_list", table_name="qa_message")
    op.drop_table("qa_message")
    op.drop_index("idx_status_last_msg", table_name="qa_session")
    op.drop_index("idx_user_list", table_name="qa_session")
    op.drop_table("qa_session")
