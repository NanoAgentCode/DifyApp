"""
文件服务测试
"""
import pytest
from fastapi import HTTPException
from mindmap.modules.file_service import FileService


def test_file_service_import():
    """测试模块导入"""
    assert FileService is not None


def test_resolve_static_path_rejects_traversal():
    """测试文件路径不能越过static目录"""
    with pytest.raises(HTTPException) as exc_info:
        FileService._resolve_static_path("../config.ini")

    assert exc_info.value.status_code == 400


def test_generate_unique_filename_preserves_extension():
    """测试唯一文件名保留扩展名"""
    filename = FileService.generate_unique_filename("report.pdf")
    assert filename.endswith(".pdf")

