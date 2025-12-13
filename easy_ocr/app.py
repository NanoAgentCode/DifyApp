#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
EasyOCR API 服务
提供 HTTP API 接口用于图片 OCR 识别
"""

import os
import io
import base64
import logging
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from PIL import Image
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
CORS(app)  # 允许跨域请求

# 初始化 EasyOCR 阅读器（支持中文和英文）
# 首次加载会下载模型，可能需要一些时间
logger.info("正在初始化 EasyOCR 阅读器...")
try:
    reader = easyocr.Reader(['ch_sim', 'en'], gpu=False)  # 中文简体和英文，使用 CPU
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
        'reader_ready': reader is not None
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
        return jsonify({
            'success': False,
            'error': 'OCR 阅读器未初始化'
        }), 500
    
    try:
        images = []  # 支持多页PDF
        page_count = 0
        is_pdf = False
        
        # 方式1: 通过文件上传
        if 'file' in request.files:
            file = request.files['file']
            if file.filename == '':
                return jsonify({
                    'success': False,
                    'error': '未选择文件'
                }), 400
            
            file_bytes = file.read()
            file_ext = os.path.splitext(file.filename)[1].lower() if file.filename else ''
            
            # 检查是否为PDF文件
            if file_ext == '.pdf' or file_bytes[:4] == b'%PDF':
                if not PDF_SUPPORT:
                    return jsonify({
                        'success': False,
                        'error': 'PDF支持未启用，请安装PyMuPDF库'
                    }), 400
                
                is_pdf = True
                logger.info(f"检测到PDF文件: {file.filename}")
                
                # 使用PyMuPDF将PDF转换为图片
                pdf_doc = fitz.open(stream=file_bytes, filetype="pdf")
                page_count = len(pdf_doc)
                
                for page_num in range(page_count):
                    page = pdf_doc[page_num]
                    # 将PDF页面渲染为图片（DPI=200，可根据需要调整）
                    mat = fitz.Matrix(200/72, 200/72)  # 200 DPI
                    pix = page.get_pixmap(matrix=mat)
                    # 转换为PIL Image
                    img_data = pix.tobytes("ppm")
                    img = Image.open(io.BytesIO(img_data))
                    if img.mode != 'RGB':
                        img = img.convert('RGB')
                    images.append(img)
                    logger.info(f"PDF第 {page_num + 1}/{page_count} 页已转换为图片")
                
                pdf_doc.close()
            else:
                # 普通图片文件
                image = Image.open(io.BytesIO(file_bytes))
                if image.mode != 'RGB':
                    image = image.convert('RGB')
                images.append(image)
                page_count = 1
                logger.info(f"接收到图片文件: {file.filename}")
        
        # 方式2: 通过 base64 编码
        elif 'image' in request.json:
            image_data = request.json['image']
            # 移除 data:image/xxx;base64, 前缀（如果有）
            if ',' in image_data:
                image_data = image_data.split(',')[1]
            
            image_bytes = base64.b64decode(image_data)
            image = Image.open(io.BytesIO(image_bytes))
            if image.mode != 'RGB':
                image = image.convert('RGB')
            images.append(image)
            page_count = 1
            logger.info("接收到 base64 编码的图片")
        
        else:
            return jsonify({
                'success': False,
                'error': '请提供图片文件、PDF文件或 base64 编码的图片'
            }), 400
        
        if not images:
            return jsonify({
                'success': False,
                'error': '未能解析文件'
            }), 400
        
        # 对每页/每张图片执行OCR识别
        all_texts = []
        all_details = []
        
        for page_idx, image in enumerate(images):
            # 将PIL Image转换为numpy array（EasyOCR需要numpy array格式）
            image_array = np.array(image)
            
            # 执行 OCR 识别
            if is_pdf:
                logger.info(f"开始OCR识别PDF第 {page_idx + 1}/{page_count} 页...")
            else:
                logger.info("开始 OCR 识别...")
            
            results = reader.readtext(image_array)
            
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
        
    except Exception as e:
        logger.error(f"OCR 识别失败: {e}", exc_info=True)
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

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
        return jsonify({
            'success': False,
            'error': 'OCR 阅读器未初始化'
        }), 500
    
    try:
        data = request.json
        if 'images' not in data or not isinstance(data['images'], list):
            return jsonify({
                'success': False,
                'error': '请提供图片数组'
            }), 400
        
        results = []
        for idx, image_data in enumerate(data['images']):
            try:
                # 移除 data:image/xxx;base64, 前缀（如果有）
                if ',' in image_data:
                    image_data = image_data.split(',')[1]
                
                image_bytes = base64.b64decode(image_data)
                image = Image.open(io.BytesIO(image_bytes))
                
                if image.mode != 'RGB':
                    image = image.convert('RGB')
                
                # 将PIL Image转换为numpy array（EasyOCR需要numpy array格式）
                image_array = np.array(image)
                
                # 执行 OCR
                ocr_results = reader.readtext(image_array)
                
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
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

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
    port = int(os.environ.get('PORT', 8000))
    host = os.environ.get('HOST', '0.0.0.0')
    logger.info(f"启动 EasyOCR API 服务，监听 {host}:{port}")
    app.run(host=host, port=port, debug=False)
