package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication   // 扫描本包及子包所有 @Component/@Configuration
public class NettySpringBootApplication {
    public static void main(String[] args) {
        SpringApplication.run(NettySpringBootApplication.class, args);
    }
}