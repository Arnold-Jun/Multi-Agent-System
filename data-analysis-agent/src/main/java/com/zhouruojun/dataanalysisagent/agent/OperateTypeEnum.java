package com.zhouruojun.dataanalysisagent.agent;

/**
 * 操作类型枚举
 * 与agent-core保持完全一致
 */
public enum OperateTypeEnum {
    
    CR("CR", "创建MR"),
    USER_CONFIRM("USER_CONFIRM", "用户确认"),
    USER_REPLAY("USER_REPLAY", "用户重放"),
    USER_INPUT("USER_INPUT", "用户输入"),
    DATA_LOAD("DATA_LOAD", "数据加载"),
    DATA_ANALYSIS("DATA_ANALYSIS", "数据分析"),
    DATA_VISUALIZATION("DATA_VISUALIZATION", "数据可视化");

    private final String code;
    private final String description;

    OperateTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static OperateTypeEnum fromCode(String code) {
        for (OperateTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown operate type: " + code);
    }
}

