"""验证雪花 ID 序列化修复：session_id / message_id 序列化为字符串。

BUG: 雪花算法生成的大整数超过 JS Number.MAX_SAFE_INTEGER (2^53-1 ≈ 9e15)，
     前端 JSON 解析时精度丢失。

FIX: ConversationVO / MessageVO / ConversationDetailVO 的 session_id/message_id
     通过 @field_serializer 序列化为字符串。
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent.parent))

from app.api.schemas import ConversationVO, MessageVO, ConversationDetailVO

# 雪花算法生成的 64-bit ID（典型范围 ~10^18，远大于 2^53-1 ≈ 9e15）
SNOWFLAKE_SESSION_ID = 1234567890123456789
SNOWFLAKE_MESSAGE_ID = 9876543210987654321

now = datetime.now(timezone.utc)


def test_conversation_vo_session_id_is_string():
    """ConversationVO 的 session_id 序列化为字符串"""
    vo = ConversationVO(
        session_id=SNOWFLAKE_SESSION_ID,
        title="测试会话",
        message_count=5,
        last_message_at=now,
        created_at=now,
    )
    dumped = vo.model_dump(mode="json")
    assert isinstance(dumped["session_id"], str), \
        f"session_id should be str, got {type(dumped['session_id'])}"
    assert dumped["session_id"] == str(SNOWFLAKE_SESSION_ID), \
        f"session_id mismatch: {dumped['session_id']} != {SNOWFLAKE_SESSION_ID}"


def test_message_vo_message_id_is_string():
    """MessageVO 的 message_id 序列化为字符串"""
    vo = MessageVO(
        message_id=SNOWFLAKE_MESSAGE_ID,
        seq=1,
        role="user",
        content="你好",
        generate_status=0,
        created_at=now,
        updated_at=now,
    )
    dumped = vo.model_dump(mode="json")
    assert isinstance(dumped["message_id"], str), \
        f"message_id should be str, got {type(dumped['message_id'])}"
    assert dumped["message_id"] == str(SNOWFLAKE_MESSAGE_ID), \
        f"message_id mismatch: {dumped['message_id']} != {SNOWFLAKE_MESSAGE_ID}"


def test_conversation_detail_vo_session_id_is_string():
    """ConversationDetailVO 的 session_id 序列化为字符串"""
    msg = MessageVO(
        message_id=SNOWFLAKE_MESSAGE_ID,
        seq=1,
        role="user",
        content="你好",
        generate_status=0,
        created_at=now,
        updated_at=now,
    )
    vo = ConversationDetailVO(
        session_id=SNOWFLAKE_SESSION_ID,
        title="测试会话",
        messages=[msg],
        total=1,
    )
    dumped = vo.model_dump(mode="json")
    assert isinstance(dumped["session_id"], str), \
        f"session_id should be str, got {type(dumped['session_id'])}"
    assert dumped["session_id"] == str(SNOWFLAKE_SESSION_ID)


def test_snowflake_id_roundtrip_via_string():
    """字符串序列化后可以无损还原为整数"""
    vo = ConversationVO(
        session_id=SNOWFLAKE_SESSION_ID,
        title="roundtrip",
        message_count=0,
        last_message_at=now,
        created_at=now,
    )
    json_str = json.dumps(vo.model_dump(mode="json"))
    parsed = json.loads(json_str)
    assert parsed["session_id"] == str(SNOWFLAKE_SESSION_ID)
    assert int(parsed["session_id"]) == SNOWFLAKE_SESSION_ID, \
        "roundtrip int(str(id)) != original id"


def test_small_ids_also_string():
    """小 ID (< 2^53-1) 也序列化为字符串，保持一致"""
    small_id = 42
    vo = ConversationVO(
        session_id=small_id,
        title="小ID",
        message_count=0,
        last_message_at=now,
        created_at=now,
    )
    dumped = vo.model_dump(mode="json")
    assert isinstance(dumped["session_id"], str)
    assert dumped["session_id"] == "42"


def test_js_safe_integer_boundary():
    """ID > Number.MAX_SAFE_INTEGER 时必须无损"""
    beyond_js = 9007199254740993  # 2^53 + 1，JS 无法精确表示
    vo = ConversationVO(
        session_id=beyond_js,
        title="边界测试",
        message_count=0,
        last_message_at=now,
        created_at=now,
    )
    dumped = vo.model_dump(mode="json")
    assert int(dumped["session_id"]) == beyond_js, \
        f"Precision lost! {int(dumped['session_id'])} != {beyond_js}"


def test_normal_int_fields_unchanged():
    """非 ID 的 int 字段（message_count, seq, total）不受影响"""
    vo = ConversationVO(
        session_id=SNOWFLAKE_SESSION_ID,
        title="测试",
        message_count=10,
        last_message_at=now,
        created_at=now,
    )
    dumped = vo.model_dump(mode="json")
    assert isinstance(dumped["message_count"], int), \
        "message_count should remain int"
    assert dumped["message_count"] == 10


if __name__ == "__main__":
    test_conversation_vo_session_id_is_string()
    test_message_vo_message_id_is_string()
    test_conversation_detail_vo_session_id_is_string()
    test_snowflake_id_roundtrip_via_string()
    test_small_ids_also_string()
    test_js_safe_integer_boundary()
    test_normal_int_fields_unchanged()
    print("All 7 snowflake serialization tests passed!")
