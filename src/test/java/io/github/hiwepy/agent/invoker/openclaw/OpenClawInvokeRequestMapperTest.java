package io.github.hiwepy.agent.invoker.openclaw;

import io.github.hiwepy.agent.invoker.AgentInvokeCmd;
import io.github.hiwepy.openclaw.InvokeAgentRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OpenClawInvokeRequestMapper} 单元测试。
 */
class OpenClawInvokeRequestMapperTest {

    @Test
    void shouldPreferCmdCallbackUrlOverBaseUrl() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .callbackUrl("http://app/callback")
                .build();
        assertEquals("http://app/callback",
                OpenClawInvokeRequestMapper.resolveCallbackUrl(cmd, "http://base/default"));
    }

    @Test
    void shouldFallbackToCallbackBaseUrl() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder().build();
        assertEquals("http://base/default",
                OpenClawInvokeRequestMapper.resolveCallbackUrl(cmd, "http://base/default"));
    }

    @Test
    void shouldMapMetadataFieldsToInvokeRequest() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .agentId("main")
                .enhancedPrompt("Generate post")
                .tenantId("t1")
                .userId("u1")
                .channel("xiaohongshu")
                .taskId("task-001")
                .callbackUrl("http://localhost/save")
                .callbackToken("secret")
                .variables(Collections.<String, Object>singletonMap("tone", "casual"))
                .build();

        InvokeAgentRequest request = OpenClawInvokeRequestMapper.toInvokeRequest(cmd, "http://ignored");
        assertEquals("main", request.getAgentId());
        assertEquals("xiaohongshu", request.getChannel());
        assertEquals("u1", request.getTo());
        assertEquals(OpenClawInvokeRequestMapper.HookSessionStrategy.EPHEMERAL_PEER_WITH_CORRELATION,
                OpenClawInvokeRequestMapper.resolveSessionStrategy(cmd));
        assertEquals("hook:t1.u1:task-001", OpenClawInvokeRequestMapper.buildSessionKey(cmd));
        assertNull(request.getSessionKey());
        assertTrue(request.getMessage().contains("Generate post"));
        assertTrue(request.getMessage().contains("http://localhost/save"));
        assertTrue(request.getMessage().contains("task_id=\"task-001\""));
        assertTrue(request.getMessage().contains("Tenant ID: t1"));
        assertTrue(request.getMessage().contains("casual"));
        assertTrue(request.getMessage().contains("tone"));
    }

    @Test
    void shouldBuildSessionKeyFromTenantAndTaskOnly() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .tenantId("tenant-a")
                .taskId("task-b")
                .build();
        assertEquals("hook:tenant-a:task-b", OpenClawInvokeRequestMapper.buildSessionKey(cmd));
    }

    @Test
    void shouldUseStableSessionWhenBusinessAgentWithoutTaskId() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .tenantId("t1")
                .userId("u1")
                .businessAgentId("biz-agent")
                .agentId("main")
                .build();
        assertEquals(OpenClawInvokeRequestMapper.HookSessionStrategy.STABLE,
                OpenClawInvokeRequestMapper.resolveSessionStrategy(cmd));
        assertEquals("hook:main:t1.u1.biz-agent", OpenClawInvokeRequestMapper.buildSessionKey(cmd));
        assertEquals("t1.u1.biz-agent", OpenClawInvokeRequestMapper.resolvePeerId(cmd));
    }

    @Test
    void shouldUseEphemeralPeerWhenUserWithoutTaskOrBusinessAgent() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .tenantId("t1")
                .userId("u1")
                .agentId("main")
                .build();
        assertEquals(OpenClawInvokeRequestMapper.HookSessionStrategy.EPHEMERAL_PEER,
                OpenClawInvokeRequestMapper.resolveSessionStrategy(cmd));
        assertNull(OpenClawInvokeRequestMapper.buildSessionKey(cmd));
    }

    @Test
    void shouldRespectExplicitSessionKeyVariable() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .agentId("main")
                .variables(Collections.singletonMap("openclaw.sessionKey", "hook:custom:1"))
                .build();
        assertEquals(OpenClawInvokeRequestMapper.HookSessionStrategy.EXPLICIT,
                OpenClawInvokeRequestMapper.resolveSessionStrategy(cmd));
        InvokeAgentRequest request = OpenClawInvokeRequestMapper.toInvokeRequest(cmd, "http://base");
        assertEquals("hook:custom:1", request.getSessionKey());
    }

    @Test
    void shouldMapOpenClawVariablesToHookFields() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("openclaw.deliver", true);
        variables.put("openclaw.model", "openai/gpt-5.5");
        variables.put("openclaw.thinking", "off");
        variables.put("openclaw.wakeMode", "next-heartbeat");
        variables.put("openclaw.timeoutSeconds", 120);
        variables.put("openclaw.name", "CustomJob");
        variables.put("openclaw.channel", "last");
        variables.put("openclaw.to", "peer-9");
        variables.put("tone", "casual");
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .agentId("main")
                .enhancedPrompt("hello")
                .variables(variables)
                .build();

        InvokeAgentRequest request = OpenClawInvokeRequestMapper.toInvokeRequest(cmd, "http://base");
        assertEquals(true, request.getDeliver());
        assertEquals("openai/gpt-5.5", request.getModel());
        assertEquals("off", request.getThinking());
        assertEquals("next-heartbeat", request.getWakeMode());
        assertEquals(120, request.getTimeoutSeconds());
        assertEquals("CustomJob", request.getName());
        assertEquals("last", request.getChannel());
        assertEquals("peer-9", request.getTo());
    }
}
