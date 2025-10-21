package com.zhouruojun.travelingagent.tools.collections;

import com.zhouruojun.travelingagent.agent.state.BaseAgentState;
import com.zhouruojun.travelingagent.mcp.ToolCategory;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MetaSearchAgent 工具集合
 * 负责解析用户意图，进行信息搜索
 * 职责：目的地信息搜索、景点查询、交通信息、住宿信息、美食推荐、天气信息、当地活动
 */
@Slf4j
@Component
public class MetaSearchTool {

//    /**
//     * 搜索目的地信息
//     * 获取指定目的地的详细信息，包括景点、文化、历史等
//     */
//    @Tool("""
//        搜索目的地信息，包括景点、文化、历史等详细信息
//
//        参数说明：
//        - destination: 目的地名称，支持城市、国家、地区等
//        - infoType: 信息类型，可选值：all(全部)、attractions(景点)、culture(文化)、history(历史)、weather(天气)
//        - language: 语言偏好，可选值：zh(中文)、en(英文)
//
//        返回详细信息包括主要景点、文化特色、历史背景、最佳旅游时间等
//        """)
//    @ToolCategory(agents = {"metaSearchAgent"}, category = "meta_search", priority = 1)
//    public String searchDestinationInfo(String destination, String infoType, String language, BaseAgentState state) {
//        log.info("搜索目的地信息: destination={}, infoType={}, language={}", destination, infoType, language);
//
//        // TODO: 实现目的地信息搜索逻辑
//        return "目的地信息搜索功能待实现";
//    }
}


