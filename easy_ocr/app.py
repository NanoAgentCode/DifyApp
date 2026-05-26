#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
EasyOCR API 服务
提供 HTTP API 接口用于图片 OCR 识别
"""

import os
import io
import base64
import binascii
import logging
import threading
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from PIL import Image, UnidentifiedImageError
from werkzeug.utils import secure_filename
from config import load_config
import easyocr
try:
    import fitz  # PyMuPDF
    PDF_SUPPORT = True
except ImportError:
    PDF_SUPPORT = False
    logger_temp = logging.getLogger(__name__)
    logger_temp.warning("PyMuPDF未安装，PDF OCR功能将不可用")

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

APP_CONFIG = load_config()
OCR_CONFIG = APP_CONFIG.ocr
SERVER_CONFIG = APP_CONFIG.server

SUPPORTED_IMAGE_EXTENSIONS = OCR_CONFIG.supported_image_extensions
SUPPORTED_EXTENSIONS = SUPPORTED_IMAGE_EXTENSIONS | {'.pdf'}
MAX_CONTENT_LENGTH = OCR_CONFIG.max_content_length
MAX_PDF_PAGES = OCR_CONFIG.max_pdf_pages
PDF_DPI = OCR_CONFIG.pdf_dpi
MAX_BATCH_SIZE = OCR_CONFIG.max_batch_size
MAX_IMAGE_PIXELS = OCR_CONFIG.max_image_pixels
OCR_GPU = OCR_CONFIG.gpu
OCR_LANGUAGES = OCR_CONFIG.languages
Image.MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS
ocr_lock = threading.Lock()

def convert_to_python_type(obj):
    """
    将 numpy 类型转换为 Python 原生类型，以便 JSON 序列化
    """
    if isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, (list, tuple)):
        return [convert_to_python_type(item) for item in obj]
    elif isinstance(obj, dict):
        return {key: convert_to_python_type(value) for key, value in obj.items()}
    else:
        return obj

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH
CORS(app, origins=SERVER_CONFIG.cors_origins)  # 允许跨域请求

def error_response(message, status_code=400):
    """统一错误响应格式。"""
    return jsonify({
        'success': False,
        'error': message
    }), status_code

def decode_base64_image(image_data):
    """解析并校验base64图片。"""
    if not image_data or not isinstance(image_data, str):
        raise ValueError('image必须是base64字符串')

    if ',' in image_data:
        image_data = image_data.split(',', 1)[1]

    try:
        image_bytes = base64.b64decode(image_data, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError('base64图片数据无效') from exc

    return load_image_from_bytes(image_bytes)

def load_image_from_bytes(image_bytes):
    """从字节加载图片并转换为RGB。"""
    try:
        image = Image.open(io.BytesIO(image_bytes))
        image.verify()
        image = Image.open(io.BytesIO(image_bytes))
    except (UnidentifiedImageError, OSError) as exc:
        raise ValueError('无法解析图片文件') from exc

    if image.mode != 'RGB':
        image = image.convert('RGB')
    if image.width * image.height > MAX_IMAGE_PIXELS:
        raise ValueError(f'图片像素过大，最多支持 {MAX_IMAGE_PIXELS} 像素')
    return image

def run_ocr(image):
    """执行OCR识别，串行化访问reader以降低并发下的模型风险。"""
    image_array = np.array(image)
    with ocr_lock:
        return reader.readtext(image_array)

def parse_uploaded_file(file):
    """解析上传文件，返回图片列表和PDF标记。"""
    original_filename = secure_filename(file.filename or '')
    if not original_filename:
        raise ValueError('未选择文件')

    file_ext = os.path.splitext(original_filename)[1].lower()
    if file_ext and file_ext not in SUPPORTED_EXTENSIONS:
        raise ValueError(f"不支持的文件类型。支持的类型: {', '.join(sorted(SUPPORTED_EXTENSIONS))}")

    file_bytes = file.read()
    if not file_bytes:
        raise ValueError('文件内容为空')

    is_pdf = file_ext == '.pdf' or file_bytes[:4] == b'%PDF'
    if is_pdf:
        if not PDF_SUPPORT:
            raise ValueError('PDF支持未启用，请安装PyMuPDF库')

        logger.info(f"检测到PDF文件: {original_filename}")
        images = convert_pdf_to_images(file_bytes)
        return images, True

    logger.info(f"接收到图片文件: {original_filename}")
    return [load_image_from_bytes(file_bytes)], False

def convert_pdf_to_images(file_bytes):
    """将PDF转换为图片列表。"""
    images = []
    pdf_doc = None
    try:
        pdf_doc = fitz.open(stream=file_bytes, filetype="pdf")
        page_count = len(pdf_doc)
        if page_count == 0:
            raise ValueError('PDF文件没有可识别页面')
        if page_count > MAX_PDF_PAGES:
            raise ValueError(f'PDF页数超过限制，最多支持 {MAX_PDF_PAGES} 页')

        for page_num in range(page_count):
            page = pdf_doc[page_num]
            mat = fitz.Matrix(PDF_DPI / 72, PDF_DPI / 72)
            pix = page.get_pixmap(matrix=mat)
            img_data = pix.tobytes("ppm")
            image = Image.open(io.BytesIO(img_data))
            if image.mode != 'RGB':
                image = image.convert('RGB')
            images.append(image)
            logger.info(f"PDF第 {page_num + 1}/{page_count} 页已转换为图片")
    finally:
        if pdf_doc is not None:
            pdf_doc.close()

    return images

# 初始化 EasyOCR 阅读器（支持中文和英文）
# 首次加载会下载模型，可能需要一些时间
logger.info("正在初始化 EasyOCR 阅读器...")
try:
    reader = easyocr.Reader(OCR_LANGUAGES, gpu=OCR_GPU)  # 默认中文简体和英文，使用 CPU
    logger.info("EasyOCR 阅读器初始化成功")
except Exception as e:
    logger.error(f"EasyOCR 初始化失败: {e}")
    reader = None

@app.route('/health', methods=['GET'])
def health():
    """健康检查接口"""
    return jsonify({
        'status': 'healthy',
        'service': 'EasyOCR',
        'reader_ready': reader is not None,
        'pdf_support': PDF_SUPPORT,
        'languages': OCR_LANGUAGES,
        'gpu': OCR_GPU,
        'limits': {
            'max_content_length': MAX_CONTENT_LENGTH,
            'max_pdf_pages': MAX_PDF_PAGES,
            'pdf_dpi': PDF_DPI,
            'max_batch_size': MAX_BATCH_SIZE,
            'max_image_pixels': MAX_IMAGE_PIXELS
        }
    }), 200

@app.route('/ocr', methods=['POST'])
def ocr():
    """
    OCR 识别接口
    支持图片和PDF文件
    
    请求格式（multipart/form-data）:
    - file: 图片文件或PDF文件
    
    或者（JSON）:
    - image: base64 编码的图片数据
    
    返回格式:
    {
        "success": true,
        "text": "识别出的文本内容",
        "details": [
            {
                "bbox": [[x1, y1], [x2, y2], [x3, y3], [x4, y4]],
                "text": "识别的文本",
                "confidence": 0.95
            }
        ],
        "page_count": 1  # PDF文件时返回页数
    }
    """
    if reader is None:
        return error_response('OCR 阅读器未初始化', 500)
    
    try:
        images = []  # 支持多页PDF
        page_count = 0
        is_pdf = False
        
        # 方式1: 通过文件上传
        if 'file' in request.files:
            file = request.files['file']
            images, is_pdf = parse_uploaded_file(file)
            page_count = len(images)
        
        # 方式2: 通过 base64 编码
        else:
            data = request.get_json(silent=True) or {}
            if 'image' not in data:
                return error_response('请提供图片文件、PDF文件或 base64 编码的图片', 400)

            images.append(decode_base64_image(data['image']))
            page_count = 1
            logger.info("接收到 base64 编码的图片")
        
        if not images:
            return error_response('未能解析文件', 400)
        
        # 对每页/每张图片执行OCR识别
        all_texts = []
        all_details = []
        
        for page_idx, image in enumerate(images):
            # 将PIL Image转换为numpy array（EasyOCR需要numpy array格式）
            # 执行 OCR 识别
            if is_pdf:
                logger.info(f"开始OCR识别PDF第 {page_idx + 1}/{page_count} 页...")
            else:
                logger.info("开始 OCR 识别...")
            
            results = run_ocr(image)
            
            # 提取文本和详细信息
            page_texts = []
            for (bbox, text, confidence) in results:
                page_texts.append(text)
                # 转换 bbox 为 Python 原生类型（处理 numpy int32 等类型）
                bbox_list = bbox.tolist() if hasattr(bbox, 'tolist') else bbox
                bbox_list = convert_to_python_type(bbox_list)
                all_details.append({
                    'page': page_idx + 1 if is_pdf else 1,
                    'bbox': bbox_list,
                    'text': text,
                    'confidence': float(confidence)
                })
            
            # 如果是PDF，在每页文本前添加页码标记
            if is_pdf and page_texts:
                all_texts.append(f"\n--- 第 {page_idx + 1} 页 ---\n")
                all_texts.append('\n'.join(page_texts))
            else:
                all_texts.extend(page_texts)
        
        # 合并所有文本
        full_text = '\n'.join(all_texts)
        
        if is_pdf:
            logger.info(f"PDF OCR识别完成，共 {page_count} 页，识别到 {len(all_details)} 个文本块")
        else:
            logger.info(f"OCR 识别完成，识别到 {len(all_details)} 个文本块")
        
        result = {
            'success': True,
            'text': full_text,
            'details': all_details,
            'text_count': len(all_details)
        }
        
        if is_pdf:
            result['page_count'] = page_count
        
        return jsonify(result), 200
        
    except ValueError as e:
        return error_response(str(e), 400)
    except Exception as e:
        logger.error(f"OCR 识别失败: {e}", exc_info=True)
        return error_response(str(e), 500)

@app.route('/ocr/batch', methods=['POST'])
def ocr_batch():
    """
    批量 OCR 识别接口
    
    请求格式（JSON）:
    {
        "images": [
            "base64_encoded_image1",
            "base64_encoded_image2"
        ]
    }
    
    返回格式:
    {
        "success": true,
        "results": [
            {
                "index": 0,
                "text": "识别出的文本",
                "details": [...]
            }
        ]
    }
    """
    if reader is None:
        return error_response('OCR 阅读器未初始化', 500)
    
    try:
        data = request.get_json(silent=True) or {}
        if 'images' not in data or not isinstance(data['images'], list):
            return error_response('请提供图片数组', 400)

        if len(data['images']) > MAX_BATCH_SIZE:
            return error_response(f'批量图片数量超过限制，最多支持 {MAX_BATCH_SIZE} 张', 400)
        
        results = []
        for idx, image_data in enumerate(data['images']):
            try:
                # 执行 OCR
                image = decode_base64_image(image_data)
                ocr_results = run_ocr(image)
                
                texts = []
                details = []
                for (bbox, text, confidence) in ocr_results:
                    texts.append(text)
                    # 转换 bbox 为 Python 原生类型（处理 numpy int32 等类型）
                    bbox_list = bbox.tolist() if hasattr(bbox, 'tolist') else bbox
                    bbox_list = convert_to_python_type(bbox_list)
                    details.append({
                        'bbox': bbox_list,
                        'text': text,
                        'confidence': float(confidence)
                    })
                
                results.append({
                    'index': idx,
                    'success': True,
                    'text': '\n'.join(texts),
                    'details': details,
                    'text_count': len(texts)
                })
                
            except Exception as e:
                logger.error(f"批量识别第 {idx} 张图片失败: {e}")
                results.append({
                    'index': idx,
                    'success': False,
                    'error': str(e)
                })
        
        return jsonify({
            'success': True,
            'results': results,
            'total': len(results)
        }), 200
        
    except Exception as e:
        logger.error(f"批量 OCR 识别失败: {e}", exc_info=True)
        return error_response(str(e), 500)

@app.errorhandler(413)
def request_entity_too_large(_error):
    """请求体过大。"""
    return error_response(f"请求体过大，最大允许 {MAX_CONTENT_LENGTH // (1024 * 1024)}MB", 413)

@app.route('/', methods=['GET'])
def index():
    """API 信息"""
    return jsonify({
        'service': 'EasyOCR API',
        'version': '1.0.0',
        'endpoints': {
            'GET /health': '健康检查',
            'POST /ocr': '单张图片 OCR 识别',
            'POST /ocr/batch': '批量图片 OCR 识别'
        },
        'reader_ready': reader is not None
    }), 200

if __name__ == '__main__':
    port = SERVER_CONFIG.port
    host = SERVER_CONFIG.host
    logger.info(f"启动 EasyOCR API 服务，监听 {host}:{port}")
    app.run(host=host, port=port, debug=SERVER_CONFIG.debug)
