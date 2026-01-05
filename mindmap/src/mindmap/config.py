"""
配置文件
"""
import configparser
import sys
from pathlib import Path
from typing import List, Final, Dict, Any, Set

def get_base_dir() -> Path:
    """
    获取基础目录，兼容exe和开发环境
    
    Returns:
        Path: 基础目录路径（项目根目录）
    """
    if getattr(sys, 'frozen', False):
        # 如果是打包后的exe，使用exe所在目录
        return Path(sys.executable).parent
    else:
        # 如果是开发环境，从 src/mindmap/config.py 向上两级到项目根目录
        # src/mindmap/config.py -> src/mindmap -> src -> 项目根目录
        return Path(__file__).parent.parent.parent

# 基础配置
BASE_DIR: Final[Path] = get_base_dir()
STATIC_DIR: Final[Path] = BASE_DIR / "static"
MARKDOWN_DIR: Final[Path] = STATIC_DIR / "markdown"
STATIC_HTML_DIR: Final[Path] = STATIC_DIR / "html"

# 确保必要的目录存在
STATIC_DIR.mkdir(parents=True, exist_ok=True)
MARKDOWN_DIR.mkdir(parents=True, exist_ok=True)
STATIC_HTML_DIR.mkdir(parents=True, exist_ok=True)

# 静态文件配置
JS_DIR: Final[Path] = BASE_DIR / "htmljs"
CSS_DIR: Final[Path] = BASE_DIR / "htmljs"  # CSS文件也在htmljs目录中

# 读取ini配置文件
config = configparser.ConfigParser()
config_file = BASE_DIR / "config.ini"

# 添加调试信息
print(f"Base directory: {BASE_DIR}")
print(f"Config file path: {config_file}")
print(f"Config file exists: {config_file.exists()}")

config.read(config_file, encoding='utf-8')

# 检查配置文件是否成功读取
if not config.sections():
    raise FileNotFoundError(f"配置文件未找到或为空: {config_file}")

# 文件上传配置
MAX_FILE_SIZE: Final[int] = config.getint('file_upload', 'max_file_size_mb') * 1024 * 1024  # 转换为字节
CHUNK_SIZE: Final[int] = config.getint('file_upload', 'chunk_size_kb') * 1024  # 转换为字节

# 服务器配置
SERVER_HOST: Final[str] = config.get('server', 'host')
SERVER_PORT: Final[int] = config.getint('server', 'port')
DEBUG: Final[bool] = config.getboolean('server', 'debug')

# 静态文件暴露配置
STATIC_FILES_CONFIG: Final[Dict[str, Dict[str, Any]]] = {
    'js': {
        'path': str(JS_DIR),
        'url_prefix': '/htmljs',
        'enabled': config.getboolean('static_files', 'enable_js_exposure') if 'static_files' in config else True,
        'files': [
            'index.js',
            'index2.js',
            'd3.min.js',
            'style.css',
            'browser/index.js',
            'html2canvas.min.js'
        ]
    },
    'html': {
        'path': str(STATIC_HTML_DIR),
        'url_prefix': '/html',
        'enabled': config.getboolean('static_files', 'enable_js_exposure') if 'static_files' in config else True,
        'files': [
            '*.html'
        ]
    },
    'static': {
        'path': str(STATIC_DIR),
        'url_prefix': '/static',
        'enabled': config.getboolean('static_files', 'enable_static_exposure') if 'static_files' in config else True
    }
}

# 允许的文件类型
ALLOWED_EXTENSIONS: Final[Set[str]] = {
    '.txt', '.md', '.pdf', '.png', '.jpg', '.jpeg', '.gif', '.doc', '.docx',
    '.xls', '.xlsx', '.ppt', '.pptx', '.zip', '.rar', '.html', '.css', '.js',
    '.json', '.xml', '.csv', '.mp3', '.mp4', '.avi', '.mov'
}

# MIME类型映射
MIME_TYPES: Final[Dict[str, str]] = {
    '.pdf': 'application/pdf',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.html': 'text/html',
    '.htm': 'text/html',
    '.txt': 'text/plain',
    '.json': 'application/json',
    '.xml': 'application/xml',
    '.css': 'text/css',
    '.js': 'application/javascript',
}

# 默认MIME类型
DEFAULT_MIME_TYPE: Final[str] = 'application/octet-stream'

# 思维导图配置
MAX_MARKDOWN_LENGTH: Final[int] = config.getint('mindmap', 'max_markdown_length', fallback=50000) if 'mindmap' in config else 50000
MAX_MARKDOWN_LENGTH_WARNING: Final[int] = config.getint('mindmap', 'max_markdown_length_warning', fallback=30000) if 'mindmap' in config else 30000

# 获取可用的JS文件列表
def get_available_js_files() -> List[str]:
    """
    获取可用的JS文件列表
    
    Returns:
        List[str]: JS和CSS文件路径列表
    """
    js_files = []
    if JS_DIR.exists():
        for file_path in JS_DIR.rglob('*.js'):
            relative_path = file_path.relative_to(JS_DIR)
            js_files.append(str(relative_path))
        for file_path in JS_DIR.rglob('*.css'):
            relative_path = file_path.relative_to(JS_DIR)
            js_files.append(str(relative_path))
    return js_files

# 获取静态文件访问URL
def get_static_file_url(file_path: str, static_type: str = 'js') -> str:
    """
    获取静态文件的访问URL
    
    Args:
        file_path: 文件路径
        static_type: 静态文件类型 ('js', 'html', 'static')
        
    Returns:
        str: 完整的访问URL
    """
    if static_type == 'js':
        return f"http://{SERVER_HOST}:{SERVER_PORT}/htmljs/{file_path}"
    elif static_type == 'html':
        return f"http://{SERVER_HOST}:{SERVER_PORT}/html/{file_path}"
    elif static_type == 'static':
        return f"http://{SERVER_HOST}:{SERVER_PORT}/static/{file_path}"
    return ""

