"""
思维导图服务模块
"""
import os
import asyncio
import subprocess
import shutil
import time
import re
import uuid
from typing import Tuple
from pathlib import Path
from fastapi import Request, HTTPException
from fastapi.responses import FileResponse
from ..config import (
    MARKDOWN_DIR,
    STATIC_HTML_DIR,
    MAX_MARKDOWN_LENGTH,
    MAX_MARKDOWN_LENGTH_WARNING,
    MARKMAP_TIMEOUT_SECONDS,
)


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
        """生成并发安全的文件名"""
        timestamp = int(time.time() * 1000)
        return f"{timestamp}_{uuid.uuid4().hex[:8]}"

    @staticmethod
    def _resolve_html_path(filename: str) -> Path:
        """解析HTML文件路径，避免目录穿越。"""
        if not filename or Path(filename).name != filename or Path(filename).suffix.lower() != ".html":
            raise HTTPException(status_code=400, detail="无效的HTML文件名")

        base_dir = STATIC_HTML_DIR.resolve()
        file_path = (base_dir / filename).resolve()
        if file_path.parent != base_dir:
            raise HTTPException(status_code=400, detail="无效的HTML文件路径")
        return file_path
    
    @staticmethod
    def _truncate_markdown_intelligently(content: str, max_length: int) -> str:
        """
        智能截断Markdown内容，保留主要结构（标题层级）
        
        Args:
            content: 原始Markdown内容
            max_length: 最大长度（字符数）
            
        Returns:
            str: 截断后的Markdown内容
        """
        if len(content) <= max_length:
            return content
        
        # 按行分割
        lines = content.split('\n')
        truncated_lines = []
        current_length = 0
        warning_added = False
        
        # 首先，识别所有标题行及其级别
        header_lines = []
        for i, line in enumerate(lines):
            header_match = re.match(r'^(#{1,6})\s+(.+)$', line.strip())
            if header_match:
                header_level = len(header_match.group(1))
                header_lines.append((i, header_level, line))
        
        # 优先保留高级别标题（# 和 ##）
        important_headers = [h for h in header_lines if h[1] <= 2]
        
        for i, line in enumerate(lines):
            line_length = len(line) + 1  # +1 for newline
            
            # 检查是否是标题行
            header_match = re.match(r'^(#{1,6})\s+(.+)$', line.strip())
            if header_match:
                header_level = len(header_match.group(1))
                
                # 如果当前长度加上这行会超过限制
                if current_length + line_length > max_length:
                    # 如果这是第一级或第二级标题，尝试保留
                    if header_level <= 2:
                        # 检查是否还有空间保留这个重要标题
                        remaining = max_length - current_length
                        if remaining > 50:  # 至少保留50字符空间
                            truncated_lines.append(line)
                            current_length += line_length
                            if not warning_added:
                                truncated_lines.append("\n\n> **注意：内容已截断，仅保留主要结构**\n")
                                warning_added = True
                            break
                    else:
                        # 低级别标题，直接截断
                        if not warning_added:
                            truncated_lines.append("\n\n> **注意：内容已截断，仅保留主要结构**\n")
                            warning_added = True
                        break
                else:
                    truncated_lines.append(line)
                    current_length += line_length
            else:
                # 非标题行
                if current_length + line_length > max_length:
                    # 尝试在段落边界截断
                    if line.strip() == '':
                        # 空行，可以保留
                        truncated_lines.append(line)
                        current_length += line_length
                        if not warning_added:
                            truncated_lines.append("\n\n> **注意：内容已截断，仅保留主要结构**\n")
                            warning_added = True
                        break
                    elif line.strip().startswith('-') or line.strip().startswith('*'):
                        # 列表项，如果空间足够就保留，否则截断
                        if current_length + line_length <= max_length:
                            truncated_lines.append(line)
                            current_length += line_length
                        else:
                            if not warning_added:
                                truncated_lines.append("\n\n> **注意：内容已截断，仅保留主要结构**\n")
                                warning_added = True
                            break
                    else:
                        # 普通文本行，尝试在句子边界截断
                        remaining = max_length - current_length
                        if remaining > 30:
                            # 截断当前行，保留一些内容
                            truncated_line = line[:remaining-30].rstrip() + "..."
                            truncated_lines.append(truncated_line)
                            current_length += len(truncated_line) + 1
                        if not warning_added:
                            truncated_lines.append("\n\n> **注意：内容已截断，仅保留主要结构**\n")
                            warning_added = True
                        break
                else:
                    truncated_lines.append(line)
                    current_length += line_length
        
        result = '\n'.join(truncated_lines)
        
        # 确保结果不超过最大长度
        if len(result) > max_length:
            # 如果还是超过，直接截断到最大长度
            result = result[:max_length].rsplit('\n', 1)[0]  # 在最后一个换行符处截断
            if not result.endswith("\n\n> **注意：内容已截断，仅保留主要结构**\n"):
                result += "\n\n> **注意：内容已截断**\n"
        
        return result
    
    @staticmethod
    def _preprocess_markdown_content(content: str) -> Tuple[str, bool]:
        """
        预处理Markdown内容，检查长度并进行智能截断
        
        Args:
            content: 原始Markdown内容
            
        Returns:
            tuple[str, bool]: (处理后的内容, 是否进行了截断)
        """
        original_length = len(content)
        was_truncated = False
        
        # 检查是否超过警告长度
        if original_length > MAX_MARKDOWN_LENGTH_WARNING:
            # 记录警告日志
            print(f"警告：Markdown内容较长 ({original_length} 字符)，建议精简")
        
        # 如果超过最大长度，进行智能截断
        if original_length > MAX_MARKDOWN_LENGTH:
            print(f"信息：Markdown内容超过最大长度 ({original_length} > {MAX_MARKDOWN_LENGTH})，进行智能截断")
            content = MindmapService._truncate_markdown_intelligently(content, MAX_MARKDOWN_LENGTH)
            was_truncated = True
            print(f"信息：截断后长度: {len(content)} 字符")
        
        return content, was_truncated
    
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
        markmap_path = shutil.which('markmap')
        if not markmap_path:
            raise HTTPException(status_code=500, detail="markmap command not found")

        markdown_cmd = [
            markmap_path,
            str(md_file_path),
            "--output",
            str(MARKDOWN_DIR / html_file_name),
            "--no-open",
        ]
        
        result = subprocess.run(
            markdown_cmd,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=MARKMAP_TIMEOUT_SECONDS,
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
            if not content or not content.strip():
                raise HTTPException(status_code=400, detail="Markdown内容不能为空")

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
            
            # 预处理Markdown内容（检查长度并智能截断）
            processed_content, was_truncated = MindmapService._preprocess_markdown_content(content)
            if was_truncated:
                print(f"警告：Markdown内容已截断，原始长度: {len(content)}, 截断后长度: {len(processed_content)}")
            
            # 生成文件名
            time_name = MindmapService.generate_filename()
            md_file_name = f"{time_name}.md"
            html_file_name = f"{time_name}.html"
            
            # 保存Markdown文件
            md_file_path = MARKDOWN_DIR / md_file_name
            with open(md_file_path, "w", encoding='utf-8') as f:
                f.write(processed_content)
            
            # 执行markmap命令
            await asyncio.to_thread(MindmapService._execute_markmap_command, md_file_path, html_file_name)
            
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
        except subprocess.TimeoutExpired:
            raise HTTPException(status_code=504, detail=f"markmap生成超时（>{MARKMAP_TIMEOUT_SECONDS}秒）")
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
        file_path = MindmapService._resolve_html_path(filename)
        if not file_path.exists() or not file_path.is_file():
            raise HTTPException(status_code=404, detail="文件不存在")
        return FileResponse(str(file_path))
