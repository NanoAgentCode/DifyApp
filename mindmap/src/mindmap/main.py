"""
FastAPI 主应用程序
"""
from typing import Dict, Any
from fastapi import FastAPI, Request, File, UploadFile, Form
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from mindmap.modules.mindmap_service import MindmapService
from mindmap.modules.file_service import FileService
from mindmap.config import SERVER_HOST, SERVER_PORT, DEBUG, STATIC_FILES_CONFIG, get_available_js_files, get_static_file_url

# 创建FastAPI应用
app = FastAPI(
    title="Mindmap & File Management Service",
    description="思维导图生成和文件管理服务",
    version="1.0.0"
)

# 配置CORS中间件，允许跨域访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 允许所有来源，生产环境建议指定具体域名
    allow_credentials=True,
    allow_methods=["*"],  # 允许所有HTTP方法
    allow_headers=["*"],  # 允许所有请求头
)

# 动态挂载静态文件目录
for static_type, config in STATIC_FILES_CONFIG.items():
    if config['enabled']:
        # 检查目录是否存在，不存在则创建
        from pathlib import Path
        dir_path = Path(config['path'])
        if not dir_path.exists():
            dir_path.mkdir(parents=True, exist_ok=True)
            print(f"已创建目录: {config['path']}")
        
        app.mount(config['url_prefix'], StaticFiles(directory=config['path']), name=static_type)
        print(f"已挂载静态文件: {config['url_prefix']} -> {config['path']}")

# ==================== 基础路由 ====================

@app.get("/")
def root() -> Dict[str, Any]:
    """
    根路径，返回API信息
    
    Returns:
        Dict[str, Any]: API信息字典，包含服务描述和端点信息
    """
    available_js_files = get_available_js_files()
    
    return {
        "message": "Mindmap & File Management Service",
        "version": "1.0.0",
        "services": {
            "mindmap": "思维导图生成服务",
            "file_management": "文件上传下载服务"
        },
        "endpoints": {
            "mindmap": {
                "upload": "POST /upload - 上传Markdown文本生成思维导图",
                "view": "GET /html/{filename} - 查看思维导图"
            },
            "file_management": {
                "upload": "POST /upload-file - 上传文件",
                "download": "GET /download/{file_path:path} - 下载文件",
                "preview": "GET /preview/{file_path:path} - 预览文件（浏览器直接显示）",
                "list": "GET /files - 获取文件列表",
                "save": "POST /save - 保存文本内容为文件"
            },
            "static_files": {
                "htmljs": "GET /htmljs/* - 访问JavaScript文件",
                "html": "GET /html/* - 访问HTML文件(思维导图)",
                "static": "GET /static/* - 访问静态文件"
            }
        },
        "js_files": {
            "available": available_js_files,
            "example_urls": [
                get_static_file_url("index.js", "js"),
                get_static_file_url("style.css", "js"),
                get_static_file_url("d3.min.js", "js")
            ]
        }
    }

@app.get("/htmljs-files")
def list_js_files() -> Dict[str, Any]:
    """
    列出所有可用的JS文件
    
    Returns:
        Dict[str, Any]: JS文件列表信息
    """
    js_files = get_available_js_files()
    return {
        "message": "可用的JS文件列表",
        "files": js_files,
        "total_count": len(js_files),
        "access_urls": [get_static_file_url(f, "js") for f in js_files]
    }

# ==================== 思维导图相关路由 ====================

@app.post("/upload")
async def upload_markdown(request: Request) -> str:
    """
    上传Markdown文本，生成思维导图（不替换CDN链接）
    
    Args:
        request: FastAPI请求对象
        
    Returns:
        str: 思维导图预览URL
    """
    content = await request.body()
    content = content.decode('utf-8')
    
    preview_url = await MindmapService.process_markdown(request, content)
    return preview_url


@app.post("/upload-local")
async def upload_markdown_replace(request: Request) -> str:
    """
    上传Markdown文本，生成思维导图（替换CDN链接并注入脚本）
    
    Args:
        request: FastAPI请求对象
        
    Returns:
        str: 思维导图预览URL
    """
    content = await request.body()
    content = content.decode('utf-8')

    preview_url = await MindmapService.process_markdown_replace(request, content)
    return preview_url


@app.get("/html/{filename}")
def get_html(filename: str):
    """
    获取生成的思维导图HTML文件
    
    Args:
        filename: HTML文件名
        
    Returns:
        FileResponse: HTML文件响应
    """
    return MindmapService.get_html_file(filename)

# ==================== 文件管理相关路由 ====================

@app.post("/upload-file")
async def upload_file(request: Request, file: UploadFile = File(...)):
    """
    上传文件到static目录
    """
    return await FileService.upload_file(request, file)

@app.get("/download/{file_path:path}")
def download_file(file_path: str):
    """
    下载或预览文件
    支持浏览器直接打开PDF、图片等文件
    支持子目录路径，如 text_files/filename.txt
    """
    return FileService.download_file(file_path)

@app.get("/preview/{file_path:path}")
def preview_file(file_path: str):
    """
    预览文件（在浏览器中直接显示）
    主要用于文本文件的预览
    支持子目录路径，如 text_files/filename.txt
    """
    return FileService.preview_file(file_path)

@app.get("/files")
def list_files(request: Request):
    """
    获取所有已上传文件列表
    """
    return FileService.list_files(request)

@app.post("/save")
async def save_text_to_file(request: Request, text_content: str = Form(...), filename: str = Form(...)):
    """
    将用户输入的文本保存为文件，返回预览地址
    文件将保存到 static/text_files/ 目录下
    参数:
    - text_content: 要保存的文本内容
    - filename: 文件名（可选扩展名，默认.txt）
    返回: 文件的预览地址（可在浏览器中直接查看）
    """
    return FileService.save_text_to_file(request, text_content, filename)

# ==================== 应用启动 ====================

def is_running_as_exe() -> bool:
    """
    检查是否作为exe运行
    
    Returns:
        bool: 如果是打包后的exe文件返回True，否则返回False
    """
    import sys
    return getattr(sys, 'frozen', False)

if __name__ == "__main__":
    import uvicorn
    
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

