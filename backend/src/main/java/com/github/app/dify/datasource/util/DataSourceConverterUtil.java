package com.github.app.dify.datasource.util;

import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.resp.DataSourceResp;
import org.springframework.beans.BeanUtils;

/**
 * 数据源实体转换工具类
 * 提供数据源相关实体的转换方法
 */
public class DataSourceConverterUtil {
    
    /**
     * 将 DataSource 转换为 DataSourceResp
     * 注意：响应对象中不包含密码
     * 
     * @param dataSource 数据源实体
     * @return 数据源响应对象
     */
    public static DataSourceResp convertToResp(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        
        DataSourceResp resp = new DataSourceResp();
        BeanUtils.copyProperties(dataSource, resp);
        // 注意：响应对象中不包含密码
        return resp;
    }
}

