package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.chat.service.OcrService;
import com.github.app.dify.knowledgebase.service.DocumentParserService;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
/**
 * 文档解析服务
 * 对于图片文件，使用OCR服务进行识别
 * 对于其他文档，使用Apache Tika进行解析
 */
@Service
public class DocumentParserServiceImpl implements DocumentParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentParserServiceImpl.class);
    
    private final Tika tika;
    
    @Autowired(required = false)
    private OcrService ocrService;
    
    public DocumentParserServiceImpl() {
        this.tika = new Tika();
    }
    
    /**
     * 解析文档，提取纯文本内容
     * 对于图片文件，使用OCR服务进行识别
     * 对于其他文档，使用Apache Tika进行解析
     */
    @Override
    public String parseDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        // 检查是否为图片文件或PDF文件
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        boolean isImage = contentType != null && contentType.startsWith("image/");
        boolean isPdf = contentType != null && contentType.equals("application/pdf") ||
                        (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
        boolean isWordDoc = fileName != null && 
                           (fileName.toLowerCase().endsWith(".docx") || 
                            fileName.toLowerCase().endsWith(".doc"));
        
        if (isImage || isPdf) {
            // 图片文件或PDF文件，使用OCR识别
            if (ocrService != null) {
                try {
                    if (isPdf) {
                        logger.info("检测到PDF文件，使用OCR识别 - 文件名: {}", fileName);
                    } else {
                        logger.info("检测到图片文件，使用OCR识别 - 文件名: {}", fileName);
                    }
                    String ocrText = ocrService.recognizeImage(file);
                    if (ocrText != null && !ocrText.trim().isEmpty()) {
                        logger.info("OCR识别成功 - 文件名: {}, 识别文本长度: {}", fileName, ocrText.length());
                        return ocrText.trim();
                    } else {
                        logger.warn("OCR识别结果为空 - 文件名: {}", fileName);
                        // PDF OCR失败时，尝试使用Tika解析（可能包含文本层）
                        if (isPdf) {
                            logger.info("PDF OCR结果为空，尝试使用Tika解析文本层 - 文件名: {}", fileName);
                        } else {
                            return "";
                        }
                    }
                } catch (Exception e) {
                    logger.error("OCR识别失败 - 文件名: {}", fileName, e);
                    // OCR失败时，尝试使用Tika解析（可能包含一些元数据或文本层）
                    if (isPdf) {
                        logger.info("PDF OCR失败，尝试使用Tika解析文本层 - 文件名: {}", fileName);
                    } else {
                        logger.info("OCR失败，尝试使用Tika解析 - 文件名: {}", fileName);
                    }
                }
            } else {
                logger.warn("OCR服务不可用，尝试使用Tika解析 - 文件名: {}", fileName);
            }
        }
        
        // Word文档：提取图片并进行OCR，然后与文本内容合并
        if (isWordDoc && ocrService != null) {
            try {
                logger.info("检测到Word文档，提取图片并进行OCR识别 - 文件名: {}", fileName);
                List<String> ocrTexts = extractAndOcrImagesFromWord(file);
                
                // 使用Tika提取文本内容
                String textContent;
                try (InputStream inputStream = file.getInputStream()) {
                    textContent = tika.parseToString(inputStream);
                }
                
                // 合并文本内容和OCR结果
                StringBuilder fullContent = new StringBuilder();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    fullContent.append(textContent.trim());
                }
                
                if (!ocrTexts.isEmpty()) {
                    if (fullContent.length() > 0) {
                        fullContent.append("\n\n--- 文档中的图片OCR识别结果 ---\n");
                    }
                    for (int i = 0; i < ocrTexts.size(); i++) {
                        if (i > 0) {
                            fullContent.append("\n\n");
                        }
                        fullContent.append("【图片 ").append(i + 1).append("】\n");
                        fullContent.append(ocrTexts.get(i));
                    }
                }
                
                String result = fullContent.toString().trim();
                logger.info("Word文档解析完成 - 文件名: {}, 文本长度: {}, 图片OCR数量: {}", 
                           fileName, result.length(), ocrTexts.size());
                return result;
                
            } catch (Exception e) {
                logger.error("Word文档图片OCR处理失败，回退到Tika解析 - 文件名: {}", fileName, e);
                // 失败时回退到Tika解析
            }
        }
        
        // 非图片文件或OCR不可用时，使用Tika解析
        try (InputStream inputStream = file.getInputStream()) {
            String content = tika.parseToString(inputStream);
            logger.info("文档解析成功 - 文件名: {}, 内容长度: {}", file.getOriginalFilename(), content.length());
            return content != null ? content.trim() : "";
        } catch (IOException | TikaException e) {
            logger.error("文档解析失败 - 文件名: {}", file.getOriginalFilename(), e);
            throw new BusinessException("文档解析失败", ErrorCode.DATA_VALIDATION_FAILED, e);
        }
    }
    
    /**
     * 解析文档输入流，提取纯文本内容
     * 注意：此方法无法直接判断文件类型，因此默认使用Tika解析
     * 如果需要OCR识别，请使用parseDocument(MultipartFile)方法
     */
    @Override
    public String parseDocument(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        
        // 根据文件名判断是否为图片
        if (fileName != null && (fileName.toLowerCase().endsWith(".png") || 
            fileName.toLowerCase().endsWith(".jpg") || 
            fileName.toLowerCase().endsWith(".jpeg") || 
            fileName.toLowerCase().endsWith(".gif"))) {
            logger.warn("检测到图片文件，但无法使用OCR（缺少MultipartFile），使用Tika解析 - 文件名: {}", fileName);
            logger.warn("建议：对于图片文件，请使用parseDocument(MultipartFile)方法以启用OCR识别");
        }
        
        try {
            String content = tika.parseToString(inputStream);
            logger.info("文档解析成功 - 文件名: {}, 内容长度: {}", fileName, content.length());
            return content != null ? content.trim() : "";
        } catch (IOException | TikaException e) {
            logger.error("文档解析失败 - 文件名: {}", fileName, e);
            throw new BusinessException("文档解析失败", ErrorCode.DATA_VALIDATION_FAILED, e);
        }
    }
    
    /**
     * 从Word文档中提取图片并进行OCR识别
     * 
     * @param file Word文档文件
     * @return OCR识别结果列表
     * @throws Exception 处理失败时抛出异常
     */
    private List<String> extractAndOcrImagesFromWord(MultipartFile file) throws Exception {
        List<String> ocrTexts = new ArrayList<>();
        String fileName = file.getOriginalFilename();
        
        if (fileName == null) {
            return ocrTexts;
        }
        
        boolean isDocx = fileName.toLowerCase().endsWith(".docx");
        boolean isDoc = fileName.toLowerCase().endsWith(".doc");
        
        try (InputStream inputStream = file.getInputStream()) {
            if (isDocx) {
                // 处理 .docx 文件
                try (XWPFDocument document = new XWPFDocument(inputStream)) {
                    List<XWPFPictureData> pictures = document.getAllPictures();
                    logger.info("从Word文档中提取到 {} 张图片", pictures.size());
                    
                    for (int i = 0; i < pictures.size(); i++) {
                        XWPFPictureData picture = pictures.get(i);
                        byte[] imageData = picture.getData();
                        // 使用 getPictureTypeEnum() 获取 PictureType 枚举，然后获取 MIME 类型
                        PictureType pictureType = picture.getPictureTypeEnum();
                        String mimeType = (pictureType != null) ? pictureType.getContentType() : "image/jpeg";
                        
                        // 创建 final 变量供内部类使用
                        final int imageIndex = i + 1;
                        final String finalMimeType = mimeType;
                        final byte[] finalImageData = imageData;
                        
                        // 创建临时MultipartFile用于OCR
                        MultipartFile imageFile = new MultipartFile() {
                            @Override
                            public String getName() {
                                return "image_" + imageIndex;
                            }
                            
                            @Override
                            public String getOriginalFilename() {
                                return "image_" + imageIndex + "." + getExtensionFromMimeType(finalMimeType);
                            }
                            
                            @Override
                            public String getContentType() {
                                return finalMimeType;
                            }
                            
                            @Override
                            public boolean isEmpty() {
                                return finalImageData == null || finalImageData.length == 0;
                            }
                            
                            @Override
                            public long getSize() {
                                return finalImageData != null ? finalImageData.length : 0;
                            }
                            
                            @Override
                            public byte[] getBytes() throws IOException {
                                return finalImageData;
                            }
                            
                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(finalImageData);
                            }
                            
                            @Override
                            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                                java.nio.file.Files.write(dest.toPath(), finalImageData);
                            }
                        };
                        
                        try {
                            String ocrText = ocrService.recognizeImage(imageFile);
                            if (ocrText != null && !ocrText.trim().isEmpty()) {
                                ocrTexts.add(ocrText.trim());
                                logger.info("Word文档图片 {} OCR识别成功，文本长度: {}", imageIndex, ocrText.length());
                            } else {
                                logger.warn("Word文档图片 {} OCR识别结果为空", imageIndex);
                            }
                        } catch (Exception e) {
                            logger.error("Word文档图片 {} OCR识别失败", imageIndex, e);
                        }
                    }
                }
            } else if (isDoc) {
                // 处理 .doc 文件（旧格式）
                try (HWPFDocument document = new HWPFDocument(inputStream)) {
                    List<Picture> pictures = document.getPicturesTable().getAllPictures();
                    logger.info("从Word文档中提取到 {} 张图片", pictures.size());
                    
                    for (int i = 0; i < pictures.size(); i++) {
                        Picture picture = pictures.get(i);
                        byte[] imageData = picture.getContent();
                        String mimeType = picture.getMimeType();
                        
                        // 创建 final 变量供内部类使用
                        final int imageIndex = i + 1;
                        final String finalMimeType = mimeType;
                        final byte[] finalImageData = imageData;
                        
                        // 创建临时MultipartFile用于OCR
                        MultipartFile imageFile = new MultipartFile() {
                            @Override
                            public String getName() {
                                return "image_" + imageIndex;
                            }
                            
                            @Override
                            public String getOriginalFilename() {
                                return "image_" + imageIndex + "." + getExtensionFromMimeType(finalMimeType);
                            }
                            
                            @Override
                            public String getContentType() {
                                return finalMimeType;
                            }
                            
                            @Override
                            public boolean isEmpty() {
                                return finalImageData == null || finalImageData.length == 0;
                            }
                            
                            @Override
                            public long getSize() {
                                return finalImageData != null ? finalImageData.length : 0;
                            }
                            
                            @Override
                            public byte[] getBytes() throws IOException {
                                return finalImageData;
                            }
                            
                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(finalImageData);
                            }
                            
                            @Override
                            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                                java.nio.file.Files.write(dest.toPath(), finalImageData);
                            }
                        };
                        
                        try {
                            String ocrText = ocrService.recognizeImage(imageFile);
                            if (ocrText != null && !ocrText.trim().isEmpty()) {
                                ocrTexts.add(ocrText.trim());
                                logger.info("Word文档图片 {} OCR识别成功，文本长度: {}", imageIndex, ocrText.length());
                            } else {
                                logger.warn("Word文档图片 {} OCR识别结果为空", imageIndex);
                            }
                        } catch (Exception e) {
                            logger.error("Word文档图片 {} OCR识别失败", imageIndex, e);
                        }
                    }
                }
            }
        }
        
        return ocrTexts;
    }
    
    /**
     * 根据MIME类型获取文件扩展名
     */
    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return "jpg";
        }
        switch (mimeType.toLowerCase()) {
            case "image/png":
                return "png";
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/gif":
                return "gif";
            case "image/bmp":
                return "bmp";
            default:
                return "jpg";
        }
    }
    
    /**
     * 检测文档类型
     */
    @Override
    public String detectContentType(MultipartFile file) {
        try {
            return tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            logger.warn("无法检测文档类型 - 文件名: {}", file.getOriginalFilename(), e);
            return "application/octet-stream";
        }
    }
}
