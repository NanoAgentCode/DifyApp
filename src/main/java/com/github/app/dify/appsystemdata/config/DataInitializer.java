package com.github.app.dify.appsystemdata.config;

import com.github.app.dify.appauth.domain.User;
import com.github.app.dify.appauth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Date;
/**
 * 数据初始化器
 * 在应用启动时创建默认管理员账户（如果不存在）
 */
@Component
public class DataInitializer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    @Autowired
    private UserRepository userRepository;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public void run(String... args) throws Exception {
        // 检查是否存在管理员账户
        if (!userRepository.existsByUsername("admin")) {
            logger.info("创建默认管理员账户...");
            
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123")); // 默认密码：admin123
            admin.setRole(1); // 管理员
            admin.setStatus(1); // 已激活
            admin.setCreateTime(new Date());
            admin.setUpdateTime(new Date());
            admin.setDeleted(0);
            
            userRepository.save(admin);
            
            logger.info("默认管理员账户创建成功 - 用户名: admin, 密码: admin123");
            logger.warn("请在生产环境中修改默认管理员密码！");
        } else {
            logger.info("管理员账户已存在，跳过初始化");
        }
    }
}

