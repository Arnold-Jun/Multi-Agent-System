package com.xiaohongshu.codewiz.codewizagent.agentcore.agent;

import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 11:59
 */
public class AgentOperateRequest {

    private OperateTypeEnum operateType;
    private List<String> text;

    public AgentOperateRequest() {}

    public AgentOperateRequest(OperateTypeEnum operateType, List<String> text) {
        this.operateType = operateType;
        this.text = text;
    }

    public List<String> getText() {
        return text;
    }

    public OperateTypeEnum getOperateType() {
        return operateType;
    }

    public void setOperateType(OperateTypeEnum operateType) {
        this.operateType = operateType;
    }

    public void setText(List<String> text) {
        this.text = text;
    }

}
