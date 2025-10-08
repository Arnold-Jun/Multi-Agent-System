package com.example.agentcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>
 * 智能体核心应用程序
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/26 21:24
 */
@SpringBootApplication(scanBasePackages = { "com.example.agentcore" })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
