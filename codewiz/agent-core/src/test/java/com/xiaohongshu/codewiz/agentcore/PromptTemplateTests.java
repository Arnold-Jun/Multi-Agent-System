package com.xiaohongshu.codewiz.agentcore;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2a.client.autoconfiguration.A2aAgentProperties;
import com.xiaohongshu.codewiz.a2a.client.autoconfiguration.A2aAgentsProperties;
import com.xiaohongshu.codewiz.a2a.client.autoconfiguration.A2aHostAutoConfiguration;
import com.xiaohongshu.codewiz.a2acore.client.A2AClient;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.a2acore.spec.AuthenticationInfo;
import com.xiaohongshu.codewiz.a2acore.spec.PushNotificationConfig;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.TeamMember;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptParseToObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptTemplate;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptTemplateReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class PromptTemplateTests {

    @Test
    public void testPromptTemplate() {
        List<String> members = new LinkedList<>();
        A2aHostAutoConfiguration a2aHostAutoConfiguration = new A2aHostAutoConfiguration();
        A2aAgentsProperties a2aAgentsProperties = new A2aAgentsProperties();
        PushNotificationConfig pushNotificationConfig = new PushNotificationConfig();
        pushNotificationConfig.setUrl("http://127.0.0.1:8081");
        pushNotificationConfig.setToken("123456");
        pushNotificationConfig.setAuthentication(new AuthenticationInfo());
        a2aAgentsProperties.setNotification(pushNotificationConfig);

        Map<String, A2aAgentProperties> agents = new ConcurrentHashMap<>();
        A2aAgentProperties a2aAgentProperties = new A2aAgentProperties();
        a2aAgentProperties.setBaseUrl("http://127.0.0.1:8089");
        agents.put("crAgent", a2aAgentProperties);
        a2aAgentsProperties.setAgents(agents);

        List<A2AClient> client = a2aHostAutoConfiguration.createClient(a2aAgentsProperties, null);
        List<AgentCard> agentCards = client.stream().map(A2AClient::getAgentCard).toList();

        Map<String, Object> params = new ConcurrentHashMap<>();
        members.addAll(agentCards.stream().map(AgentCard::getName).toList());
        // 添加团队成员
        params.put("TeamMembers", String.join(", ", members));
        // 添加AgentCard
        params.put("AVAILABLE_AGENTS", agentCards);
        String supervisorPrompt =
                PromptTemplateReader.getInstance().getPrompt("SupervisorPrompt", params);
        log.info(supervisorPrompt);

        TeamMember teamMember = PromptParseToObject.parse(supervisorPrompt, TeamMember.class);
        log.info(JSONObject.toJSONString(teamMember));
    }
}
