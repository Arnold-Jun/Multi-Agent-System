package com.zhouruojun.travelingagent.tools.collections;

import com.zhouruojun.travelingagent.agent.state.BaseAgentState;
import com.zhouruojun.travelingagent.mcp.ToolCategory;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BookingAgent 工具集合
 * 负责各供应商下单、支付、发票、日历写入等功能
 * 职责：机票预订、酒店预订、门票预订、支付处理、发票管理、日历写入
 */
@Slf4j
@Component
public class BookingTool {

//    /**
//     * 预订机票
//     * 处理机票预订，包括航班选择、座位选择、餐食等
//     */
//    @Tool("""
//        预订机票，支持往返机票预订
//
//        参数说明：
//        - fromCity: 出发城市
//        - toCity: 到达城市
//        - departureDate: 出发日期，格式：YYYY-MM-DD
//        - returnDate: 返程日期，格式：YYYY-MM-DD（可选）
//        - passengers: 乘客信息，包括姓名、身份证号等
//        - preferences: 偏好设置，如：座位偏好、餐食偏好等
//
//        返回预订结果，包括航班信息、预订确认号、价格等
//        """)
//    @ToolCategory(agents = {"bookingAgent"}, category = "booking", priority = 1)
//    public String bookFlight(String fromCity, String toCity, String departureDate, String returnDate, String passengers, String preferences, BaseAgentState state) {
//        log.info("预订机票: fromCity={}, toCity={}, departureDate={}, returnDate={}, passengers={}, preferences={}", fromCity, toCity, departureDate, returnDate, passengers, preferences);
//
//        // TODO: 实现机票预订逻辑
//        return "机票预订功能待实现";
//    }
}


