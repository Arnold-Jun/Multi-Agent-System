package com.zhouruojun.a2acore.spec;

import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * ����״̬ö??* ���������submitted
 * ��������ִ�У�working
 * �ȴ��û����룬input-required
 * 4�����̬��
 *  ������ɣ�completed
 *  ����ȡ����canceled
 *  ����ʧ�ܣ�failed
 *  δ֪״̬��unknown
 *
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#63-taskstate-enum">�����ת</a>
 * </p>
 *
 */
@ToString
@Getter
public enum TaskState implements Serializable {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    ACTION_REQUIRED("action-required"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    UNKNOWN("unknown");

    private final String state;

    TaskState(String state) {
        this.state = state;
    }

    @JsonValue
    public String getState() {
        return state;
    }
}


