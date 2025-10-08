package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/10 12:40
 */
public enum ToolInterceptorEnum {
    CONFIRM("confirm"),
    TEXT("text"),
    CHOICE("choice"),
    REPLAY("replay")
    ;

    ToolInterceptorEnum(String type) {
        this.type = type;
    }

    private String type;

    public String getType() {
        return type;
    }

    public static ToolInterceptorEnum getByType(String type) {
        for (ToolInterceptorEnum toolInterceptorEnum : ToolInterceptorEnum.values()) {
            if (toolInterceptorEnum.getType().equals(type)) {
                return toolInterceptorEnum;
            }
        }
        return null;
    }
}
