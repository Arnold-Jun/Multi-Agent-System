package com.zhouruojun.travelingagent.agent.dto;

import lombok.Data;

/**
 * Supervisor响应DTO
 * 用于解析SupervisorAgent的JSON输出
 */
@Data
public class SupervisorResponse {
    
    /**
     * 下一步动作
     * "Finish" - 审查通过，结束子图
     * "revise" - 需要修改，返回itineraryPlanner
     */
    private String next;
    
    /**
     * 输出内容
     * 如果next是"revise"，则包含具体的方案修改意见
     * 如果next是"Finish"，可以为空或包含审查通过的信息
     */
    private String output;
}
