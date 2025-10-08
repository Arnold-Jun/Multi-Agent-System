package com.xiaohongshu.codewiz.codewizagent.cragent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/26 21:24
 */
@SpringBootApplication(scanBasePackages = { "com.xiaohongshu.infra", "com.xiaohongshu.codewiz.codewizagent.cragent" })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
