package com.zhouruojun.jobsearchagent.agent;

/**
 * 操作类型枚举
 * 与agent-core保持完全一致
 */
public enum OperateTypeEnum {
    
    CR("CR", "创建MR"),
    USER_CONFIRM("USER_CONFIRM", "用户确认"),
    USER_REPLAY("USER_REPLAY", "用户重放"),
    USER_INPUT("USER_INPUT", "用户输入"),
    RESUME_ANALYSIS("RESUME_ANALYSIS", "简历分析"),
    JOB_SEARCH("JOB_SEARCH", "岗位搜索"),
    MATCHING_ANALYSIS("MATCHING_ANALYSIS", "匹配度分析"),
    CAREER_ADVICE("CAREER_ADVICE", "职业建议");

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
