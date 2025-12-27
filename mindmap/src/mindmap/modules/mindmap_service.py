"""
思维导图服务模块
"""
import os
import subprocess
import shutil
import time
from pathlib import Path
from fastapi import Request, HTTPException
from fastapi.responses import FileResponse
from ..config import MARKDOWN_DIR, STATIC_HTML_DIR


class MindmapService:
    """思维导图服务类"""
    
    # CDN链接替换映射
    CDN_REPLACEMENTS = {
        'https://cdn.jsdelivr.net/npm/d3@7.9.0/dist': '../htmljs',
        'https://cdn.jsdelivr.net/npm/markmap-toolbar@0.18.10/dist': '../htmljs',
        'https://cdn.jsdelivr.net/npm/markmap-view@0.18.10/dist/browser/index.js': '../htmljs/index2.js',
    }
    
    @staticmethod
    def create_directories():
        """创建必要的目录"""
        os.makedirs(MARKDOWN_DIR, exist_ok=True)
        os.makedirs(STATIC_HTML_DIR, exist_ok=True)
    
    @staticmethod
    def check_markmap_available() -> bool:
        """检查markmap命令是否可用"""
        markmap_path = shutil.which('markmap')
        return markmap_path is not None
    
    @staticmethod
    def generate_filename() -> str:
        """生成基于时间戳的文件名"""
        return str(int(time.time()))
    
    @staticmethod
    def _execute_markmap_command(md_file_path: Path, html_file_name: str) -> subprocess.CompletedProcess:
        """
        执行markmap命令生成HTML文件
        
        Args:
            md_file_path: Markdown文件路径
            html_file_name: 输出的HTML文件名
            
        Returns:
            subprocess.CompletedProcess: 命令执行结果
            
        Raises:
            HTTPException: 当markmap命令执行失败时
        """
        markdown_cmd = f"markmap {md_file_path} --output {MARKDOWN_DIR / html_file_name} --no-open"
        
        # Windows环境使用PowerShell
        if os.name == 'nt':
            markdown_cmd = f"powershell -Command {markdown_cmd}"
        
        result = subprocess.run(
            markdown_cmd,
            check=False,
            text=True,
            shell=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True
        )
        
        if result.returncode != 0:
            raise subprocess.CalledProcessError(
                result.returncode,
                result.args,
                output=result.stdout,
                stderr=result.stderr
            )
        
        return result
    
    @staticmethod
    def _replace_cdn_links(html_content: str) -> str:
        """
        替换HTML内容中的CDN链接为本地路径
        
        Args:
            html_content: 原始HTML内容
            
        Returns:
            str: 替换后的HTML内容
        """
        for cdn_url, local_path in MindmapService.CDN_REPLACEMENTS.items():
            html_content = html_content.replace(cdn_url, local_path)
        return html_content
    
    @staticmethod
    def _process_html_file(html_file_path: Path) -> None:
        """
        处理HTML文件：替换CDN链接为本地路径
        
        Args:
            html_file_path: HTML文件路径
        """
        # 读取HTML文件内容
        with open(html_file_path, 'r', encoding='utf-8') as f:
            html_content = f.read()
        
        # 替换CDN链接为本地路径
        html_content = MindmapService._replace_cdn_links(html_content)
        
        # 写回文件
        with open(html_file_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
    
    @staticmethod
    async def _generate_mindmap(request: Request, content: str, replace_cdn: bool = False) -> str:
        """
        生成思维导图的核心方法
        
        Args:
            request: FastAPI请求对象
            content: Markdown内容
            replace_cdn: 是否替换CDN链接为本地路径
            
        Returns:
            str: 预览URL
            
        Raises:
            HTTPException: 当生成失败时
        """
        try:
            # 创建目录
            MindmapService.create_directories()
            
            # 检查markmap是否可用
            if not MindmapService.check_markmap_available():
                raise HTTPException(
                    status_code=500,
                    detail="Error: markmap command not found. Please install markmap-cli:\n"
                           "1. Install Node.js (if not installed)\n"
                           "2. Run: npm install -g markmap-cli\n"
                           "3. Verify: markmap --version\n"
                           "4. Make sure markmap is in your system PATH"
                )
            
            # 生成文件名
            time_name = MindmapService.generate_filename()
            md_file_name = f"{time_name}.md"
            html_file_name = f"{time_name}.html"
            
            # 保存Markdown文件
            md_file_path = MARKDOWN_DIR / md_file_name
            with open(md_file_path, "w", encoding='utf-8') as f:
                f.write(content)
            
            # 执行markmap命令
            MindmapService._execute_markmap_command(md_file_path, html_file_name)
            
            # 移动HTML文件到static/html目录
            source_path = MARKDOWN_DIR / html_file_name
            target_path = STATIC_HTML_DIR / html_file_name
            os.replace(str(source_path), str(target_path))
            
            # 如果需要，处理HTML文件（替换CDN链接为本地路径）
            if replace_cdn:
                MindmapService._process_html_file(target_path)
            
            # 返回预览链接
            base_url = str(request.base_url)
            preview_url = f"{base_url}html/{html_file_name}"
            
            return preview_url
            
        except subprocess.CalledProcessError as e:
            error_msg = f"Error generating HTML file: {e.output}\n{e.stderr}"
            raise HTTPException(status_code=500, detail=error_msg)
        except HTTPException:
            raise
        except Exception as e:
            error_msg = f"Unexpected error: {str(e)}"
            raise HTTPException(status_code=500, detail=error_msg)
    
    @staticmethod
    async def process_markdown(request: Request, content: str) -> str:
        """
        处理Markdown内容，生成思维导图（不替换CDN链接）
        
        Args:
            request: FastAPI请求对象
            content: Markdown内容
            
        Returns:
            str: 预览URL
        """
        return await MindmapService._generate_mindmap(request, content, replace_cdn=False)

    @staticmethod
    async def process_markdown_replace(request: Request, content: str) -> str:
        """
        处理Markdown内容，生成思维导图（替换CDN链接为本地路径）
        
        Args:
            request: FastAPI请求对象
            content: Markdown内容
            
        Returns:
            str: 预览URL
        """
        return await MindmapService._generate_mindmap(request, content, replace_cdn=True)

    @staticmethod
    def get_html_file(filename: str):
        """获取HTML文件"""
        file_path = STATIC_HTML_DIR / filename
        if not file_path.exists():
            raise HTTPException(status_code=404, detail="文件不存在")
        return FileResponse(str(file_path))
