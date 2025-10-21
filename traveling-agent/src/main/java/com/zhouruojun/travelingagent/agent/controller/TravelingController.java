package com.zhouruojun.travelingagent.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.dto.AgentChatRequest;
import com.zhouruojun.travelingagent.agent.core.TravelingControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 旅游智能体控制器
 * 提供旅游相关的API接口
 */
@RestController
@RequestMapping("/api/traveling")
@Slf4j
public class TravelingController {

    @Autowired
    private TravelingControllerCore controllerCore;

    @PostMapping("/chat")
    public String chat(@RequestBody AgentChatRequest request) {
        log.info("Travel planning chat request: {}", JSONObject.toJSON(request));
        try {
            return controllerCore.processStreamReturnStr(request);
        } catch (Exception e) {
            log.error("Error processing travel planning request", e);
            return "错误: " + e.getMessage();
        }
    }

    @GetMapping("/capabilities")
    public String getCapabilities() {
        return """
        ✈️ 旅游智能体能力清单:
        
        **旅游信息收集能力**:
        - 目的地信息收集
        - 景点信息查询
        - 交通信息获取
        - 住宿信息搜索
        - 美食推荐收集
        
        **行程规划能力**:
        - 多目的地行程规划
        - 时间安排优化
        - 路线规划
        - 预算估算
        - 个性化推荐
        
        **预订执行能力**:
        - 机票预订
        - 酒店预订
        - 景点门票预订
        - 交通票务预订
        - 预订状态跟踪
        
        **旅游建议**:
        - 最佳旅游时间建议
        - 当地文化介绍
        - 安全注意事项
        - 必备物品清单
        - 应急联系方式
        
        **工具调用方式**:
        - 通过ExecuteTool节点调用
        - 支持ToolSpecification注入
        - 基于LangGraph4j工作流
        """;
    }

    @GetMapping("/status")
    public String getStatus() {
        try {
            String ollamaStatus = controllerCore.checkOllamaStatus();
            String graphStatus = JSONObject.toJSONString(controllerCore.getGraphStatus());
            
            return String.format("""
                📊 旅游智能体状态:
                
                **Ollama服务**: %s
                
                **图状态**: %s
                
                **活跃会话**: %d
                """, 
                ollamaStatus, 
                graphStatus,
                controllerCore.getActiveSessionCount()
            );
        } catch (Exception e) {
            log.error("Error getting status", e);
            return "获取状态失败: " + e.getMessage();
        }
    }

    @GetMapping("/models")
    public String getModels() {
        try {
            return controllerCore.getAvailableModels();
        } catch (Exception e) {
            log.error("Error getting models", e);
            return "获取模型列表失败: " + e.getMessage();
        }
    }

    @GetMapping("/sessions")
    public String getSessions() {
        try {
            return JSONObject.toJSONString(controllerCore.getActiveSessions());
        } catch (Exception e) {
            log.error("Error getting sessions", e);
            return "获取会话列表失败: " + e.getMessage();
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public String clearSession(@PathVariable String sessionId) {
        try {
            controllerCore.clearSessionHistory(sessionId);
            return "会话 " + sessionId + " 已清除";
        } catch (Exception e) {
            log.error("Error clearing session", e);
            return "清除会话失败: " + e.getMessage();
        }
    }

    @PostMapping("/test")
    public String testToolCall() {
        try {
            AgentChatRequest testRequest = new AgentChatRequest();
            testRequest.setSessionId("test-session-" + System.currentTimeMillis());
            testRequest.setChat("请帮我制定一个完整的旅游计划");
            
            return controllerCore.processStreamReturnStr(testRequest);
        } catch (Exception e) {
            log.error("Error testing tool call", e);
            return "测试工具调用失败: " + e.getMessage();
        }
    }

    @PostMapping("/upload-travel-plan")
    public String uploadTravelPlan(@RequestParam("files") MultipartFile[] files) {
        log.info("Uploading travel plan files: {} files", files.length);
        
        try {
            List<String> uploadedFiles = new ArrayList<>();
            String uploadDir = "./temp/travel-plans";
            
            // 创建上传目录
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                
                // 验证文件类型
                String contentType = file.getContentType();
                if (!isValidFileType(contentType)) {
                    return "不支持的文件类型: " + contentType + "。支持的类型: PDF, DOC, DOCX, TXT";
                }
                
                // 验证文件大小 (10MB)
                if (file.getSize() > 10 * 1024 * 1024) {
                    return "文件大小超过10MB限制: " + file.getOriginalFilename();
                }
                
                // 保存文件
                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(file.getInputStream(), filePath);
                
                uploadedFiles.add(file.getOriginalFilename());
                log.info("File uploaded successfully: {}", fileName);
            }
            
            if (uploadedFiles.isEmpty()) {
                return "没有有效的文件被上传";
            }
            
            return String.format("""
                📄 文件上传成功！
                
                **已上传文件**:
                %s
                
                **文件处理说明**:
                - 文件已保存到临时目录
                - 智能体将分析文件内容
                - 您可以在对话中引用这些文件
                
                **下一步**:
                请告诉我您希望如何处理这些文件，或者直接开始您的旅游规划对话。
                """, String.join("\n- ", uploadedFiles));
                
        } catch (IOException e) {
            log.error("Error uploading files", e);
            return "文件上传失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error during file upload", e);
            return "文件上传过程中发生错误: " + e.getMessage();
        }
    }
    
    private boolean isValidFileType(String contentType) {
        return contentType != null && (
            contentType.equals("application/pdf") ||
            contentType.equals("application/msword") ||
            contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
            contentType.equals("text/plain")
        );
    }

}

