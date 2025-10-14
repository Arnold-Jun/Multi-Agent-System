package com.zhouruojun.jobsearchagent.agent.builder;

import com.zhouruojun.jobsearchagent.agent.BaseAgent;
import com.zhouruojun.jobsearchagent.agent.actions.CallSubAgent;
import com.zhouruojun.jobsearchagent.agent.actions.ExecuteTools;
import com.zhouruojun.jobsearchagent.agent.state.BaseAgentState;
import com.zhouruojun.jobsearchagent.agent.state.SubgraphState;
import com.zhouruojun.jobsearchagent.config.ParallelExecutionConfig;
import com.zhouruojun.jobsearchagent.tools.JobSearchToolCollection;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import org.bsc.langgraph4j.CompileConfig;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 基础子图构建器
 * 为所有求职智能体子图提供统一的架构模式
 * 架构：CallAgent → ExecuteTools → Summary
 */
public abstract class BaseSubgraphBuilder<T extends BaseAgentState> {
    
    protected StreamingChatLanguageModel streamingChatLanguageModel;
    protected ChatLanguageModel chatLanguageModel;
    protected StateSerializer<T> stateSerializer;
    protected ParallelExecutionConfig parallelExecutionConfig;
    protected List<ToolSpecification> tools;
    protected BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue;
    protected JobSearchToolCollection mainToolCollection;
    
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
     * 设置工具列表
     */
    public BaseSubgraphBuilder<T> tools(List<ToolSpecification> tools) {
        this.tools = tools;
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
     * 设置主工具集合
     */
    public BaseSubgraphBuilder<T> mainToolCollection(JobSearchToolCollection mainToolCollection) {
        this.mainToolCollection = mainToolCollection;
        return this;
    }
    
    /**
     * 设置checkpoint保存器
     */
    public BaseSubgraphBuilder<T> checkpointSaver(BaseCheckpointSaver checkpointSaver) {
        this.checkpointSaver = checkpointSaver;
        return this;
    }
    
    /**
     * 设置是否启用checkpoint
     */
    public BaseSubgraphBuilder<T> enableCheckpoint(boolean enableCheckpoint) {
        this.enableCheckpoint = enableCheckpoint;
        return this;
    }
    
    /**
     * 设置checkpoint命名空间
     */
    public BaseSubgraphBuilder<T> checkpointNamespace(String checkpointNamespace) {
        this.checkpointNamespace = checkpointNamespace;
        return this;
    }

    /**
     * 模板方法：构建状态图
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
        if (stateSerializer == null) {
            // 子类需要重写此方法来设置特定的序列化器
            throw new IllegalStateException("stateSerializer must be set by subclass");
        }
        
        // 创建队列用于流式输出
        queue = new LinkedBlockingQueue<>();
        
        // 初始化checkpoint保存器
        if (checkpointSaver == null && enableCheckpoint) {
            checkpointSaver = new MemorySaver();
        }
    }

    /**
     * 创建智能体 - 子类实现
     */
    protected BaseAgent createAgent() {
        // 如果使用mainToolCollection，则从工具集合获取工具
        if (mainToolCollection != null) {
            return createAgentWithToolCollection(mainToolCollection);
        }
        
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(tools)
                .agentName(getAgentName())
                .prompt(getPrompt())
                .build();
    }

    /**
     * 创建智能体 - 使用工具集合的统一方式
     */
    protected BaseAgent createAgentWithToolCollection(JobSearchToolCollection toolCollection) {
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolCollection.getToolsByAgentName(getAgentName()))
                .agentName(getAgentName())
                .prompt(getPrompt())
                .build();
    }

    /**
     * 创建调用智能体节点
     */
    protected CallSubAgent createCallAgent(BaseAgent agent) {
        CallSubAgent callAgent = new CallSubAgent(getAgentName(), agent);
        // 注意：这里需要类型转换，因为CallSubAgent期望SubgraphState，但基类使用泛型T
        // 由于子图构建器都使用SubgraphState，这个转换是安全的
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
        // 使用工厂方法创建轻量级的工具集合
        JobSearchToolCollection tempToolCollection = createTempToolCollection();
        return new ExecuteTools<>(getActionName(), agent, tempToolCollection, parallelExecutionConfig);
    }

    /**
     * 创建临时工具集合 - 子类实现
     */
    protected abstract JobSearchToolCollection createTempToolCollection();

    /**
     * 构建图 - 子类实现
     */
    protected abstract StateGraph<T> buildGraph(
            CallSubAgent callAgent,
            ExecuteTools<T> executeTools
    ) throws GraphStateException;

    /**
     * 获取智能体名称 - 子类实现
     */
    protected abstract String getAgentName();

    /**
     * 获取动作名称 - 子类实现
     */
    protected abstract String getActionName();


    /**
     * 获取提示词 - 子类实现
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
