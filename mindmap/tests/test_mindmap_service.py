"""
思维导图服务测试
"""
import asyncio
import pytest
from fastapi import HTTPException
from mindmap.modules.mindmap_service import MindmapService


def test_mindmap_service_import():
    """测试模块导入"""
    assert MindmapService is not None


def test_generate_filename_is_unique():
    """测试生成的文件名不会在快速连续调用时冲突"""
    names = {MindmapService.generate_filename() for _ in range(100)}
    assert len(names) == 100


def test_resolve_html_path_rejects_traversal():
    """测试HTML文件路径不能越过输出目录"""
    with pytest.raises(HTTPException) as exc_info:
        MindmapService._resolve_html_path("../secret.html")

    assert exc_info.value.status_code == 400


def test_process_markdown_rejects_empty_content():
    """测试空Markdown内容会被拒绝"""
    with pytest.raises(HTTPException) as exc_info:
        asyncio.run(MindmapService._generate_mindmap(None, "   "))

    assert exc_info.value.status_code == 400

