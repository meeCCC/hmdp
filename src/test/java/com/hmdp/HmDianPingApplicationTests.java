package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;


@SpringBootTest
class HmDianPingApplicationTests {

    public static void main(String[] args) {

    }

    @Test
    public void getTime(){
        LocalDateTime now = LocalDateTime.now();
        System.out.println(now);

        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);

        System.out.println(nowSeconds);

    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testredis(){
        String stockKey = "seckill:stock:" + 3 ;
        String s = stringRedisTemplate.opsForValue().get(stockKey);
        System.out.println(s);
    }


}
