package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.parser.PlannerJsonParser;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.main.TodoList;
import com.zhouruojun.travelingagent.agent.state.main.TodoTask;
import dev.langchain4j.data.message.AiMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.*;

/**
 * TodoList解析器节点
 * 专门用于解析Planner输出的JSON格式，并更新TodoList
 */
@Slf4j
public class TodoListParser implements NodeAction<MainGraphState> {
    
    private static final PlannerJsonParser JSON_PARSER = new PlannerJsonParser();
    
    @Override
    public Map<String, Object> apply(MainGraphState state) {
        log.info("开始解析Planner的JSON输出");
        
        try {
            // 获取和验证最后一条消息
            AiMessage aiMessage = state.lastMessage()
                .filter(msg -> msg instanceof AiMessage)
                .map(msg -> (AiMessage) msg)
                .orElse(null);
                
            if (aiMessage == null) {
                log.warn("无法获取有效的AI消息进行JSON解析");
                return createErrorResult(state, "无法获取有效的AI消息");
            }

            String plannerOutput = aiMessage.text();
            
            log.info("Planner输出内容: {}", plannerOutput);
            
            // 解析JSON并更新TodoList
            TodoList updatedTodoList = JSON_PARSER.parseAndUpdateTodoList(plannerOutput, state.getTodoList().orElse(null));
            
            log.info("TodoList解析成功，当前任务数量: {}", updatedTodoList.getTasks().size());
            log.info("TodoList摘要: {}", updatedTodoList.getSummary());
            
            // 返回成功结果（会更新状态）
            Map<String, Object> result = createSuccessResult(state, updatedTodoList);
            return result;
            
        } catch (PlannerJsonParser.PlannerParseException e) {
            log.error("JSON解析失败: {}", e.getMessage(), e);
            return createErrorResult(state, "JSON解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("TodoList解析过程中发生未知错误: {}", e.getMessage(), e);
            return createErrorResult(state, "JSON解析过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 创建成功结果
     */
    private Map<String, Object> createSuccessResult(MainGraphState state, TodoList updatedTodoList) {
        Map<String, Object> result = new HashMap<>();
        result.put("messages", state.messages());
        
        // 直接设置更新的TodoList
        result.put("todoList", updatedTodoList);
        
        // 决定下一个节点：如果有可执行任务，路由到scheduler；否则路由到summary
        String nextNode = determineNextNode(updatedTodoList);
        result.put("next", nextNode);
        
        return result;
    }
    
    /**
     * 确定下一个节点
     */
    private String determineNextNode(TodoList todoList) {
        TodoTask nextTask = todoList.getNextExecutableTask();
        
        if (nextTask == null) {
            log.info("没有可执行的任务，路由到summary");
            return "summary";
        }
        
        log.info("有可执行任务 {}，路由到scheduler", nextTask.getTaskId());
        return "scheduler";
    }
    
    /**
     * 创建错误结果
     * 构造一条AiMessage，包含原始Planner输出和失败报错
     */
    private Map<String, Object> createErrorResult(MainGraphState state, String errorMessage) {
        log.error("TodoList解析失败: {}", errorMessage);
        
        Map<String, Object> result = new HashMap<>();
        
        // 获取上一次Planner的JSON输出并写入结构化状态键
        String originalPlannerOutput = state.lastMessage()
            .filter(msg -> msg instanceof AiMessage)
            .map(msg -> ((AiMessage) msg).text())
            .orElse("");

        result.put("plannerLastJsonOutput", originalPlannerOutput);
        result.put("plannerJsonErrorMessage", errorMessage);

        // 保持原有消息历史，不以错误信息覆盖，messages仅用于留痕
        result.put("messages", state.messages());

        // 保持TodoList状态
        if (state.getTodoList().isPresent()) {
            result.put("todoList", state.getTodoList().get());
        }
        
        // JSON解析失败，路由回planner重新生成
        result.put("next", "planner");
        
        return result;
    }
    
    
}