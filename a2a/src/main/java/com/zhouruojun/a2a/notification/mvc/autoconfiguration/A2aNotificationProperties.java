package com.zhouruojun.a2a.notification.mvc.autoconfiguration;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "a2a.notification", ignoreInvalidFields = true)
@Configuration(proxyBeanMethods = false)
public class A2aNotificationProperties {
    private String endpoint;
    private List<String> jwksUrls;
}
