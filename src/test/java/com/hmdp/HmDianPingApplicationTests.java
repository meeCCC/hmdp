package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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


}
