package com.xiaohongshu.codewiz.codewizagent.agentcore.common;

import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * <p>
 * 提示词模板读取与替换工具
 * </p>
 *
 * @author codewiz
 */
@Slf4j
public class PromptTemplateReader {

    @Getter
    private static final PromptTemplateReader instance = new PromptTemplateReader();

    private final Configuration freemarkerConfig;

    private PromptTemplateReader() {
        // 初始化FreeMarker配置
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_31);
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
        freemarkerConfig.setWrapUncheckedExceptions(true);
    }

    /**
     * 根据模板名称和参数生成提示词
     *
     * @param templateName 模板名称
     * @param params 替换参数
     * @return 处理后的提示词
     */
    public String getPrompt(String templateName, Map<String, Object> params) {
        String templateContent = PromptTemplate.getInstance().get(templateName);
        if (templateContent == null || templateContent.isEmpty()) {
            log.error("Template not found: {}", templateName);
            return "";
        }

        // 添加当前时间
        if (!params.containsKey("CURRENT_TIME")) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            params.put("CURRENT_TIME", dateFormat.format(new Date()));
        }

        return processTemplate(templateContent, params);
    }

    /**
     * 根据模板名称、团队成员和参数生成提示词
     *
     * @param templateName 模板名称
     * @param teamMembers 团队成员列表
     * @param params 替换参数
     * @return 处理后的提示词
     */
    public String getSupervisorPrompt(String templateName, List<String> teamMembers, Map<String, Object> params) {
        String templateContent = PromptTemplate.getInstance().get(templateName);
        if (templateContent == null || templateContent.isEmpty()) {
            log.error("Template not found: {}", templateName);
            return "";
        }

        // 添加当前时间
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        params.put("CURRENT_TIME", dateFormat.format(new Date()));
        
        // 添加团队成员
        params.put("TeamMembers", String.join(", ", teamMembers));
        params.put("TEAM_MEMBERS", teamMembers);

        return processTemplate(templateContent, params);
    }

    /**
     * 将AgentCard信息注入到模板中
     *
     * @param templateName 模板名称
     * @param agentCards 可用的AgentCard列表
     * @return 处理后的提示词
     */
    public String getSupervisorPromptWithAgentCards(String templateName,
                                                 List<AgentCard> agentCards) {
        String templateContent = PromptTemplate.getInstance().get(templateName);
        if (templateContent == null || templateContent.isEmpty()) {
            log.error("Template not found: {}", templateName);
            return "";
        }

        Map<String, Object> params = new HashMap<>();
        // 添加当前时间
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        params.put("CURRENT_TIME", dateFormat.format(new Date()));

        List<String> teamMembers = agentCards.stream().map(AgentCard::getName).toList();
        // 添加团队成员
        params.put("TeamMembers", String.join(", ", teamMembers));
        
        // 添加AgentCard
        params.put("AVAILABLE_AGENTS", agentCards);

        return processTemplate(templateContent, params);
    }

    /**
     * 使用FreeMarker处理模板内容
     *
     * @param templateContent 模板内容
     * @param params 替换参数
     * @return 处理后的内容
     */
    private String processTemplate(String templateContent, Map<String, Object> params) {
        try {
            Template template = new Template("dynamicTemplate", 
                                           new StringReader(templateContent), 
                                           freemarkerConfig);
            
            StringWriter out = new StringWriter();
            template.process(params, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            log.error("Error processing template: {}", e.getMessage(), e);
            return templateContent;
        }
    }
    
    /**
     * 使用简单字符串替换处理模板（备用方法，当FreeMarker出问题时使用）
     *
     * @param templateContent 模板内容
     * @param params 替换参数
     * @return 处理后的内容
     */
    private String simpleReplace(String templateContent, Map<String, Object> params) {
        String result = templateContent;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
