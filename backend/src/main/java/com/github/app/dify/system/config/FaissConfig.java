package com.github.app.dify.system.config;

// 注意：VectorDatabase和VectorDatabaseRepository将在knowledge-base模块迁移时更新
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
/**
 * FAISS配置类
 * 用于配置FAISS向量存储的基础路径
 * 仅从数据库读取配置
 */
@Configuration
@DependsOn("vectorDatabaseRepository")
public class FaissConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(FaissConfig.class);
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    /**
     * 实际使用的基础路径（从数据库读取）
     */
    private String actualBasePath;
    
    /**
     * 初始化配置（从数据库读取或使用默认值）
     */
    @PostConstruct
    public void init() {
        loadConfigFromDatabase();
    }
    
    /**
     * 从数据库加载配置
     */
    private void loadConfigFromDatabase() {
        if (vectorDatabaseRepository == null) {
            logger.debug("VectorDatabaseRepository未注入，无法加载配置");
            return;
        }
        
        try {
            // 先尝试查找默认的启用配置
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType("faiss");
            if (defaultConfig.isPresent()) {
                VectorDatabase config = defaultConfig.get();
                actualBasePath = config.getUrl(); // FAISS使用url字段存储路径
                logger.info("从数据库加载FAISS配置（默认） - 名称: {}, 路径: {}", config.getName(), actualBasePath);
                return;
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType("faiss");
            if (!enabledConfigs.isEmpty()) {
                VectorDatabase config = enabledConfigs.get(0);
                actualBasePath = config.getUrl(); // FAISS使用url字段存储路径
                logger.info("从数据库加载FAISS配置（第一个启用） - 名称: {}, 路径: {}", config.getName(), actualBasePath);
                return;
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType("faiss");
            if (!allConfigs.isEmpty()) {
                VectorDatabase config = allConfigs.get(0);
                actualBasePath = config.getUrl(); // FAISS使用url字段存储路径
                logger.info("从数据库加载FAISS配置（任意） - 名称: {}, 路径: {}, 启用状态: {}", 
                        config.getName(), actualBasePath, config.getEnabled());
                return;
            }
            
            logger.warn("数据库中没有FAISS配置");
        } catch (Exception e) {
            logger.error("从数据库加载FAISS配置失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 重新加载配置（当数据库配置更新时调用）
     */
    public void reload() {
        loadConfigFromDatabase();
    }
    
    /**
     * 获取实际使用的基础路径（转换为绝对路径）
     */
    public String getBasePath() {
        String path = actualBasePath;
        if (path == null) {
            logger.warn("FAISS基础路径未配置，使用默认路径");
            path = "./data/faiss";
        }
        // 如果是相对路径，转换为绝对路径（基于项目根目录）
        if (path.startsWith("./") || (!path.startsWith("/") && !path.matches("^[A-Za-z]:.*"))) {
            // 获取项目根目录（backend目录的父目录）
            String projectRoot = System.getProperty("user.dir");
            // 如果当前工作目录是backend，需要向上一级
            if (projectRoot.endsWith("backend")) {
                projectRoot = new File(projectRoot).getParent();
            }
            // 移除相对路径前缀
            String cleanPath = path.replaceFirst("^\\./", "");
            path = Paths.get(projectRoot, cleanPath).toAbsolutePath().normalize().toString();
            logger.debug("转换相对路径为绝对路径: {} -> {}", actualBasePath != null ? actualBasePath : "./data/faiss", path);
        }
        return path;
    }
    
    /**
     * @description 获取知识库的FAISS索引目录路径
     * @param knowledgeBaseId 知识库ID
     * @return 索引目录路径
     */
    public String getKnowledgeBasePath(Long knowledgeBaseId) {
        String path = Paths.get(getBasePath(), "kb_" + knowledgeBaseId).toString();
        // 确保目录存在
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.debug("创建FAISS索引目录: {}", path);
            } else {
                logger.warn("创建FAISS索引目录失败: {}", path);
            }
        }
        return path;
    }
    
    /**
     * 获取知识库的FAISS索引文件路径
     * @param knowledgeBaseId 知识库ID
     * @return 索引文件路径
     */
    public String getIndexFilePath(Long knowledgeBaseId) {
        return Paths.get(getKnowledgeBasePath(knowledgeBaseId), "index.faiss").toString();
    }
    
    /**
     * 获取知识库的元数据文件路径
     * @param knowledgeBaseId 知识库ID
     * @return 元数据文件路径
     */
    public String getMetadataFilePath(Long knowledgeBaseId) {
        return Paths.get(getKnowledgeBasePath(knowledgeBaseId), "metadata.json").toString();
    }
}

