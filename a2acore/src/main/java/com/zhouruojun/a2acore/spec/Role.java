package com.zhouruojun.a2acore.spec;

import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public enum Role implements Serializable {
    USER("user"),
    AGENT("agent"),
    ;
    private final String desc;

    Role(String desc) {
        this.desc = desc;
    }

    @JsonValue
    public String getDesc() {
        return desc;
    }
}


