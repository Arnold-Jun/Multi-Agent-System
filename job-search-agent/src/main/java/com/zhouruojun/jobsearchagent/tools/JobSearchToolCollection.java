package com.zhouruojun.jobsearchagent.tools;

import com.zhouruojun.jobsearchagent.agent.state.BaseAgentState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 求职工具集合管理器
 * 使用官方LangGraph4j方式管理工具规范和执行
 */
@Component
public class JobSearchToolCollection {
    private static final Logger log = Logger.getLogger(JobSearchToolCollection.class.getName());
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Getter
    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    
    // 分组的工具规范
    @Getter
    private final List<ToolSpecification> jobInfoCollectionTools = new ArrayList<>();
    @Getter
    private final List<ToolSpecification> resumeAnalysisOptimizationTools = new ArrayList<>();
    @Getter
    private final List<ToolSpecification> jobSearchExecutionTools = new ArrayList<>();
    @Getter
    private final List<ToolSpecification> plannerTools = new ArrayList<>();
    @Getter
    private final List<ToolSpecification> schedulerTools = new ArrayList<>();
    
    // 缓存的工具类列表
    private final List<Class<?>> cachedToolClasses = new ArrayList<>();
    
    // 缓存的工具方法映射 (工具名 -> 方法)
    private final Map<String, Method> toolMethodCache = new ConcurrentHashMap<>();
    
    // 可配置的扫描包路径
    private static final String[] SCAN_PACKAGES = {
        "com.zhouruojun.jobsearchagent.tools.collections"
    };
    
    public JobSearchToolCollection() {
        // 构造函数
    }
    
