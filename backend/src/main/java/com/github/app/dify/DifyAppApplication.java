package com.github.app.dify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * DifyApp 主应用类
 * 
 * 模块化结构：
 * - auth: 认证模块（登录、注册、JWT）
 * - permission: 权限管理模块（可见性控制）
 * - chat: AI应用与对话模块
 * - knowledgebase: 知识库模块
 * - documentreader: 文档解读模块
 * - system: 系统配置模块
 * - statistics: 数据统计模块
 * - mcp: MCP服务集成模块（浏览器搜索、时间服务等）
 * - model: 模型配置模块（问答模型、向量化模型配置管理）
 * - datasource: 数据源管理模块（数据源配置、连接管理、表结构管理）
 * - common: 公共组件模块（工具类、异常、响应格式）
 */
@SpringBootApplication(exclude = {
    MongoAutoConfiguration.class,
    ElasticsearchRepositoriesAutoConfiguration.class,
    ReactiveElasticsearchRepositoriesAutoConfiguration.class
})
@EnableAsync
// Spring Boot 默认会扫描主应用类所在包及其所有子包
// 由于主应用类在 com.github.app.dify 包下，会自动扫描所有子模块
// 如果需要显式指定，可以只指定根包：
// @ComponentScan(basePackages = "com.github.app.dify")
// @EntityScan(basePackages = "com.github.app.dify")
// @EnableJpaRepositories(basePackages = "com.github.app.dify")
public class DifyAppApplication {

    public static void main(String[] args) {
        // 设置系统属性，解决Windows控制台中文乱码问题
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("user.language", "zh");
        System.setProperty("user.country", "CN");

        SpringApplication.run(DifyAppApplication.class, args);
    }

}