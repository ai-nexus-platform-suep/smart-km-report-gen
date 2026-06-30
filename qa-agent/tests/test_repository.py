"""DB CRUD 测试 — repository.py 全部方法

覆盖 PingCode: ZNZ-63 ZNZ-61 ZNZ-58 ZNZ-35 ZNZ-33

全部用 asyncio.run() 避免 Windows 事件循环冲突。

运行:
  cd d:/CS/ruangong/smart-km-report-gen/qa-agent
  PYTHONPATH="d:/CS/ruangong/smart-km-report-gen" python -m pytest tests/test_repository.py -v -s
"""

import asyncio

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from qa_agent.core.config import settings
from qa_agent.db.constants import (
    DEFAULT_TITLE,
    GENERATE_STATUS_COMPLETED,
    GENERATE_STATUS_GENERATING,
    ROLE_ASSISTANT,
    ROLE_USER,
    STATUS_ACTIVE,
    STATUS_DELETED,
    TITLE_MAX_LENGTH,
)
from qa_agent.db.models import QaSession
from qa_agent.db.repository import (
    _build_title_from_content,
    create_conversation,
    delete_conversation,
    get_conversation,
    get_messages,
    list_conversations,
    save_message,
    update_conversation_title,
    update_message,
)

# 每个测试独立的单连接 engine，避免 Windows 连接池清理问题
_test_engine = create_async_engine(settings.database_url, poolclass=NullPool)
_test_session = async_sessionmaker(_test_engine, class_=AsyncSession, expire_on_commit=False)

TEST_USER = 99999


def _clean_all():
    """模块级清理：删除 TEST_USER 的所有残留数据"""
    async def _do():
        async with _test_session() as db:
            await db.execute(text("DELETE FROM qa_message WHERE user_id = :uid"), {"uid": TEST_USER})
            await db.execute(text("DELETE FROM qa_session WHERE user_id = :uid"), {"uid": TEST_USER})
            await db.commit()
    asyncio.run(_do())


_clean_all()  # 模块加载时清理一次


# ==================================================================
# 1. _build_title_from_content (纯函数，无 DB)
# ==================================================================

class TestBuildTitle:
    def test_short_returns_as_is(self):
        assert _build_title_from_content("变压器油温异常") == "变压器油温异常"

    def test_long_truncates_with_ellipsis(self):
        text = "这是一段非常长的用户输入内容超过了五十个字符的限制需要被截断处理因为标题最多只能保留五十个字符再多就不行啦"
        assert len(text) > TITLE_MAX_LENGTH
        result = _build_title_from_content(text)
        assert len(result) <= TITLE_MAX_LENGTH + 3
        assert result.endswith("...")

    def test_exactly_max_length(self):
        exactly = "A" * TITLE_MAX_LENGTH
        assert _build_title_from_content(exactly) == exactly

    def test_whitespace_trimmed(self):
        assert _build_title_from_content("  你好  ") == "你好"


# ==================================================================
# 2. create_conversation
# ==================================================================

