package io.github.hiwepy.agent.invoker.openclaw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hiwepy.agent.invoker.AgentInvokeCmd;
import io.github.hiwepy.openclaw.InvokeAgentRequest;

import java.util.Map;
import java.util.Objects;

/**
 * 将业务层 {@link AgentInvokeCmd} 翻译为 OpenClaw {@link InvokeAgentRequest}。
 *
 * <p>callbackUrl 优先取自 {@link AgentInvokeCmd#getCallbackUrl()}，否则回退到 adapter 配置的
 * {@code callbackBaseUrl}，并写入 agent 提示词供 OpenClaw 回写业务系统（Gateway Hooks 协议无独立 callbackUrl 字段）。</p>
 */
public final class OpenClawInvokeRequestMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenClawInvokeRequestMapper() {
    }

    /**
     * 构建 OpenClaw Gateway {@code POST /hooks/agent} 请求体。
     *
     * @param cmd             业务调用命令
     * @param callbackBaseUrl adapter 级 callback 基础 URL（{@code agents.provider.openclaw.callback-base-url}）
     * @return 可直接交给 {@link io.github.hiwepy.openclaw.OpenClawClient#agent(InvokeAgentRequest)} 的请求
     */
    public static InvokeAgentRequest toInvokeRequest(AgentInvokeCmd cmd, String callbackBaseUrl) {
        Objects.requireNonNull(cmd, "cmd");
        InvokeAgentRequest request = new InvokeAgentRequest();
        request.setAgentId(cmd.getAgentId());
        request.setMessage(buildMessage(cmd, resolveCallbackUrl(cmd, callbackBaseUrl)));
        request.setName("Generation");
        request.setWakeMode("now");
        request.setTimeoutSeconds(300);

        if (hasText(cmd.getChannel())) {
            request.setChannel(cmd.getChannel());
        }
        if (hasText(cmd.getUserId())) {
            request.setTo(cmd.getUserId());
        }
        String sessionKey = buildSessionKey(cmd);
        if (sessionKey != null) {
            request.setSessionKey(sessionKey);
        }
        return request;
    }

    /**
     * 解析最终 callback URL：命令级覆盖 adapter 默认值。
     */
    public static String resolveCallbackUrl(AgentInvokeCmd cmd, String callbackBaseUrl) {
        if (cmd != null && hasText(cmd.getCallbackUrl())) {
            return cmd.getCallbackUrl().trim();
        }
        if (hasText(callbackBaseUrl)) {
            return callbackBaseUrl.trim();
        }
        return "http://localhost:7088";
    }

    /**
     * 组装 agent message：原始 prompt + callback 指令 + 租户/变量等元数据。
     */
    static String buildMessage(AgentInvokeCmd cmd, String callbackUrl) {
        StringBuilder message = new StringBuilder();
        if (hasText(cmd.getEnhancedPrompt())) {
            message.append(cmd.getEnhancedPrompt().trim());
        }

        message.append("\n\n---\n");
        message.append("When finished, POST the result JSON to callback URL: ").append(callbackUrl);
        if (hasText(cmd.getTaskId())) {
            message.append("\nInclude field task_id=\"").append(cmd.getTaskId()).append("\" in the JSON body.");
        }
        if (hasText(cmd.getCallbackToken())) {
            message.append("\nAuthenticate callback with api_key=\"").append(cmd.getCallbackToken()).append("\".");
        }
        if (hasText(cmd.getTenantId())) {
            message.append("\nTenant ID: ").append(cmd.getTenantId());
        }
        if (hasText(cmd.getUserId())) {
            message.append("\nUser ID: ").append(cmd.getUserId());
        }
        if (hasText(cmd.getChannel())) {
            message.append("\nChannel: ").append(cmd.getChannel());
        }
        Map<String, Object> variables = cmd.getVariables();
        if (variables != null && !variables.isEmpty()) {
            message.append("\nVariables (JSON):\n").append(toJson(variables));
        }
        return message.toString();
    }

    /**
     * 将会话维度编码为 sessionKey，便于 Gateway 侧关联同一业务任务。
     */
    static String buildSessionKey(AgentInvokeCmd cmd) {
        if (cmd == null) {
            return null;
        }
        StringBuilder key = new StringBuilder("invoker");
        if (hasText(cmd.getTenantId())) {
            key.append(':').append(cmd.getTenantId().trim());
        }
        if (hasText(cmd.getUserId())) {
            key.append(':').append(cmd.getUserId().trim());
        }
        if (hasText(cmd.getTaskId())) {
            key.append(':').append(cmd.getTaskId().trim());
        }
        return key.length() > "invoker".length() ? key.toString() : null;
    }

    private static String toJson(Map<String, Object> variables) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            return variables.toString();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
