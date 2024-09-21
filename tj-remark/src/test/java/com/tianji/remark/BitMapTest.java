package com.tianji.remark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(classes = RemarkApplication.class)
public class BitMapTest {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Test
    public void test() {
        stringRedisTemplate.opsForValue().setBit("test116",4,true);
        System.out.println();
    }

}
