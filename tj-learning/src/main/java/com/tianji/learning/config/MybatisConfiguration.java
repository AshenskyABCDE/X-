package com.tianji.learning.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.learning.utils.TableInfoContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MybatisConfiguration {

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {
        // 创建一个DynamicTableNameInnerInterceptor实例
        DynamicTableNameInnerInterceptor interceptor = new DynamicTableNameInnerInterceptor();

        // 设置TableNameHandler，用来替换points_board表名称
        // 替换方式，就是从TableInfoContext中读取保存好的动态表名
        interceptor.setTableNameHandler((sql, tableName) -> {
            if ("points_board".equals(tableName)) {
                System.out.println("Dynamic Table Name: " + TableInfoContext.getInfo());
                return TableInfoContext.getInfo();
            }
            return tableName; // 如果不是points_board表，返回原始表名
        });

        return interceptor;
    }
}