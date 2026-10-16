"""Offline schema validation — no API key required."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pydantic import ValidationError
from app.models.schemas import TrainingLevel


def valid_level_dict():
    return {
        "theme": "告别",
        "training_goal": "从个人瞬间写到时代",
        "difficulty": "medium",
        "scenario": "火车站台",
        "slots": [
            {
                "id": "a",
                "label": "具体事件",
                "role": "定锚",
                "options": [
                    {"id": "a1", "text": "火车即将开动", "function": "事件", "suitability": "好", "potential_issue": ""},
                    {"id": "a2", "text": "广播响起", "function": "事件", "suitability": "中", "potential_issue": "弱"},
                    {"id": "a3", "text": "雨忽然大了", "function": "事件", "suitability": "中", "potential_issue": ""},
                ],
            },
            {
                "id": "b",
                "label": "人物反应",
                "role": "情绪",
                "options": [
                    {"id": "b1", "text": "他攥紧车票", "function": "动作", "suitability": "好", "potential_issue": ""},
                    {"id": "b2", "text": "她很伤心", "function": "直抒", "suitability": "弱", "potential_issue": "直白"},
                    {"id": "b3", "text": "他转身又停住", "function": "动作", "suitability": "好", "potential_issue": ""},
                    {"id": "b4", "text": "她把短信删了", "function": "动作", "suitability": "中", "potential_issue": ""},
                ],
            },
        ],
        "reference_combination": {"a": "a1", "b": "b1"},
        "reference_rationale": "具体+克制",
        "common_pitfalls": ["情绪直白"],
        "next_difficulty_hint": "加入时代对照",
        "summary_fingerprint": "station-farewell",
    }


def test_valid():
    level = TrainingLevel.model_validate(valid_level_dict())
    assert len(level.slots) == 2
    assert 3 <= len(level.slots[0].options) <= 5


def test_too_few_options():
    d = valid_level_dict()
    d["slots"][0]["options"] = d["slots"][0]["options"][:2]
    try:
        TrainingLevel.model_validate(d)
        raise AssertionError("should fail")
    except ValidationError:
        pass


if __name__ == "__main__":
    test_valid()
    test_too_few_options()
    print("schema tests OK")
