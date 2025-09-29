package com.zhouruojun.a2a.client.autoconfiguration;

import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.client.AgentCardResolver;
import com.zhouruojun.a2acore.client.sse.SseEventHandler;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.PushNotificationConfig;
import com.zhouruojun.a2acore.spec.message.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@EnableConfigurationProperties(A2aAgentsProperties.class)
@Configuration(proxyBeanMethods = false)
public class A2aHostAutoConfiguration {


    @Bean(name = "a2aClients")
    @ConditionalOnBean(SseEventHandler.class)
    public List<A2AClient> a2aClientsWithHandlers(A2aAgentsProperties a2AAgentsProperties, List<SseEventHandler> sseEventHandlers) {
        return createClient(a2AAgentsProperties, sseEventHandlers);
    }

    @Bean(name = "a2aClients")
    @ConditionalOnMissingBean(SseEventHandler.class)
    public List<A2AClient> a2aClientsWithoutHandlers(A2aAgentsProperties a2AAgentsProperties) {
        return createClient(a2AAgentsProperties, null);
    }

    public List<A2AClient> createClient(A2aAgentsProperties a2AAgentsProperties, List<SseEventHandler> sseEventHandlers) {
        PushNotificationConfig notificationGlobal = a2AAgentsProperties.getNotification();
        Map<String, A2aAgentProperties> agents = a2AAgentsProperties.getAgents();
        return agents.entrySet().stream()
                .map(entry -> {
                    PushNotificationConfig notification = entry.getValue().getNotification();

                    AgentCardResolver resolver = new AgentCardResolver(entry.getKey(), entry.getValue().getBaseUrl(), entry.getValue().getAgentCardPath());
                    try {
                        AgentCard agentCard = resolver.resolve();
                        return new A2AClient(agentCard, sseEventHandlers, notification != null ? notification : notificationGlobal, WebClient.create());
                    } catch (Exception ex) {
                        log.error("agent resolve exception, agent: {}, error: {}", Util.toJson(resolver), ex.getMessage());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
