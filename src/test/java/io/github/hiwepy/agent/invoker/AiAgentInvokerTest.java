package io.github.hiwepy.agent.invoker;

import io.github.hiwepy.agent.invoker.openclaw.OpenClawAiAgentInvoker;
import io.github.hiwepy.agent.invoker.openclaw.OpenClawCallbackParser;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AiAgentInvokerTest {

    @Test
    void shouldRouteByProviderCode() {
        AiAgentInvokerRouter router = new AiAgentInvokerRouter();
        // Without any registered invokers, routing should throw
        assertThrows(AgentInvokeException.class, () -> router.route("openclaw"));
    }

    @Test
    void shouldDefaultToOpenClawWhenNullProviderCode() {
        AiAgentInvokerRouter router = new AiAgentInvokerRouter();
        router.setDefaultProvider("openclaw");
        try {
            router.route((String) null);
        } catch (AgentInvokeException e) {
            assertTrue(e.getMessage().contains("openclaw"));
        }
    }

    @Test
    void shouldUseConfiguredDefaultProvider() {
        AiAgentInvokerRouter router = new AiAgentInvokerRouter();
        router.setDefaultProvider("custom");
        assertEquals("custom", router.getDefaultProvider());
        AgentInvokeException ex = assertThrows(AgentInvokeException.class, () -> router.route((String) null));
        assertTrue(ex.getMessage().contains("custom"));
    }

    @Test
    void shouldParseValidCallback() {
        OpenClawCallbackParser parser = new OpenClawCallbackParser();
        String json = "{"
                + "\"task_id\":\"task-001\","
                + "\"title\":\"Test Title\","
                + "\"content\":\"Test Content\","
                + "\"cover_url\":\"http://img.com/1.jpg\","
                + "\"source_channel\":\"xiaohongshu\""
                + "}";

        RawCallbackPayload payload = RawCallbackPayload.builder()
                .providerCode("openclaw")
                .rawBody(json)
                .build();

        CallbackOutcome outcome = parser.parse(payload);
        assertTrue(outcome.isSuccess());
        assertEquals("task-001", outcome.getTaskId());
        assertEquals("Test Title", outcome.getTitle());
        assertEquals("Test Content", outcome.getContent());
        assertEquals("xiaohongshu", outcome.getSourceChannel());
    }

    @Test
    void shouldRejectEmptyCallbackBody() {
        OpenClawCallbackParser parser = new OpenClawCallbackParser();
        RawCallbackPayload payload = RawCallbackPayload.builder()
                .rawBody("")
                .build();

        CallbackOutcome outcome = parser.parse(payload);
        assertFalse(outcome.isSuccess());
        assertNotNull(outcome.getErrorMessage());
    }

    @Test
    void shouldRejectInvalidJson() {
        OpenClawCallbackParser parser = new OpenClawCallbackParser();
        RawCallbackPayload payload = RawCallbackPayload.builder()
                .rawBody("not valid {{{")
                .build();

        CallbackOutcome outcome = parser.parse(payload);
        assertFalse(outcome.isSuccess());
        assertNotNull(outcome.getErrorMessage());
    }

    @Test
    void shouldBuildAgentInvokeCmd() {
        AgentInvokeCmd cmd = AgentInvokeCmd.builder()
                .tenantId("t1")
                .userId("u1")
                .channel("xiaohongshu")
                .businessAgentId("agent-1")
                .agentId("main")
                .providerCode("openclaw")
                .enhancedPrompt("test prompt")
                .taskId("task-001")
                .callbackUrl("http://localhost:7088/api/generation/save")
                .build();

        assertEquals("t1", cmd.getTenantId());
        assertEquals("u1", cmd.getUserId());
        assertEquals("main", cmd.getAgentId());
        assertEquals("openclaw", cmd.getProviderCode());
    }

    @Test
    void shouldCreateSubmitResult() {
        SubmitResult result = SubmitResult.builder()
                .taskId("task-001")
                .status(SubmitResult.InvokeStatus.ACCEPTED)
                .message("OK")
                .build();

        assertTrue(result.isAccepted());
        assertEquals("task-001", result.getTaskId());
    }

    @Test
    void callbackRouterShouldDelegate() {
        AiAgentInvokerRouter router = new AiAgentInvokerRouter();
        CallbackRouter callbackRouter = new CallbackRouter(router);

        RawCallbackPayload payload = RawCallbackPayload.builder()
                .rawBody("{}")
                .providerCode("openclaw")
                .build();

        // No invokers registered — should throw
        assertThrows(AgentInvokeException.class, () -> callbackRouter.route(payload));
    }
}
