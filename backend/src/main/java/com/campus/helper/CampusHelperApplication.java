package com.campus.helper;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.campus.helper.mapper")
@EnableScheduling
public class CampusHelperApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusHelperApplication.class, args);
    }
}
