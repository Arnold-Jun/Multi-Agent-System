package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp;

import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolInterceptor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/10 12:38
 */
public class ToolsInterceptorUtil {

    private Map<String, ToolInterceptor> toolsInterceptorMap;
    private Map<String, ToolInterceptor> toolsPostInterceptorMap;

    @Getter
    private static ToolsInterceptorUtil instance = new ToolsInterceptorUtil();

    private ToolsInterceptorUtil() {
        toolsInterceptorMap = new ConcurrentHashMap<>();
//        toolsInterceptorMap.put("getSourceAndRepo", createToolInterceptor("getSourceAndRepo"));
        toolsInterceptorMap.put("git_add", createToolInterceptor("git_add", ToolInterceptorEnum.CONFIRM));
        toolsInterceptorMap.put("git_commit", createToolInterceptor("git_commit", ToolInterceptorEnum.CONFIRM));
        toolsInterceptorMap.put("git_push", createToolInterceptor("git_push", ToolInterceptorEnum.CONFIRM));
        toolsInterceptorMap.put("crCreate", createToolInterceptor("crCreate", ToolInterceptorEnum.CONFIRM));

        toolsPostInterceptorMap = new ConcurrentHashMap<>();
        toolsPostInterceptorMap.put("autoSuggestReviewer", createToolInterceptor("autoSuggestReviewer", ToolInterceptorEnum.CHOICE));
        toolsPostInterceptorMap.put("checkMergeConflict", createToolInterceptor("checkMergeConflict", ToolInterceptorEnum.REPLAY));
        toolsPostInterceptorMap.put("queryCrTemplateConfig", createToolInterceptor("queryCrTemplateConfig", ToolInterceptorEnum.TEXT));
    }

    public ToolInterceptor createToolInterceptor(String toolName, ToolInterceptorEnum type) {
        ToolInterceptor toolInterceptor = new ToolInterceptor();
        toolInterceptor.setName(toolName);
        toolInterceptor.setType(type);
        return toolInterceptor;
    }

    public ToolInterceptor getToolInterceptor(String toolName) {
        return toolsInterceptorMap.getOrDefault(toolName, null);
    }

    public ToolInterceptor getPostToolInterceptor(String toolName) {
        return toolsPostInterceptorMap.getOrDefault(toolName, null);
    }

    public static ToolInterceptor getInterceptor(String toolName) {
        return ToolsInterceptorUtil.getInstance().getToolInterceptor(toolName);
    }

    public static ToolInterceptor getPostInterceptor(String toolName) {
        return ToolsInterceptorUtil.getInstance().getPostToolInterceptor(toolName);
    }

    public static ToolExecutionRequest buildToolRequestByInterceptor(ToolInterceptor toolInterceptor) {
        return ToolExecutionRequest.builder()
                .id(toolInterceptor.getId())
                .name(toolInterceptor.getName())
                .arguments(toolInterceptor.getToolInfo())
                .build();
    }
}
