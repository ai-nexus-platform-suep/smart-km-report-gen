package com.km;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@MapperScan("com.km.repository")
public class KmApplication {

    public static void main(String[] args) {
        SpringApplication.run(KmApplication.class, args);
    }
}
