package com.witliving;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.witliving.mapper")
@SpringBootApplication
public class WitLivingApplication {

    public static void main(String[] args) {
        SpringApplication.run(WitLivingApplication.class, args);
    }

}