class TestCreateConversation:
    def test_create_with_default_title(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                print(f"  [CREATE] id={conv.id}  title={conv.title!r}  "
                      f"status={conv.status}  msg_count={conv.message_count}")
                assert conv.id > 0
                assert conv.user_id == TEST_USER
                assert conv.title == DEFAULT_TITLE
                assert conv.status == STATUS_ACTIVE
                assert conv.message_count == 0
                await db.rollback()
        asyncio.run(_test())

    def test_create_with_custom_title(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER, title="测试标题")
                print(f"  [CREATE] id={conv.id}  title={conv.title!r}  "
                      f"created_at={conv.created_at}")
                assert conv.title == "测试标题"
                await db.rollback()
        asyncio.run(_test())

    def test_create_persists_to_db(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.commit()
                print(f"  [INSERT] committed id={conv.id} -> DB")
                found = await get_conversation(db, conv.id)
                assert found is not None
                assert found.id == conv.id
                print(f"  [QUERY] get_conversation({conv.id}) -> found={found is not None}")
                # cleanup
                await db.execute(text("DELETE FROM qa_session WHERE id = :id"), {"id": conv.id})
                await db.commit()
                print(f"  [DELETE] cleaned id={conv.id}")
        asyncio.run(_test())


# ==================================================================
# 3. list_conversations
# ==================================================================

class TestListConversations:
    def test_list_empty_for_new_user(self):
        async def _test():
            async with _test_session() as db:
                items, total = await list_conversations(db, user_id=TEST_USER)
                assert total == 0
                assert items == []
        asyncio.run(_test())

    def test_list_ordered_by_last_message(self):
        async def _test():
            async with _test_session() as db:
                c1 = await create_conversation(db, user_id=TEST_USER, title="旧对话")
                c2 = await create_conversation(db, user_id=TEST_USER, title="新对话")
                await db.flush()
                await save_message(db, c2.id, ROLE_USER, "你好", user_id=TEST_USER)
                await db.commit()

                items, total = await list_conversations(db, user_id=TEST_USER)
                print(f"  [LIST] total={total}  first={items[0].title!r}(id={items[0].id})"
                      f"  last_msg={items[0].last_message_at}")
                assert total >= 2
                assert items[0].id == c2.id  # newer first

                # cleanup
                await db.execute(text("DELETE FROM qa_message WHERE session_id IN (:c1,:c2)"),
                                 {"c1": c1.id, "c2": c2.id})
                await db.execute(text("DELETE FROM qa_session WHERE id IN (:c1,:c2)"),
                                 {"c1": c1.id, "c2": c2.id})
                await db.commit()
        asyncio.run(_test())

    def test_list_pagination(self):
        async def _test():
            async with _test_session() as db:
                ids = []
                for i in range(5):
                    c = await create_conversation(db, user_id=TEST_USER, title=f"对话{i}")
                    ids.append(c.id)
                await db.commit()

                items, total = await list_conversations(db, user_id=TEST_USER, page=1, size=2)
                assert len(items) == 2
                assert total == 5

                # cleanup
                for cid in ids:
                    await db.execute(text("DELETE FROM qa_session WHERE id = :id"), {"id": cid})
                await db.commit()
        asyncio.run(_test())

    def test_deleted_not_listed(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.commit()
                await delete_conversation(db, conv.id)
                await db.commit()
                items, total = await list_conversations(db, user_id=TEST_USER)
                assert total == 0
                # cleanup
                await db.execute(text("DELETE FROM qa_session WHERE id = :id"), {"id": conv.id})
                await db.commit()
        asyncio.run(_test())


# ==================================================================
# 4. get_conversation
# ==================================================================

class TestGetConversation:
    def test_get_existing(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                found = await get_conversation(db, conv.id)
                assert found is not None
                assert found.id == conv.id
                await db.rollback()
        asyncio.run(_test())

    def test_get_non_existent(self):
        async def _test():
            async with _test_session() as db:
                found = await get_conversation(db, 999999999)
                assert found is None
        asyncio.run(_test())

    def test_get_deleted_returns_none(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.commit()
                await delete_conversation(db, conv.id)
                await db.commit()
                found = await get_conversation(db, conv.id)
                assert found is None
                await db.execute(text("DELETE FROM qa_session WHERE id = :id"), {"id": conv.id})
                await db.commit()
        asyncio.run(_test())


# ==================================================================
# 5. delete_conversation
# ==================================================================

class TestDeleteConversation:
    def test_soft_delete(self):
        async def _test():
            async with _test_session() as db:
                from sqlalchemy import select
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.commit()
                print(f"  [SOFT-DEL] before: id={conv.id}  status={conv.status}")
                await delete_conversation(db, conv.id)
                await db.commit()

                stmt = select(QaSession).where(QaSession.id == conv.id)
                deleted = (await db.execute(stmt)).scalar_one_or_none()
                print(f"  [SOFT-DEL] after:  status={deleted.status}  deleted_at={deleted.deleted_at}")
                assert deleted is not None
                assert deleted.status == STATUS_DELETED
                assert deleted.deleted_at is not None
                await db.execute(text("DELETE FROM qa_session WHERE id = :id"), {"id": conv.id})
                await db.commit()
        asyncio.run(_test())

    def test_delete_non_existent_raises(self):
        async def _test():
            async with _test_session() as db:
                with pytest.raises(ValueError, match="会话不存在"):
                    await delete_conversation(db, 999999999)
        asyncio.run(_test())

    def test_cascade_soft_deletes_messages(self):
        async def _test():
            async with _test_session() as db:
                from sqlalchemy import select
                conv = await create_conversation(db, user_id=TEST_USER)
                await save_message(db, conv.id, ROLE_USER, "测试消息", user_id=TEST_USER)
                await db.commit()

                await delete_conversation(db, conv.id)
                await db.commit()

                # 直接查表验证消息也被软删（get_messages 会先调 get_conversation
                # 而 get_conversation 过滤了 STATUS_DELETED，这里直接查）
                from qa_agent.db.models import QaMessage
                stmt = select(QaMessage).where(QaMessage.session_id == conv.id)
                all_msgs = (await db.execute(stmt)).scalars().all()
                for m in all_msgs:
                    assert m.status == STATUS_DELETED

                # cleanup
                await db.execute(text("DELETE FROM qa_message WHERE session_id = :sid"), {"sid": conv.id})
                await db.execute(text("DELETE FROM qa_session WHERE id = :sid"), {"sid": conv.id})
                await db.commit()
        asyncio.run(_test())


# ==================================================================
# 6. update_conversation_title
# ==================================================================

class TestUpdateConversationTitle:
    def test_update_success(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER, title="旧标题")
                await db.flush()
                updated = await update_conversation_title(db, conv.id, "新标题")
                assert updated.title == "新标题"
                await db.rollback()
        asyncio.run(_test())

    def test_update_non_existent_raises(self):
        async def _test():
            async with _test_session() as db:
                with pytest.raises(ValueError, match="会话不存在"):
                    await update_conversation_title(db, 999999999, "标题")
        asyncio.run(_test())

    def test_empty_title_falls_back(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                updated = await update_conversation_title(db, conv.id, "   ")
                assert updated.title == DEFAULT_TITLE
                await db.rollback()
        asyncio.run(_test())


# ==================================================================
# 7. save_message
# ==================================================================

class TestSaveMessage:
    def test_save_user_message(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                msg = await save_message(db, conv.id, ROLE_USER,
                                         "变压器油温异常怎么处理？",
                                         user_id=TEST_USER)
                print(f"  [SAVE MSG] id={msg.id}  seq={msg.seq}  role={msg.role}  "
                      f"content={msg.content[:30]!r}")
                assert msg.id > 0
                assert msg.seq == 1
                assert msg.role == ROLE_USER
                await db.rollback()
        asyncio.run(_test())

    def test_save_with_metadata(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                msg = await save_message(
                    db, conv.id, ROLE_ASSISTANT, "建议检查冷却系统",
                    user_id=TEST_USER,
                    intent_type="KNOWLEDGE_QA",
                    thinking_steps='[{"type":"intent"}]',
                    citations='[{"index":1}]',
                    generate_status=GENERATE_STATUS_GENERATING,
                    token_usage=150,
                )
                print(f"  [SAVE MSG] id={msg.id}  role={msg.role}  "
                      f"intent={msg.intent_type}  gen_status={msg.generate_status}  "
                      f"tokens={msg.token_usage}")
                assert msg.intent_type == "KNOWLEDGE_QA"
                assert msg.generate_status == GENERATE_STATUS_GENERATING
                assert msg.token_usage == 150
                await db.rollback()
        asyncio.run(_test())

    def test_seq_auto_increment(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                m1 = await save_message(db, conv.id, ROLE_USER, "Q1", user_id=TEST_USER)
                m2 = await save_message(db, conv.id, ROLE_ASSISTANT, "A1", user_id=TEST_USER)
                m3 = await save_message(db, conv.id, ROLE_USER, "Q2", user_id=TEST_USER)
                print(f"  [SEQ] m1.seq={m1.seq}  m2.seq={m2.seq}  m3.seq={m3.seq}")
                assert m1.seq == 1
                assert m2.seq == 2
                assert m3.seq == 3
                await db.rollback()
        asyncio.run(_test())

    def test_first_user_message_auto_title(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.commit()
                print(f"  [TITLE] before msg: {conv.title!r}")
                await save_message(db, conv.id, ROLE_USER,
                                   "变压器油温异常怎么处理？",
                                   user_id=TEST_USER)
                await db.commit()

                updated = await get_conversation(db, conv.id)
                print(f"  [TITLE] after msg:  {updated.title!r}")
                assert updated.title == "变压器油温异常怎么处理？"
                # cleanup
                await db.execute(text("DELETE FROM qa_message WHERE session_id = :sid"), {"sid": conv.id})
                await db.execute(text("DELETE FROM qa_session WHERE id = :sid"), {"sid": conv.id})
                await db.commit()
        asyncio.run(_test())

    def test_non_existent_conversation_raises(self):
        async def _test():
            async with _test_session() as db:
                with pytest.raises(ValueError, match="会话不存在"):
                    await save_message(db, 999999999, ROLE_USER, "test", user_id=TEST_USER)
        asyncio.run(_test())


# ==================================================================
# 8. get_messages
# ==================================================================

class TestGetMessages:
    def test_sorted_by_seq(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                await save_message(db, conv.id, ROLE_USER, "Q1", user_id=TEST_USER)
                await save_message(db, conv.id, ROLE_ASSISTANT, "A1", user_id=TEST_USER)
                await db.flush()

                items, total = await get_messages(db, conv.id)
                assert total == 2
                assert items[0].seq < items[1].seq
                await db.rollback()
        asyncio.run(_test())

    def test_pagination(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                for i in range(10):
                    await save_message(db, conv.id, ROLE_USER, f"消息{i}", user_id=TEST_USER)
                await db.flush()

                items, total = await get_messages(db, conv.id, page=1, size=3)
                assert len(items) == 3
                assert total == 10
                await db.rollback()
        asyncio.run(_test())

    def test_non_existent_conversation_raises(self):
        async def _test():
            async with _test_session() as db:
                with pytest.raises(ValueError, match="会话不存在"):
                    await get_messages(db, 999999999)
        asyncio.run(_test())


# ==================================================================
# 9. update_message
# ==================================================================

class TestUpdateMessage:
    def test_update_content(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                msg = await save_message(db, conv.id, ROLE_ASSISTANT, "",
                                         user_id=TEST_USER,
                                         generate_status=GENERATE_STATUS_GENERATING)
                await db.flush()
                print(f"  [UPDATE] before: content={msg.content!r}  gen_status={msg.generate_status}")
                updated = await update_message(db, msg.id, content="完整回答")
                print(f"  [UPDATE] after:  content={updated.content!r}  updated_at={updated.updated_at}")
                assert updated.content == "完整回答"
                await db.rollback()
        asyncio.run(_test())

    def test_update_all_fields(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                msg = await save_message(db, conv.id, ROLE_ASSISTANT, "", user_id=TEST_USER)
                await db.flush()
                updated = await update_message(
                    db, msg.id,
                    content="最终回答", intent_type="KNOWLEDGE_QA",
                    thinking_steps='[{"t":"intent"}]', citations='[{"i":1}]',
                    generate_status=GENERATE_STATUS_COMPLETED, token_usage=200,
                )
                assert updated.content == "最终回答"
                assert updated.intent_type == "KNOWLEDGE_QA"
                assert updated.generate_status == GENERATE_STATUS_COMPLETED
                assert updated.token_usage == 200
                await db.rollback()
        asyncio.run(_test())

    def test_non_existent_raises(self):
        async def _test():
            async with _test_session() as db:
                with pytest.raises(ValueError, match="消息不存在"):
                    await update_message(db, 999999999, content="test")
        asyncio.run(_test())

    def test_partial_update_keeps_others(self):
        async def _test():
            async with _test_session() as db:
                conv = await create_conversation(db, user_id=TEST_USER)
                await db.flush()
                msg = await save_message(db, conv.id, ROLE_ASSISTANT, "原始",
                                         user_id=TEST_USER, intent_type="CHAT",
                                         generate_status=GENERATE_STATUS_COMPLETED)
                await db.flush()
                updated = await update_message(db, msg.id, content="更新内容")
                assert updated.content == "更新内容"
                assert updated.intent_type == "CHAT"
                assert updated.generate_status == GENERATE_STATUS_COMPLETED
                await db.rollback()
        asyncio.run(_test())
