package com.zhouruojun.travelingagent.agent.builder;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.actions.CallSubAgent;
import com.zhouruojun.travelingagent.agent.actions.ExecuteTools;
import com.zhouruojun.travelingagent.agent.state.BaseAgentState;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.config.ParallelExecutionConfig;
import com.zhouruojun.travelingagent.mcp.TravelingToolProviderManager;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 基础子图构建器
 * 为所有旅游智能体子图提供统一的架构模式
 * 架构：CallAgent → ExecuteTools → Summary
 */
public abstract class BaseSubgraphBuilder<T extends BaseAgentState> {
    
    protected StreamingChatLanguageModel streamingChatLanguageModel;
    protected ChatLanguageModel chatLanguageModel;
    protected StateSerializer<T> stateSerializer;
    protected ParallelExecutionConfig parallelExecutionConfig;
    protected TravelingToolProviderManager toolProviderManager;
    protected BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue;
    protected PromptManager promptManager;
    
    // Checkpoint相关配置
    protected BaseCheckpointSaver checkpointSaver;
    protected boolean enableCheckpoint = true;
    protected String checkpointNamespace = "subgraph";


    /**
     * 设置聊天语言模型
     */
    public BaseSubgraphBuilder<T> chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public BaseSubgraphBuilder<T> streamingChatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public BaseSubgraphBuilder<T> stateSerializer(StateSerializer<T> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * 设置工具提供者管理器
     */
    public BaseSubgraphBuilder<T> toolProviderManager(TravelingToolProviderManager toolProviderManager) {
        this.toolProviderManager = toolProviderManager;
        return this;
    }
    

    /**
     * 设置并行执行配置
     */
    public BaseSubgraphBuilder<T> parallelExecutionConfig(ParallelExecutionConfig parallelExecutionConfig) {
        this.parallelExecutionConfig = parallelExecutionConfig;
        return this;
    }

    /**
     * 设置检查点保存器
     */
    public BaseSubgraphBuilder<T> checkpointSaver(BaseCheckpointSaver checkpointSaver) {
        this.checkpointSaver = checkpointSaver;
        return this;
    }

    /**
     * 设置是否启用检查点
     */
    public BaseSubgraphBuilder<T> enableCheckpoint(boolean enableCheckpoint) {
        this.enableCheckpoint = enableCheckpoint;
        return this;
    }

    /**
     * 设置检查点命名空间
     */
    public BaseSubgraphBuilder<T> checkpointNamespace(String checkpointNamespace) {
        this.checkpointNamespace = checkpointNamespace;
        return this;
    }

    /**
     * 设置提示词管理器
     */
    public BaseSubgraphBuilder<T> promptManager(PromptManager promptManager) {
        this.promptManager = promptManager;
        return this;
    }

    /**
     * 构建子图
     */
    public final StateGraph<T> build() throws GraphStateException {
        // 1. 参数验证
        validateParameters();
        
        // 2. 初始化默认值
        initializeDefaults();
        
        // 3. 创建智能体
        BaseAgent agent = createAgent();
        
        // 4. 创建节点
        CallSubAgent callAgent = createCallAgent(agent);
        ExecuteTools<T> executeTools = createExecuteTools(agent);
        
        // 5. 构建图 - 子类实现
        return buildGraph(callAgent, executeTools);
    }

    /**
     * 参数验证
     */
    protected void validateParameters() {
        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }
    }

    /**
     * 初始化默认值
     */
    protected void initializeDefaults() {
        if (checkpointSaver == null && enableCheckpoint) {
            checkpointSaver = new MemorySaver();
        }
        if (queue == null) {
            queue = new LinkedBlockingQueue<>();
        }
    }

    /**
     * 创建智能体
     */
    protected BaseAgent createAgent() {
        // 如果使用toolProviderManager，则根据智能体名称获取相关工具
        if (toolProviderManager != null) {
            return createAgentWithToolProvider();
        }
        
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName(getAgentName())
                .prompt(getPrompt())
                .build();
    }

    /**
     * 创建智能体 - 使用工具提供者管理器的统一方式
     */
    protected BaseAgent createAgentWithToolProvider() {
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolProviderManager.getToolSpecificationsByAgent(getAgentName()))
                .agentName(getAgentName())
                .prompt(getPrompt())
                .build();
    }

    /**
     * 创建调用智能体节点
     */
    protected CallSubAgent createCallAgent(BaseAgent agent) {
        CallSubAgent callAgent = new CallSubAgent(getAgentName(), agent, promptManager);
        @SuppressWarnings("unchecked")
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>> subgraphQueue =
            (BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>>) (BlockingQueue<?>) queue;
        callAgent.setQueue(subgraphQueue);
        return callAgent;
    }

    /**
     * 创建执行工具节点
     */
    protected ExecuteTools<T> createExecuteTools(BaseAgent agent) {
        return new ExecuteTools<>(getActionName(), agent, toolProviderManager, parallelExecutionConfig);
    }

    /**
     * 构建图 - 子类必须实现
     */
    protected abstract StateGraph<T> buildGraph(CallSubAgent callAgent, ExecuteTools<T> executeTools) throws GraphStateException;

    /**
     * 获取智能体名称 - 子类必须实现
     */
    protected abstract String getAgentName();

    /**
     * 获取动作名称 - 子类必须实现
     */
    protected abstract String getActionName();

    /**
     * 获取提示词 - 子类必须实现
     */
    protected abstract String getPrompt();


    /**
     * 获取标准的边条件 - 辅助方法
     * 智能体判断是否需要调用工具
     */
    protected EdgeAction<T> getStandardAgentShouldContinue() {
        return (state) ->
                state.next().orElse("END");
    }

    /**
     * 获取标准的工具执行回调边条件
     * 工具执行完成后返回智能体
     */
    protected EdgeAction<T> getStandardActionShouldContinue() {
        return (state) ->
                state.next().orElse("callback");
    }
}