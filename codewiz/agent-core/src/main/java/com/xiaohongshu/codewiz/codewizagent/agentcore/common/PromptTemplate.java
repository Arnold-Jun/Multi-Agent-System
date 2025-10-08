package com.xiaohongshu.codewiz.codewizagent.agentcore.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * <p>
 * 提示词模板管理器，从resources/META-INF/template目录读取模板
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 23:46
 */
@Slf4j
public class PromptTemplate {

    @Getter
    private static final PromptTemplate instance = new PromptTemplate();

    private Map<String, String> cache = new ConcurrentHashMap<>();
    private static final String TEMPLATE_PATH = "classpath:META-INF/template/*.md";
    private static final String DEFAULT_PROMPT = "You are a helpful assistant, You need use tool to do anything.";

    private PromptTemplate() {
        initializeTemplates();
    }

    /**
     * 初始化模板，从resources/META-INF/template目录读取所有.md文件
     */
    public void initializeTemplates() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(TEMPLATE_PATH);
            
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith(".md")) {
                    String templateKey = filename.substring(0, filename.lastIndexOf("."));
                    String templateContent = readResourceContent(resource);
                    cache.put(templateKey, templateContent);
                    log.info("Loaded template: {}", templateKey);
                }
            }
            
            // 如果未加载到任何模板，添加默认模板
            if (cache.isEmpty()) {
                log.warn("No templates found in META-INF/template directory, using default templates");
            }
        } catch (IOException e) {
            log.error("Failed to load templates from resources: {}", e.getMessage(), e);
        }
    }

    /**
     * 读取资源内容
     *
     * @param resource 资源
     * @return 资源内容
     * @throws IOException 如果读取失败
     */
    private String readResourceContent(Resource resource) throws IOException {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

    /**
     * 添加新模板
     *
     * @param key 模板键
     * @param value 模板内容
     */
    public void add(String key, String value) {
        cache.put(key, value);
    }

    /**
     * 获取模板内容
     *
     * @param key 模板键
     * @return 模板内容，如果不存在则返回默认提示词
     */
    public String get(String key) {
        return cache.getOrDefault(key, DEFAULT_PROMPT);
    }

    /**
     * 移除模板
     *
     * @param key 模板键
     */
    public void remove(String key) {
        cache.remove(key);
    }
    
    /**
     * 重新加载所有模板
     */
    public void reloadTemplates() {
        cache.clear();
        initializeTemplates();
    }
    
    /**
     * 获取所有模板键
     * 
     * @return 所有模板键的集合
     */
    public Set<String> getAllTemplateKeys() {
        return cache.keySet();
    }
}
