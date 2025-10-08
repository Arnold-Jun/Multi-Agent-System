package com.zhouruojun.jobsearchagent.tools.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.jobsearchagent.agent.state.BaseAgentState;
import com.zhouruojun.jobsearchagent.model.ResumeData;
import com.zhouruojun.jobsearchagent.service.ResumeRenderService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 简历分析优化工具集合
 * 合并了简历分析和简历优化的所有功能
 * 职责：简历解析、信息提取、匹配度计算、简历优化、格式调整
 */
@Slf4j
@Component
public class ResumeAnalysisOptimizationTool {
    
    @Autowired
    private ResumeRenderService resumeRenderService;
    
    private final Tika tika = new Tika();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 文件处理工具 ====================

    /**
     * 解析简历文件，支持PDF、Word、TXT等格式，提取文本内容
     */
    @Tool("解析简历文件，支持PDF、Word、TXT等格式，提取文本内容并进行结构化处理")
    public String parseResumeFile(String filePath, String fileType, BaseAgentState state) {
        log.info("开始解析简历文件: {} (类型: {})", filePath, fileType);
        
        try {
            // 检查文件是否存在
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return String.format("❌ 文件不存在: %s", filePath);
            }
            
            // 使用Tika解析文件内容
            String extractedText = tika.parseToString(path);
            
            // 获取文件信息
            String actualMimeType = tika.detect(path);
            
            log.info("简历文件解析完成: {}, 类型: {}", filePath, actualMimeType);
            
            return String.format("""
                ✅ 简历文件解析成功
                
                📁 文件信息:
                - 文件路径: %s
                - 文件类型: %s (实际: %s)
                - 提取内容长度: %d 字符
                
                📝 提取的文本内容:
                %s
                
                💡 下一步建议: 使用 analyzeResumeContent 工具分析简历内容
                """, 
                filePath, fileType, actualMimeType, extractedText.length(),extractedText);
                
        } catch (IOException | TikaException e) {
            log.error("解析简历文件失败: {}", e.getMessage(), e);
            return String.format("❌ 解析简历文件失败: %s", e.getMessage());
        }
    }
    
    /**
     * 保存简历数据到文件系统
     */
    @Tool("保存简历数据到文件系统，支持JSON格式。resumeDataJson参数必须是符合ResumeData格式的JSON字符串")
    public String saveResumeData(String resumeDataJson, String fileName, BaseAgentState state) {
        log.info("保存简历数据: {}", fileName);
        
        try {
            // 创建输出目录
            Path outputDir = Paths.get("./temp/resumes");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fullFileName = String.format("%s_%s.json", fileName, timestamp);
            Path outputPath = outputDir.resolve(fullFileName);
            
            // 保存文件
            Files.write(outputPath, resumeDataJson.getBytes("UTF-8"));
            
            log.info("简历数据保存成功: {}", outputPath);
            
            return String.format("""
                ✅ 简历数据保存成功
                
                📁 保存信息:
                - 文件名: %s
                - 保存路径: %s
                - 文件大小: %d bytes
                - 保存时间: %s
                
                💡 下一步建议: 使用 renderResumePDF 工具渲染PDF简历
                """, 
                fullFileName, outputPath.toString(), Files.size(outputPath), 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                
        } catch (IOException e) {
            log.error("保存简历数据失败: {}", e.getMessage(), e);
            return String.format("❌ 保存简历数据失败: %s", e.getMessage());
        }
    }
    
    // ==================== 模板处理工具 ====================
    
    /**
     * 获取可用的简历模板列表
     */
    @Tool("获取可用的简历模板列表，包括模板名称和描述")
    public String getAvailableTemplates(String category, BaseAgentState state) {
        log.info("获取可用模板列表: category={}", category);
        
        Map<String, String> templates = resumeRenderService.getAvailableTemplates();
        
        StringBuilder result = new StringBuilder();
        result.append("📋 可用简历模板列表\n\n");
        
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            String templateId = entry.getKey();
            String description = entry.getValue();
            boolean exists = resumeRenderService.isTemplateExists(templateId);
            
            result.append(String.format("""
                🎨 模板: %s
                - 描述: %s
                - 状态: %s
                - 模板ID: %s
                
                """, 
                templateId, description, exists ? "✅ 可用" : "❌ 不可用", templateId));
        }
        
        result.append("💡 使用建议:\n");
        result.append("- resume: 通用简历模板，适合大多数岗位\n");
        result.append("- technical: 技术类简历模板，突出技术技能\n");
        
        return result.toString();
    }

    /**
     * 获取简历JSON数据模板
     */
    @Tool("获取简历JSON数据模板，包含完整的字段结构和示例数据，用于指导LLM生成正确的简历数据格式")
    public String getResumeJsonTemplate(BaseAgentState state) {
        log.info("获取简历JSON模板");
        
        String jsonTemplate = """
            {
              "name": "张三",
              "title": "高级Java开发工程师",
              "email": "zhangsan@example.com",
              "phone": "138****8888",
              "location": "北京市朝阳区",
              "summary": "5年Java开发经验，精通Spring Boot、微服务架构，有丰富的电商平台开发经验。",
              "skills": [
                "Java", "Spring Boot", "Spring Cloud", "MySQL", "Redis", 
                "Docker", "Kubernetes", "微服务架构", "分布式系统"
              ],
              "experiences": [
                {
                  "company": "阿里巴巴",
                  "role": "高级Java开发工程师",
                  "range": "2021.03 - 至今",
                  "location": "杭州",
                  "bullets": [
                    "负责核心业务系统架构设计与开发，使用Spring Boot + MySQL技术栈",
                    "优化系统性能，将响应时间从500ms降低到200ms，提升60%用户体验",
                    "参与微服务架构改造，服务拆分后系统稳定性提升至99.9%",
                    "带领3人团队完成重要项目，按时交付并获得业务方好评"
                  ]
                },
                {
                  "company": "腾讯",
                  "role": "Java开发工程师",
                  "range": "2019.06 - 2021.02",
                  "location": "深圳",
                  "bullets": [
                    "参与微信支付系统开发，处理日均千万级交易请求",
                    "使用Redis缓存优化查询性能，QPS提升40%",
                    "参与代码review，提升团队代码质量，bug率降低30%"
                  ]
                }
              ],
              "education": [
                {
                  "school": "北京理工大学",
                  "degree": "计算机科学与技术",
                  "major": "软件工程",
                  "range": "2014.09 - 2018.06",
                  "location": "北京",
                  "gpa": "3.8/4.0",
                  "honors": "校级奖学金2次，优秀毕业生"
                }
              ],
              "projects": [
                {
                  "name": "电商平台系统",
                  "description": "大型电商平台后端系统，支持日活用户10万+",
                  "role": "项目负责人",
                  "range": "2020.01 - 2020.12",
                  "technologies": ["Spring Boot", "MySQL", "Redis", "Vue.js"],
                  "achievements": [
                    "负责系统架构设计，支持日活用户10万+，交易额千万级",
                    "实现分布式锁、消息队列等核心功能，保证系统高可用",
                    "项目获得公司技术创新奖，代码被多个团队复用"
                  ]
                }
              ],
              "certifications": [
                {
                  "name": "Oracle Java认证",
                  "issuer": "Oracle",
                  "date": "2020.06",
                  "credentialId": "OCP-123456"
                }
              ],
              "languages": [
                {
                  "language": "英语",
                  "proficiency": "流利",
                  "certification": "CET-6",
                  "score": "580分"
                }
              ],
              "additionalInfo": "GitHub: https://github.com/zhangsan\\n开源项目贡献者，技术博客作者"
            }
            """;
        
        return String.format("""
            📋 简历JSON数据模板
            
            **模板说明**：
            这是一个完整的简历JSON数据模板，包含了所有可能的字段和示例数据。
            请根据实际简历内容，替换相应的字段值。
            
            **字段说明**：
            - name: 姓名（必填）
            - title: 职位/求职意向（必填）
            - email: 邮箱地址（必填）
            - phone: 电话号码（必填）
            - location: 居住地址
            - summary: 个人简介/职业概述
            - skills: 技能列表（数组）
            - experiences: 工作经历（数组，包含company、role、range、location、bullets等字段）
            - education: 教育经历（数组，包含school、degree、major、range、location等字段）
            - projects: 项目经验（数组，包含name、description、role、range、technologies等字段）
            - certifications: 认证证书（数组，包含name、issuer、date等字段）
            - languages: 语言能力（数组，包含language、proficiency等字段）
            - additionalInfo: 附加信息
            
            **JSON模板**：
            ```json
            %s
            ```
            
            **使用说明**：
            1. 复制上述JSON模板
            2. 根据实际简历内容替换相应的字段值
            3. 确保JSON格式正确（注意引号、逗号、括号等）
            4. 必填字段：name、title、email、phone
            5. 可选字段：其他所有字段都可以为空或省略
            
            **注意事项**：
            - 数组字段（如skills、experiences）如果为空，请使用空数组 []
            - 字符串字段如果为空，请使用空字符串 "" 或省略该字段
            - 确保所有字符串都用双引号包围
            - 注意转义字符，如换行符使用 \\n
            
            💡 生成JSON后，建议使用 validateResumeData 工具验证数据格式
            """, jsonTemplate);
    }
    
    /**
     * 渲染简历为PDF文件
     */
    @Tool("渲染简历为PDF文件，使用指定的模板。resumeDataJson参数必须是符合ResumeData格式的JSON字符串，包含name、title、email、phone等字段，以及experiences、education、skills等数组字段")
    public String renderResumePDF(String resumeDataJson, String templateId, String outputPath, BaseAgentState state) {
        log.info("渲染简历PDF: template={}, output={}", templateId, outputPath);
        
        try {
            // 解析简历数据
            ResumeData resumeData = objectMapper.readValue(resumeDataJson, ResumeData.class);
            
            // 检查模板是否存在
            if (!resumeRenderService.isTemplateExists(templateId)) {
                return String.format("❌ 模板不存在: %s", templateId);
            }
            
            // 渲染PDF
            String pdfPath = resumeRenderService.renderToPdf(resumeData, templateId, outputPath);
            
            // 获取文件信息
            Path path = Paths.get(pdfPath);
            long fileSize = Files.size(path);
            
            log.info("简历PDF渲染完成: {}", pdfPath);
            
            return String.format("""
                ✅ 简历PDF渲染成功
                
                📄 渲染信息:
                - 模板: %s
                - 输出路径: %s
                - 文件大小: %d bytes
                - 渲染时间: %s
                
                📋 简历内容概览:
                - 姓名: %s
                - 职位: %s
                - 邮箱: %s
                - 电话: %s
                - 工作经历: %d 条
                - 教育经历: %d 条
                - 技能: %d 项
                
                💡 下一步建议: 使用 generateResumePreview 工具生成预览
                """, 
                templateId, pdfPath, fileSize, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                resumeData.getName() != null ? resumeData.getName() : "未填写",
                resumeData.getTitle() != null ? resumeData.getTitle() : "未填写",
                resumeData.getEmail() != null ? resumeData.getEmail() : "未填写",
                resumeData.getPhone() != null ? resumeData.getPhone() : "未填写",
                resumeData.getExperiences() != null ? resumeData.getExperiences().size() : 0,
                resumeData.getEducation() != null ? resumeData.getEducation().size() : 0,
                resumeData.getSkills() != null ? resumeData.getSkills().size() : 0);
            
        } catch (Exception e) {
            log.error("渲染简历PDF失败: {}", e.getMessage(), e);
            return String.format("❌ 渲染简历PDF失败: %s", e.getMessage());
        }
    }
    
    /**
     * 生成简历预览HTML
     */
    @Tool("生成简历预览HTML，用于查看渲染效果。resumeDataJson参数必须是符合ResumeData格式的JSON字符串")
    public String generateResumePreview(String resumeDataJson, String templateId, BaseAgentState state) {
        log.info("生成简历预览: template={}", templateId);
        
        try {
            // 解析简历数据
            ResumeData resumeData = objectMapper.readValue(resumeDataJson, ResumeData.class);
            
            // 检查模板是否存在
            if (!resumeRenderService.isTemplateExists(templateId)) {
                return String.format("❌ 模板不存在: %s", templateId);
            }
            
            // 生成预览HTML
            String previewHtml = resumeRenderService.generatePreview(resumeData, templateId);
            
            log.info("简历预览生成完成");
            
            return String.format("""
                ✅ 简历预览生成成功
                
                📋 预览信息:
                - 模板: %s
                - HTML长度: %d 字符
                - 生成时间: %s
                
                📄 预览内容 (前500字符):
                %s
                
                💡 下一步建议: 使用 renderResumePDF 工具生成最终PDF
                """, 
                templateId, previewHtml.length(), 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                previewHtml.length() > 500 ? previewHtml.substring(0, 500) + "..." : previewHtml);
                
        } catch (Exception e) {
            log.error("生成简历预览失败: {}", e.getMessage(), e);
            return String.format("❌ 生成简历预览失败: %s", e.getMessage());
        }
    }
    
    // ==================== 数据验证工具 ====================
    
    /**
     * 验证简历数据格式
     */
    @Tool("验证简历数据的完整性和格式。resumeDataJson参数必须是符合ResumeData格式的JSON字符串，会检查必填字段和数据结构")
    public String validateResumeData(String resumeDataJson, BaseAgentState state) {
        log.info("验证简历数据格式");
        
        try {
            // 解析简历数据
            ResumeData resumeData = objectMapper.readValue(resumeDataJson, ResumeData.class);
            
            java.util.List<String> errors = new java.util.ArrayList<>();
            java.util.List<String> warnings = new java.util.ArrayList<>();
            boolean isValid = true;
            
            // 验证必填字段
            if (resumeData.getName() == null || resumeData.getName().trim().isEmpty()) {
                errors.add("姓名不能为空");
                isValid = false;
            }
            
            if (resumeData.getEmail() == null || resumeData.getEmail().trim().isEmpty()) {
                errors.add("邮箱不能为空");
                isValid = false;
            }
            
            if (resumeData.getPhone() == null || resumeData.getPhone().trim().isEmpty()) {
                errors.add("电话不能为空");
                isValid = false;
            }
            
            // 验证可选字段
            if (resumeData.getExperiences() == null || resumeData.getExperiences().isEmpty()) {
                warnings.add("建议添加工作经历");
            }
            
            if (resumeData.getEducation() == null || resumeData.getEducation().isEmpty()) {
                warnings.add("建议添加教育经历");
            }
            
            if (resumeData.getSkills() == null || resumeData.getSkills().isEmpty()) {
                warnings.add("建议添加技能信息");
            }
            
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("📋 简历数据验证结果: %s\n\n", isValid ? "✅ 通过" : "❌ 失败"));
            
            if (!errors.isEmpty()) {
                result.append("❌ 错误信息:\n");
                for (String error : errors) {
                    result.append(String.format("- %s\n", error));
                }
                result.append("\n");
            }
            
            if (!warnings.isEmpty()) {
                result.append("⚠️ 警告信息:\n");
                for (String warning : warnings) {
                    result.append(String.format("- %s\n", warning));
                }
                result.append("\n");
            }
            
            result.append("📊 数据统计:\n");
            result.append(String.format("- 姓名: %s\n", resumeData.getName() != null ? "✅" : "❌"));
            result.append(String.format("- 邮箱: %s\n", resumeData.getEmail() != null ? "✅" : "❌"));
            result.append(String.format("- 电话: %s\n", resumeData.getPhone() != null ? "✅" : "❌"));
            result.append(String.format("- 工作经历: %d 条\n", resumeData.getExperiences() != null ? resumeData.getExperiences().size() : 0));
            result.append(String.format("- 教育经历: %d 条\n", resumeData.getEducation() != null ? resumeData.getEducation().size() : 0));
            result.append(String.format("- 技能: %d 项\n", resumeData.getSkills() != null ? resumeData.getSkills().size() : 0));
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("验证简历数据失败: {}", e.getMessage(), e);
            return String.format("❌ 验证简历数据失败: %s", e.getMessage());
        }
    }
    
    /**
     * 检查文件是否存在
     */
    @Tool("检查文件是否存在")
    public String checkFileExists(String filePath, BaseAgentState state) {
        log.info("检查文件是否存在: {}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            boolean exists = Files.exists(path);
            
            if (exists) {
                long size = Files.size(path);
                return String.format("""
                    ✅ 文件存在
                    
                    📁 文件信息:
                    - 路径: %s
                    - 大小: %d bytes
                    - 状态: 可访问
                    """, filePath, size);
            } else {
                return String.format("❌ 文件不存在: %s", filePath);
            }
            
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}", e.getMessage(), e);
            return String.format("❌ 检查文件失败: %s", e.getMessage());
        }
    }
}