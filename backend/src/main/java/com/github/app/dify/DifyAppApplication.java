package com.github.app.dify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * DifyApp 主应用类
 * 
 * 模块化结构：
 * - auth: 认证模块（登录、注册、JWT）
 * - permission: 权限管理模块（可见性控制）
 * - chat: AI应用与对话模块
 * - knowledgebase: 知识库模块
 * - documentreader: 文档解读模块
 * - system: 系统配置与数据源模块
 * - statistics: 数据统计模块
 * - common: 公共组件模块（工具类、异常、响应格式）
 */
@SpringBootApplication(exclude = {
    MongoAutoConfiguration.class,
    ElasticsearchRepositoriesAutoConfiguration.class,
    ReactiveElasticsearchRepositoriesAutoConfiguration.class
})
@EnableAsync
@ComponentScan(basePackages = {
        "com.github.app.dify.auth",
        "com.github.app.dify.permission",
        "com.github.app.dify.chat",
        "com.github.app.dify.knowledgebase",
        "com.github.app.dify.system",
        "com.github.app.dify.statistics",
        "com.github.app.dify.documentreader",
        "com.github.app.dify.common",
        "com.github.app.dify"
})
@EntityScan(basePackages = {
        "com.github.app.dify.auth.domain",
        "com.github.app.dify.permission.domain",
        "com.github.app.dify.chat.domain",
        "com.github.app.dify.knowledgebase.domain",
        "com.github.app.dify.system.domain",
        "com.github.app.dify.documentreader.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.github.app.dify.auth.repository",
        "com.github.app.dify.permission.repository",
        "com.github.app.dify.chat.repository",
        "com.github.app.dify.knowledgebase.repository",
        "com.github.app.dify.system.repository",
        "com.github.app.dify.documentreader.repository"
})
public class DifyAppApplication {

    public static void main(String[] args) {
        // 设置系统属性，解决Windows控制台中文乱码问题
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("user.language", "zh");
        System.setProperty("user.country", "CN");
        
        // 设置标准输出和错误输出的编码（Java 8兼容方式）
        PrintStream utf8Out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream utf8Err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.setOut(utf8Out);
        System.setErr(utf8Err);

        SpringApplication.run(DifyAppApplication.class, args);
    }

}