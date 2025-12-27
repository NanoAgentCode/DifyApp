"""
思维导图服务测试
"""
import pytest
from mindmap.modules.mindmap_service import MindmapService


def test_mindmap_service_import():
    """测试模块导入"""
    assert MindmapService is not None


# 可以添加更多测试用例
# def test_process_markdown():
#     """测试Markdown处理"""
#     pass

