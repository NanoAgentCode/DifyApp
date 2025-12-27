"""
应用入口文件
用于从项目根目录启动应用
"""
import sys
from pathlib import Path

# 添加src目录到Python路径
src_dir = Path(__file__).parent / "src"
if str(src_dir) not in sys.path:
    sys.path.insert(0, str(src_dir))

# 先导入config，确保配置已加载
# 注意：路径在运行时动态添加，IDE可能无法解析，但运行时正常
from mindmap.config import SERVER_HOST, SERVER_PORT, DEBUG  # pyright: ignore[reportMissingImports]
# 然后导入app
from mindmap.main import app  # pyright: ignore[reportMissingImports]
import uvicorn  # pyright: ignore[reportMissingImports]

def is_running_as_exe() -> bool:
    """
    检查是否作为exe运行
    
    Returns:
        bool: 如果是打包后的exe文件返回True，否则返回False
    """
    return getattr(sys, 'frozen', False)

if __name__ == "__main__":
    # 如果是exe运行，强制关闭热加载
    reload_enabled = False if is_running_as_exe() else DEBUG
    
    print(f"打包后关闭热加载: reload={reload_enabled}")
    
    # 在exe环境中直接传递app对象，而不是字符串
    if is_running_as_exe():
        uvicorn.run(
            app,  # 直接传递app对象
            host=SERVER_HOST,
            port=SERVER_PORT,
            reload=False
        )
    else:
        uvicorn.run(
            "mindmap.main:app",  # 使用包路径
            host=SERVER_HOST,
            port=SERVER_PORT,
            reload=reload_enabled
        )
