package com.zhouruojun.agentcore.config;

/**
 * 智能体常量配置
 * 统一管理硬编码的配置值
 * 
 */
public class AgentConstants {
    
    // 数据分析智能体配置
    public static final String DATA_ANALYSIS_AGENT_NAME = "data-analysis-agent";
    public static final String DATA_ANALYSIS_AGENT_URL = "http://localhost:8082/";
    public static final String DATA_ANALYSIS_AGENT_CARD_PATH = "/.well-known/agent.json";
    
    // 会话配置
    public static final long SESSION_TTL_HOURS = 2;
    
    // 超时配置
    public static final int CONNECT_TIMEOUT_SECONDS = 10;
    public static final int READ_TIMEOUT_MINUTES = 10;
    
    // 任务类型
    public static final String TASK_TYPE_CHAT = "chat";
    public static final String TASK_TYPE_DATA_ANALYSIS = "data_analysis";
    public static final String TASK_TYPE_LOAD_DATA = "load_data";
    public static final String TASK_TYPE_GENERATE_CHART = "generate_chart";
    
    // 系统用户名
    public static final String SYSTEM_USERNAME = "system";
    
    private AgentConstants() {
        // 工具类，禁止实例化
    }
}
