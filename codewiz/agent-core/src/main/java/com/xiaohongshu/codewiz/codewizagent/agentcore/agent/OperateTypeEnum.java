package com.xiaohongshu.codewiz.codewizagent.agentcore.agent;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 11:56
 */
public enum OperateTypeEnum {
    YUNXIAO("YUNXIAO"),
    CR("CR")
    ;
    private OperateTypeEnum(String type) {
        this.type = type;
    }
    private String type;

    public String getType() {
        return type;
    }
}
