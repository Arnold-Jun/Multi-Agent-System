package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.state.AgentMessageState;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CallAgentTest {

    @Test
    public void testCallAgentWithValidState() {
        // 创建测试消息
        UserMessage userMsg = UserMessage.from("Hello, world!");
        
        // 创建状态数??
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("messages", List.of(userMsg));
        stateData.put("sessionId", "test-session");
        
        // 创建状??
        AgentMessageState state = new AgentMessageState(stateData);
        
        // 测试状态是否正确初始化
        assertNotNull(state);
        assertFalse(state.messages().isEmpty());
        assertEquals(1, state.messages().size());
        assertEquals("Hello, world!", state.messages().get(0).text());
        
        // 测试状态的其他字段
        assertTrue(state.value("sessionId").isPresent());
        assertEquals("test-session", state.value("sessionId").get());
    }
    
    @Test
    public void testCallAgentWithEmptyState() {
        // 创建空状态，但包含messages字段
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("messages", List.of());
        stateData.put("sessionId", "test-session");
        
        // 创建状??
        AgentMessageState state = new AgentMessageState(stateData);
        
        // 测试状态是否正确初始化
        assertNotNull(state);
        assertTrue(state.messages().isEmpty());
        
        // 测试状态的其他字段
        assertTrue(state.value("sessionId").isPresent());
        assertEquals("test-session", state.value("sessionId").get());
    }
}


