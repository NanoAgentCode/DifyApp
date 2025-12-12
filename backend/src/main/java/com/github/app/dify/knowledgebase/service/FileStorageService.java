package com.github.app.dify.knowledgebase.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
/**
 * 文件存储服务接口
 */
public interface FileStorageService {
    
    /**
     * 上传文件
     * @param file 文件
     * @param filePath 文件路径（在存储中的路径）
     * @return 文件访问URL
     */
    String uploadFile(MultipartFile file, String filePath);
    
    /**
     * 删除文件
     * @param filePath 文件路径
     */
    void deleteFile(String filePath);
    
    /**
     * 获取文件访问URL
     * @param filePath 文件路径
     * @return 文件访问URL
     */
    String getFileUrl(String filePath);
    
    /**
     * 下载文件
     * @param filePath 文件路径
     * @return 文件输入流
     */
    InputStream downloadFile(String filePath);
    
    /**
     * 检查文件是否存在
     * @param filePath 文件路径
     * @return 是否存在
     */
    boolean fileExists(String filePath);
}