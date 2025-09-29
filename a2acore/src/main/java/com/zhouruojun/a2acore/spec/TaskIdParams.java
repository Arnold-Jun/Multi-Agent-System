package com.zhouruojun.a2acore.spec;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * ����Id��Ԫ���ݣ�����tasks/cancel����
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#74-taskscancel">�����ת</a>
 * </p>
 *
 */
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaskIdParams {
    private String id;
    private Map<String, Object> metadata;
}


