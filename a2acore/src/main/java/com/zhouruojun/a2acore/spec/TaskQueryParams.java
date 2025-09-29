package com.zhouruojun.a2acore.spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 任务查询请求入参，用于tasks/get场景
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#731-taskqueryparams-object">点击跳转</a>
 * </p>
 */
@ToString
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaskQueryParams extends TaskIdParams {
    private Integer historyLength;
}


