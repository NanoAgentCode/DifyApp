package com.github.app.dify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import java.io.PrintStream;

/**
 * DifyApp 主应用类
 * 
 * 模块化结构：
 * - appauth: 认证授权模块
 * - appaichat: AI应用与对话模块
 * - appknowledgebase: 知识库模块
 * - appsystemdata: 系统配置与数据源模块
 * - appcommon: 公共组件模块
 */
@SpringBootApplication(exclude = {MongoAutoConfiguration.class})
@EnableAsync
@ComponentScan(basePackages = {
    "com.github.app.dify.appauth",
    "com.github.app.dify.appaichat",
    "com.github.app.dify.appknowledgebase",
    "com.github.app.dify.appsystemdata",
    "com.github.app.dify.appcommon",
    "com.github.app.dify"  // 保留根包以扫描主应用类和其他可能遗留的组件
})
@EntityScan(basePackages = {
    "com.github.app.dify.appauth.domain",
    "com.github.app.dify.appaichat.domain",
    "com.github.app.dify.appknowledgebase.domain",
    "com.github.app.dify.appsystemdata.domain"
})
@EnableJpaRepositories(basePackages = {
    "com.github.app.dify.appauth.repository",
    "com.github.app.dify.appaichat.repository",
    "com.github.app.dify.appknowledgebase.repository",
    "com.github.app.dify.appsystemdata.repository"
})
public class DifyAppApplication {

    public static void main(String[] args) {
        // 设置系统属性，解决Windows控制台中文乱码问题
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("user.language", "zh");
        System.setProperty("user.country", "CN");
        
        // 设置标准输出和错误输出的编码（Java 8兼容方式）
        try {
            PrintStream utf8Out = new PrintStream(System.out, true, "UTF-8");
            PrintStream utf8Err = new PrintStream(System.err, true, "UTF-8");
            System.setOut(utf8Out);
            System.setErr(utf8Err);
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8应该总是支持的，但如果失败也不影响程序运行
        }
        
        SpringApplication.run(DifyAppApplication.class, args);
    }

}