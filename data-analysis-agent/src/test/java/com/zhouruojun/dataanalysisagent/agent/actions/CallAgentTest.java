package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.state.MainGraphState;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CallAgentTest {

    @Test
    public void testCallAgentWithValidState() {
        // ����������Ϣ
        UserMessage userMsg = UserMessage.from("Hello, world!");
        
        // ����״̬��??
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("messages", List.of(userMsg));
        stateData.put("sessionId", "test-session");
        
        // ����״??
        MainGraphState state = new MainGraphState(stateData);
        
        // ����״̬�Ƿ���ȷ��ʼ��
        assertNotNull(state);
        assertFalse(state.messages().isEmpty());
        assertEquals(1, state.messages().size());
        assertEquals("Hello, world!", state.messages().get(0).text());
        
        // ����״̬�������ֶ�
        assertTrue(state.value("sessionId").isPresent());
        assertEquals("test-session", state.value("sessionId").get());
    }
    
    @Test
    public void testCallAgentWithEmptyState() {
        // ������״̬��������messages�ֶ�
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("messages", List.of());
        stateData.put("sessionId", "test-session");
        
        // ����״??
        MainGraphState state = new MainGraphState(stateData);
        
        // ����״̬�Ƿ���ȷ��ʼ��
        assertNotNull(state);
        assertTrue(state.messages().isEmpty());
        
        // ����״̬�������ֶ�
        assertTrue(state.value("sessionId").isPresent());
        assertEquals("test-session", state.value("sessionId").get());
    }
}


