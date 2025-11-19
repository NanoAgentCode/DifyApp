package com.github.app.dify.config;

import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Configuration
public class DatabaseConfig implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public void run(String... args) throws Exception {
        testDatabaseConnection();
    }
    
    /**
     * 测试数据库连接
     */
    private void testDatabaseConnection() {
        try {
            Connection connection = dataSource.getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            
            logger.info("==========================================");
            logger.info("数据库连接成功！");
            logger.info("数据库产品名称: {}", metaData.getDatabaseProductName());
            logger.info("数据库产品版本: {}", metaData.getDatabaseProductVersion());
            logger.info("驱动名称: {}", metaData.getDriverName());
            logger.info("驱动版本: {}", metaData.getDriverVersion());
            logger.info("数据库URL: {}", metaData.getURL());
            logger.info("用户名: {}", metaData.getUserName());
            logger.info("==========================================");
            
            // 测试查询
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            logger.info("PostgreSQL版本信息: {}", version);
            
            connection.close();
        } catch (Exception e) {
            logger.error("数据库连接失败！", e);
            throw new RuntimeException("无法连接到数据库: " + e.getMessage(), e);
        }
    }
}

