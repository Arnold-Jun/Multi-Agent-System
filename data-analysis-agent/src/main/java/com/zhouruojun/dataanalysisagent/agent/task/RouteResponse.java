package com.zhouruojun.dataanalysisagent.agent.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 璺敱鍝嶅簲绫?
 * 鐢ㄤ簬瑙ｆ瀽鏅鸿兘浣撶殑璺敱鍐崇瓥
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteResponse {

    /**
     * 璺敱鍔ㄤ綔
     */
    @JsonProperty("action")
    private String action;

    /**
     * 鐩爣鏅鸿兘浣撶殑鍚嶇О
     */
    @JsonProperty("targetAgent")
    private String targetAgent;

    /**
     * 浠诲姟鎻忚堪锛堝綋action涓嶆槸FINISH鏃讹級
     */
    @JsonProperty("task")
    private TaskDescription task;

    /**
     * 鏈€缁堟€荤粨锛堝綋action鏄疐INISH鏃讹級
     */
    @JsonProperty("summary")
    private String summary;

    /**
     * 鏋勯€犲嚱鏁?
     */
    public RouteResponse() {
    }

    /**
     * 鏋勯€犲嚱鏁?
     */
    public RouteResponse(String action, TaskDescription task, String summary) {
        this(action, null, task, summary);
    }

    public RouteResponse(String action, String targetAgent, TaskDescription task, String summary) {
        this.action = action;
        this.targetAgent = targetAgent;
        this.task = task;
        this.summary = summary;
    }

    /**
     * 鍒涘缓瀹屾垚鍝嶅簲
     */
    public static RouteResponse finish(String summary) {
        return new RouteResponse("FINISH", null, null, summary);
    }

    /**
     * 鍒ゆ柇鏄惁涓哄畬鎴愬姩浣?
     */
    public boolean isFinish() {
        return "FINISH".equals(action);
    }

    /**
     * 鍒ゆ柇鏄惁涓鸿矾鐢卞姩浣?
     */
    public boolean isRoute() {
        return action != null && !"FINISH".equals(action);
    }
}