    /**
     * 应用启动后执行工具注册和缓存
     * 也可以手动调用用于非Spring管理的实例
     */
    @PostConstruct
    public void initializeTools() {
        try {
            // 使用官方推荐的方式生成工具规范
            registerTools();
            
            // 构建工具方法缓存（用于执行）
            buildToolMethodCache();
            
            log.info("工具初始化完成，发现 " + toolSpecifications.size() + " 个工具规范，" + toolMethodCache.size() + " 个工具方法");
            
        } catch (Exception e) {
            log.severe("工具初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 动态扫描工具类
     */
    private void registerTools() {
        // 清空现有的工具规范
        toolSpecifications.clear();
        jobInfoCollectionTools.clear();
        resumeAnalysisOptimizationTools.clear();
        jobSearchExecutionTools.clear();
        plannerTools.clear();
        schedulerTools.clear();
        cachedToolClasses.clear();
        
        // 动态扫描工具类
        List<Class<?>> toolClasses = scanToolClasses();
        
        // 使用官方方式为每个工具类生成规范
        for (Class<?> toolClass : toolClasses) {
            try {
                List<ToolSpecification> specs = toolSpecificationsFrom(toolClass);
                toolSpecifications.addAll(specs);
                cachedToolClasses.add(toolClass);
                
                // 根据工具类分组
                categorizeTools(specs, toolClass);
                
            } catch (Exception e) {
                log.warning("注册工具类 " + toolClass.getSimpleName() + " 失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 根据工具类将工具规范分组
     */
    private void categorizeTools(List<ToolSpecification> specs, Class<?> toolClass) {
        String className = toolClass.getSimpleName();
        
        for (ToolSpecification spec : specs) {
            if (className.contains("JobInfoCollection")) {
                jobInfoCollectionTools.add(spec);
            } else if (className.contains("ResumeAnalysisOptimization")) {
                resumeAnalysisOptimizationTools.add(spec);
            } else if (className.contains("JobSearchExecution")) {
                jobSearchExecutionTools.add(spec);
            } else {
                // 默认添加到岗位信息收集工具组
                jobInfoCollectionTools.add(spec);
            }
        }
    }

    /**
     * 根据智能体名称获取对应的工具列表
     * 统一工具获取方式，支持主智能体和子智能体
     */
    public List<ToolSpecification> getToolsByAgentName(String agentName) {
        switch (agentName) {
            case "planner":
                return plannerTools;
            case "scheduler":
                return schedulerTools;
            case "jobInfoCollectorAgent":
                return jobInfoCollectionTools;
            case "resumeAnalysisOptimizationAgent":
                return resumeAnalysisOptimizationTools;
            case "jobSearchExecutionAgent":
                return jobSearchExecutionTools;
            case "comprehensiveAnalysisAgent":
                // 综合分析智能体专注于整合和评估，不需要工具
                // 它基于其他子智能体的结果进行综合分析和报告生成
                return List.of();
            default:
                log.warning("Unknown agent name: " + agentName + ", returning empty tools");
                return List.of();
        }
    }
    
    /**
     * 动态扫描工具类
     */
    private List<Class<?>> scanToolClasses() {
        List<Class<?>> toolClasses = new ArrayList<>();
        
        try {
            // 使用Spring的类路径扫描器，设置为扫描所有类（不限制Spring注解）
            ClassPathScanningCandidateComponentProvider scanner = 
                new ClassPathScanningCandidateComponentProvider(false);
            
            // 添加包含过滤器，扫描所有类
            scanner.addIncludeFilter((metadata, factory) -> true);
            
            // 扫描所有配置的包路径
            for (String basePackage : SCAN_PACKAGES) {
                try {
                    Set<org.springframework.beans.factory.config.BeanDefinition> candidates = 
                        scanner.findCandidateComponents(basePackage);
                    
                    for (org.springframework.beans.factory.config.BeanDefinition candidate : candidates) {
                        try {
                            Class<?> clazz = Class.forName(candidate.getBeanClassName());
                            
                            // 检查类中是否有带有@Tool注解的方法
                            if (hasToolMethods(clazz)) {
                                toolClasses.add(clazz);
                            }
                            
                        } catch (ClassNotFoundException e) {
                            log.warning("无法加载类: " + candidate.getBeanClassName());
                        }
                    }
                } catch (Exception e) {
                    log.warning("扫描包 " + basePackage + " 时出错: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.severe("工具类扫描过程中出错: " + e.getMessage());
        }
        
        return toolClasses;
    }
    
    /**
     * 检查类中是否有带有@Tool注解的方法
     */
    private boolean hasToolMethods(Class<?> clazz) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warning("检查类 " + clazz.getName() + " 的工具方法时出错: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 重新扫描并注册工具（用于动态添加新工具）
     */
    public void rescanTools() {
        toolSpecifications.clear();
        toolMethodCache.clear();
        cachedToolClasses.clear();
        registerTools();
        buildToolMethodCache();
    }
    
    /**
     * 构建工具方法缓存
     */
    private void buildToolMethodCache() {
        for (Class<?> toolClass : cachedToolClasses) {
            Method[] methods = toolClass.getDeclaredMethods();
            for (Method method : methods) {
                // 处理所有带有@Tool注解的方法，包括静态和非静态方法
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    String toolName = method.getName();
                    toolMethodCache.put(toolName, method);
                }
            }
        }
    }
    
    /**
     * 获取当前注册的工具数量
     */
    public int getToolCount() {
        return toolSpecifications.size();
    }
    
    /**
     * 获取所有工具的名称列表
     */
    public List<String> getToolNames() {
        return toolSpecifications.stream()
                .map(ToolSpecification::name)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取缓存的工具方法信息
     */
    public Map<String, String> getCachedToolMethods() {
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, Method> entry : toolMethodCache.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getDeclaringClass().getSimpleName());
        }
        return result;
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("cachedToolClasses", cachedToolClasses.size());
        stats.put("cachedToolMethods", toolMethodCache.size());
        stats.put("toolSpecifications", toolSpecifications.size());
        stats.put("scanPackages", SCAN_PACKAGES.length);
        return stats;
    }
    
    /**
     * 获取当前扫描的包路径
     */
    public String[] getScanPackages() {
        return SCAN_PACKAGES.clone();
    }
    
    /**
     * 获取发现的工具类列表
     */
    public List<String> getDiscoveredToolClasses() {
        return cachedToolClasses.stream()
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 执行工具调用
     */
    public String executeTool(ToolExecutionRequest request, BaseAgentState state) {
        try {

            // 通过反射调用工具方法
            return executeToolByReflection(request, state);
        } catch (Exception e) {
            log.severe("Error executing tool " + request.name() + ": " + e.getMessage());
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * 通过反射执行工具方法
     */
    private String executeToolByReflection(ToolExecutionRequest request, BaseAgentState state) {
        try {
            // 使用缓存的方法直接查找
            Method method = toolMethodCache.get(request.name());
            
            if (method != null) {
                // 解析参数
                Object[] args = parseToolArguments(request.arguments(), method.getParameterTypes(), request.name(), state);
                
                // 判断是否为静态方法
                if (Modifier.isStatic(method.getModifiers())) {
                    // 调用静态方法
                    Object result = method.invoke(null, args);
                    return result != null ? result.toString() : "null";
                } else {
                    // 调用非静态方法，需要通过Spring容器获取实例
                    Object result = executeNonStaticTool(method, args);
                    return result != null ? result.toString() : "null";
                }
            } else {
                log.warning("工具未在缓存中找到: " + request.name());
                return "Tool not found: " + request.name();
            }
            
        } catch (Exception e) {
            log.severe("反射执行工具失败: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * 执行非静态工具方法
     */
    private Object executeNonStaticTool(Method method, Object[] args) {
        try {
            // 通过Spring容器获取工具类实例
            Class<?> toolClass = method.getDeclaringClass();
            Object toolInstance = getToolInstance(toolClass);
            
            if (toolInstance != null) {
                return method.invoke(toolInstance, args);
            } else {
                log.severe("无法获取工具类实例: " + toolClass.getSimpleName());
                return "Error: Cannot get tool instance for " + toolClass.getSimpleName();
            }
        } catch (Exception e) {
            log.severe("执行非静态工具失败: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * 获取工具类实例（通过Spring容器）
     */
    private Object getToolInstance(Class<?> toolClass) {
        try {
            if (applicationContext != null) {
                // 通过Spring容器获取工具类实例
                return applicationContext.getBean(toolClass);
            } else {
                log.warning("ApplicationContext未注入，无法获取工具实例: " + toolClass.getSimpleName());
                return null;
            }
        } catch (Exception e) {
            log.severe("获取工具实例失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析工具参数
     */
    private Object[] parseToolArguments(String argumentsJson, Class<?>[] parameterTypes, String toolName, BaseAgentState state) {
        try {
            com.alibaba.fastjson.JSONObject argsJson = com.alibaba.fastjson.JSONObject.parseObject(argumentsJson);
            Object[] args = new Object[parameterTypes.length];
            
            // 获取方法参数名（需要从缓存的工具方法中获取）
            Method method = getMethodFromCache(toolName);
            if (method != null) {
                java.lang.reflect.Parameter[] methodParams = method.getParameters();
                
                for (int i = 0; i < parameterTypes.length && i < methodParams.length; i++) {
                    Class<?> paramType = parameterTypes[i];
                    String paramName = methodParams[i].getName(); // 使用实际的参数名
                    
                    // 如果参数类型是BaseAgentState或其子类，直接使用传入的state
                    if (BaseAgentState.class.isAssignableFrom(paramType)) {
                        args[i] = state;
                    } else if (argsJson.containsKey(paramName)) {
                        Object value = argsJson.get(paramName);
                        args[i] = convertValue(value, paramType);
                    } else {
                        args[i] = getDefaultValue(paramType);
                    }
                }
            } else {
                // 回退到原来的逻辑
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> paramType = parameterTypes[i];
                    String paramName = "arg" + i;
                    
                    // 如果参数类型是BaseAgentState或其子类，直接使用传入的state
                    if (BaseAgentState.class.isAssignableFrom(paramType)) {
                        args[i] = state;
                    } else if (argsJson.containsKey(paramName)) {
                        Object value = argsJson.get(paramName);
                        args[i] = convertValue(value, paramType);
                    } else {
                        args[i] = getDefaultValue(paramType);
                    }
                }
            }
            
            return args;
        } catch (Exception e) {
            log.severe("解析工具参数失败: " + e.getMessage());
            return new Object[parameterTypes.length];
        }
    }
    
    /**
     * 从缓存中获取方法（通过工具名）
     */
    private Method getMethodFromCache(String toolName) {
        try {
            if (toolName != null && toolMethodCache.containsKey(toolName)) {
                return toolMethodCache.get(toolName);
            } else {
                return null;
            }
        } catch (Exception e) {
            log.warning("从缓存获取方法失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 转换值类型
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        // 基本类型转换
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(value.toString());
        } else if (targetType == java.util.List.class) {
            return com.alibaba.fastjson.JSONObject.parseArray(value.toString(), Object.class);
        } else if (targetType == java.util.Map.class) {
            return com.alibaba.fastjson.JSONObject.parseObject(value.toString(), java.util.Map.class);
        }
        
        return value;
    }

    /**
     * 获取默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
    
    /**
     * 创建子图工具集合的工厂方法
     * 基于已初始化的主工具集合创建轻量级的子图工具集合
     * 
     * @param agentName 智能体名称
     * @param mainToolCollection 主工具集合（用于获取工具和方法缓存）
     * @return 子图工具集合
     */
    public static JobSearchToolCollection createSubgraphToolCollection(String agentName, 
                                                                      JobSearchToolCollection mainToolCollection) {
        // 创建轻量级的工具集合，不重新初始化
        JobSearchToolCollection subgraphCollection = new JobSearchToolCollection();
        
        // 复制ApplicationContext引用
        subgraphCollection.applicationContext = mainToolCollection.applicationContext;
        
        // 直接从主工具集合获取对应智能体的工具
        List<ToolSpecification> tools = mainToolCollection.getToolsByAgentName(agentName);
        
        // 根据智能体名称确定目标工具组并添加工具
        switch (agentName) {
            case "jobInfoCollectorAgent":
                subgraphCollection.getJobInfoCollectionTools().addAll(tools);
                break;
            case "resumeAnalysisOptimizationAgent":
                subgraphCollection.getResumeAnalysisOptimizationTools().addAll(tools);
                break;
            case "jobSearchExecutionAgent":
                subgraphCollection.getJobSearchExecutionTools().addAll(tools);
                break;
            case "comprehensiveAnalysisAgent":
                // 综合分析智能体不需要工具，专注于整合和评估
                break;
            default:
                // 默认添加到岗位信息收集工具组
                subgraphCollection.getJobInfoCollectionTools().addAll(tools);
                break;
        }
        
        // 同时添加到主工具规范列表，确保getToolCount()返回正确值
        subgraphCollection.getToolSpecifications().addAll(tools);
        
        // 从主工具集合复制相关的方法缓存
        subgraphCollection.copyRelevantMethodCache(tools, mainToolCollection);
        
        return subgraphCollection;
    }
    
    /**
     * 从主工具集合复制相关的方法缓存
     * 只复制当前工具组需要的方法，避免重复扫描
     */
    private void copyRelevantMethodCache(List<ToolSpecification> tools, JobSearchToolCollection mainToolCollection) {
        // 获取工具名称集合
        java.util.Set<String> toolNames = tools.stream()
                .map(ToolSpecification::name)
                .collect(java.util.stream.Collectors.toSet());
        
        // 从主工具集合的方法缓存中复制相关方法
        Map<String, Method> mainMethodCache = mainToolCollection.getToolMethodCache();
        for (String toolName : toolNames) {
            Method method = mainMethodCache.get(toolName);
            if (method != null) {
                toolMethodCache.put(toolName, method);
            }
        }
        
        log.info("子图工具集合构建完成，包含 " + toolMethodCache.size() + " 个工具方法");
    }
    
    /**
     * 获取工具方法缓存（用于子图构建器）
     * @return 工具方法缓存
     */
    public Map<String, Method> getToolMethodCache() {
        return new java.util.HashMap<>(toolMethodCache);
    }
    
}
