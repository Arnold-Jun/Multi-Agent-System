package com.zhouruojun.jobsearchagent.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.zhouruojun.jobsearchagent.model.ResumeData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 简历渲染服务
 * 负责将简历数据渲染为PDF文件
 */
@Slf4j
@Service
public class ResumeRenderService {
    
    private final TemplateEngine templateEngine;
    
    public ResumeRenderService() {
        this.templateEngine = createTemplateEngine();
    }
    
    /**
     * 创建Thymeleaf模板引擎
     */
    private TemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
    
    /**
     * 渲染简历为PDF文件
     * 
     * @param resumeData 简历数据
     * @param templateName 模板名称
     * @param outputPath 输出文件路径
     * @return 生成的PDF文件路径
     * @throws Exception 渲染异常
     */
    public String renderToPdf(ResumeData resumeData, String templateName, String outputPath) throws Exception {
        log.info("开始渲染简历PDF: template={}, output={}", templateName, outputPath);
        
        // 1. 准备模板数据
        Map<String, Object> templateData = prepareTemplateData(resumeData);
        
        // 2. 渲染HTML
        String html = renderHtml(templateName, templateData);
        
        // 3. HTML转PDF
        String pdfPath = convertHtmlToPdf(html, outputPath);
        
        log.info("简历PDF渲染完成: {}", pdfPath);
        return pdfPath;
    }
    
    /**
     * 准备模板数据
     */
    private Map<String, Object> prepareTemplateData(ResumeData resumeData) {
        Map<String, Object> data = new HashMap<>();
        
        // 基本信息
        data.put("name", resumeData.getName() != null ? resumeData.getName() : "");
        data.put("title", resumeData.getTitle() != null ? resumeData.getTitle() : "");
        data.put("email", resumeData.getEmail() != null ? resumeData.getEmail() : "");
        data.put("phone", resumeData.getPhone() != null ? resumeData.getPhone() : "");
        data.put("location", resumeData.getLocation() != null ? resumeData.getLocation() : "");
        data.put("summary", resumeData.getSummary() != null ? resumeData.getSummary() : "");
        
        // 技能
        data.put("skills", resumeData.getSkills() != null ? resumeData.getSkills() : java.util.Collections.emptyList());
        
        // 工作经历
        data.put("experiences", resumeData.getExperiences() != null ? resumeData.getExperiences() : java.util.Collections.emptyList());
        
        // 教育经历
        data.put("education", resumeData.getEducation() != null ? resumeData.getEducation() : java.util.Collections.emptyList());
        
        // 项目经验
        data.put("projects", resumeData.getProjects() != null ? resumeData.getProjects() : java.util.Collections.emptyList());
        
        // 认证证书
        data.put("certifications", resumeData.getCertifications() != null ? resumeData.getCertifications() : java.util.Collections.emptyList());
        
        // 语言能力
        data.put("languages", resumeData.getLanguages() != null ? resumeData.getLanguages() : java.util.Collections.emptyList());
        
        // 附加信息
        data.put("additionalInfo", resumeData.getAdditionalInfo() != null ? resumeData.getAdditionalInfo() : "");
        
        return data;
    }
    
    /**
     * 渲染HTML
     */
    private String renderHtml(String templateName, Map<String, Object> data) {
        Context context = new Context();
        context.setVariables(data);
        return templateEngine.process(templateName, context);
    }
    
    /**
     * HTML转PDF
     */
    private String convertHtmlToPdf(String html, String outputPath) throws Exception {
        // 确保输出目录存在
        Path outputDir = Paths.get(outputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.usePdfAConformance(null); // 不强制PDF/A
            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.LTR);
            
            // 添加中文字体支持
            try {
                builder.useFont(() -> getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.otf"), 
                              "NotoSansSC", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            } catch (Exception e) {
                log.warn("无法加载中文字体，使用默认字体: {}", e.getMessage());
            }
            
            builder.withHtmlContent(html, null);
            builder.toStream(fos);
            builder.run();
        }
        
        return outputPath;
    }
    
    /**
     * 生成简历预览HTML
     * 
     * @param resumeData 简历数据
     * @param templateName 模板名称
     * @return 预览HTML内容
     */
    public String generatePreview(ResumeData resumeData, String templateName) {
        log.info("生成简历预览: template={}", templateName);
        
        Map<String, Object> templateData = prepareTemplateData(resumeData);
        return renderHtml(templateName, templateData);
    }
    
    /**
     * 获取可用的模板列表
     * 
     * @return 模板列表
     */
    public Map<String, String> getAvailableTemplates() {
        Map<String, String> templates = new HashMap<>();
        templates.put("resume", "默认简历模板");
        templates.put("technical", "技术类简历模板");
        templates.put("management", "管理类简历模板");
        templates.put("creative", "创意类简历模板");
        return templates;
    }
    
    /**
     * 验证模板是否存在
     * 
     * @param templateName 模板名称
     * @return 是否存在
     */
    public boolean isTemplateExists(String templateName) {
        try {
            String templatePath = "templates/" + templateName + ".html";
            return getClass().getClassLoader().getResource(templatePath) != null;
        } catch (Exception e) {
            log.warn("检查模板存在性失败: {}", e.getMessage());
            return false;
        }
    }
}
