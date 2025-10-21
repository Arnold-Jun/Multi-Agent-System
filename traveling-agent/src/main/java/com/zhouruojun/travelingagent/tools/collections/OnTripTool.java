package com.zhouruojun.travelingagent.tools.collections;

import com.zhouruojun.travelingagent.agent.state.BaseAgentState;
import com.zhouruojun.travelingagent.mcp.ToolCategory;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OnTripAgent 工具集合
 * 负责出发前提醒、登机口变动、延误/取消自动改签、酒店超售换房、当地交通/紧急联系人
 * 职责：出发提醒、航班监控、酒店服务、交通安排、紧急联系、安全监控
 */
@Slf4j
@Component
public class OnTripTool {

    /**
     * 发送出发前提醒
     * 发送出发前的各种提醒通知
     */
    @Tool("""
        发送出发前提醒，包括准备物品、天气信息、交通建议等
        
        参数说明：
        - departureTime: 出发时间
        - destination: 目的地
        - weatherInfo: 天气信息
        - preparationList: 准备清单
        - contactInfo: 紧急联系信息
        
        返回提醒发送结果，包括发送状态、提醒内容等
        """)
    @ToolCategory(agents = {"onTripAgent"}, category = "on_trip", priority = 1)
    public String sendDepartureReminder(String departureTime, String destination, String weatherInfo, String preparationList, String contactInfo, BaseAgentState state) {
        log.info("发送出发前提醒: departureTime={}, destination={}, weatherInfo={}, preparationList={}, contactInfo={}", departureTime, destination, weatherInfo, preparationList, contactInfo);
        
        // TODO: 实现出发前提醒逻辑
        return "出发前提醒功能待实现";
    }
}


