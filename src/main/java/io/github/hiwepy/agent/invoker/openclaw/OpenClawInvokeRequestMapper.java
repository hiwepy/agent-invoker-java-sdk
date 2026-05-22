package io.github.hiwepy.agent.invoker.openclaw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hiwepy.agent.invoker.AgentInvokeCmd;
import io.github.hiwepy.openclaw.InvokeAgentRequest;
import io.github.hiwepy.openclaw.OpenClawSessionKeys;

import java.util.Map;
import java.util.Objects;

/**
 * 将业务层 {@link AgentInvokeCmd} 翻译为 OpenClaw {@link InvokeAgentRequest}。
 *
 * <p>callbackUrl 优先取自 {@link AgentInvokeCmd#getCallbackUrl()}，否则回退到 adapter 配置的
 * {@code callbackBaseUrl}，并写入 agent 提示词供 OpenClaw 回写业务系统（Gateway Hooks 协议无独立 callbackUrl 字段）。</p>
 * <p>会话策略与 {@link OpenClawSessionKeys} / {@link io.github.hiwepy.openclaw.OpenClawClient} 便捷方法对齐，
 * 由 {@link #resolveSessionStrategy(AgentInvokeCmd)} 决定 {@link OpenClawAgentInvoker} 调用
 * {@code agentOneShot}、{@code agentOneShotForPeer} 或 {@code agentWithStableSession}。</p>
 * <p>其余 Hook 可选字段（{@code deliver}、{@code model}、{@code thinking}、{@code wakeMode}、
 * {@code timeoutSeconds}、{@code name}、{@code sessionMode}、{@code sessionKey}）可通过
 * {@link AgentInvokeCmd#getVariables()} 中 {@code openclaw.*} 键传入，避免扩展业务命令模型。</p>
 */
public final class OpenClawInvokeRequestMapper {

    /**
     * OpenClaw Hook 会话策略，对应 {@link io.github.hiwepy.openclaw.OpenClawClient} 便捷方法。
     */
    public enum HookSessionStrategy {
        /** 无 peer：Gateway 生成 {@code hook:<uuid>}，对应 {@code agentOneShot} */
        ONE_SHOT,
        /** 有 peer、无固定 correlation：{@code hook:<peerId>:<uuid>}，对应 {@code agentOneShotForPeer(peerId, request)} */
        EPHEMERAL_PEER,
        /** 有 peer + taskId：{@code hook:<peerId>:<taskId>}，对应 {@code agentOneShotForPeer(peerId, taskId, request)} */
        EPHEMERAL_PEER_WITH_CORRELATION,
        /** 多轮固定会话：{@code hook:<agentId>:<peerId>}，对应 {@code agentWithStableSession} */
        STABLE,
        /** 显式 {@code openclaw.sessionKey}，对应底层 {@code agent(request)} */
        EXPLICIT
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VAR_PREFIX = "openclaw.";

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
        request.setName(stringVariable(cmd, "name", "Generation"));
        request.setWakeMode(stringVariable(cmd, "wakeMode", "now"));
        request.setTimeoutSeconds(intVariable(cmd, "timeoutSeconds", 300));

        String hookChannel = stringVariable(cmd, "channel", null);
        if (hasText(hookChannel)) {
            request.setChannel(hookChannel);
        } else if (hasText(cmd.getChannel())) {
            request.setChannel(cmd.getChannel());
        }
        String hookTo = stringVariable(cmd, "to", null);
        if (hasText(hookTo)) {
            request.setTo(hookTo);
        } else if (hasText(cmd.getUserId())) {
            request.setTo(cmd.getUserId());
        }
        Boolean deliver = booleanVariable(cmd, "deliver");
        if (deliver != null) {
            request.setDeliver(deliver);
        }
        String model = stringVariable(cmd, "model", null);
        if (hasText(model)) {
            request.setModel(model);
        }
        String thinking = stringVariable(cmd, "thinking", null);
        if (hasText(thinking)) {
            request.setThinking(thinking);
        }
        if (resolveSessionStrategy(cmd) == HookSessionStrategy.EXPLICIT) {
            String sessionKey = readVariable(cmd, "sessionKey");
            if (hasText(sessionKey)) {
                request.setSessionKey(sessionKey.trim());
            }
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
     * 解析 Hook 会话策略，供 {@link OpenClawAgentInvoker} 选择 {@link io.github.hiwepy.openclaw.OpenClawClient} 便捷方法。
     *
     * <p>默认规则：</p>
     * <ul>
     *     <li>{@code taskId} + peer → 一次性任务（correlation = taskId）</li>
     *     <li>{@code businessAgentId} + peer + {@code agentId}、无 taskId → 固定多轮</li>
     *     <li>仅有 peer → 每次新 UUID 的一次性 Hook</li>
     *     <li>无 peer → Gateway 匿名一次性 Hook</li>
     * </ul>
     * <p>可通过 {@code openclaw.sessionMode} 覆盖：{@code stable}、{@code ephemeral}、{@code none}；
     * {@code openclaw.sessionKey} 则走 {@link HookSessionStrategy#EXPLICIT}。</p>
     */
    public static HookSessionStrategy resolveSessionStrategy(AgentInvokeCmd cmd) {
        if (cmd == null) {
            return HookSessionStrategy.ONE_SHOT;
        }
        if (hasText(readVariable(cmd, "sessionKey"))) {
            return HookSessionStrategy.EXPLICIT;
        }
        String peerId = resolvePeerId(cmd);
        String mode = stringVariable(cmd, "sessionMode", null);
        if (hasText(mode)) {
            return mapSessionMode(mode.trim(), cmd, peerId);
        }
        return defaultSessionStrategy(cmd, peerId);
    }

    /**
     * 按 {@link OpenClawSessionKeys} 约定构造 sessionKey（便于测试与日志）；动态 UUID 场景返回 {@code null}。
     */
    public static String buildSessionKey(AgentInvokeCmd cmd) {
        if (cmd == null) {
            return null;
        }
        String explicit = readVariable(cmd, "sessionKey");
        if (hasText(explicit)) {
            return explicit.trim();
        }
        String peerId = resolvePeerId(cmd);
        try {
            switch (resolveSessionStrategy(cmd)) {
                case ONE_SHOT:
                case EPHEMERAL_PEER:
                case EXPLICIT:
                    return null;
                case EPHEMERAL_PEER_WITH_CORRELATION:
                    return OpenClawSessionKeys.forEphemeralPeer(peerId, cmd.getTaskId().trim());
                case STABLE:
                    return OpenClawSessionKeys.forStableSession(cmd.getAgentId(), peerId);
                default:
                    return null;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 解析业务 peerId（OpenClaw Hook 第二段，非路由 agentId）：{@code tenantId.userId.businessAgentId} 组合。
     */
    public static String resolvePeerId(AgentInvokeCmd cmd) {
        if (cmd == null) {
            return null;
        }
        StringBuilder peer = new StringBuilder();
        appendPeerSegment(peer, cmd.getTenantId());
        appendPeerSegment(peer, cmd.getUserId());
        appendPeerSegment(peer, cmd.getBusinessAgentId());
        return peer.length() > 0 ? peer.toString() : null;
    }

    /**
     * 将 {@code openclaw.sessionMode} 映射为 Hook 会话策略。
     */
    private static HookSessionStrategy mapSessionMode(String mode, AgentInvokeCmd cmd, String peerId) {
        String normalized = mode.toLowerCase();
        if ("stable".equals(normalized) || "multi".equals(normalized) || "multi-turn".equals(normalized)) {
            return hasText(peerId) && hasText(cmd.getAgentId())
                    ? HookSessionStrategy.STABLE
                    : defaultSessionStrategy(cmd, peerId);
        }
        if ("ephemeral".equals(normalized) || "oneshot".equals(normalized) || "one-shot".equals(normalized)) {
            if (hasText(peerId) && hasText(cmd.getTaskId())) {
                return HookSessionStrategy.EPHEMERAL_PEER_WITH_CORRELATION;
            }
            return hasText(peerId) ? HookSessionStrategy.EPHEMERAL_PEER : HookSessionStrategy.ONE_SHOT;
        }
        if ("none".equals(normalized) || "anonymous".equals(normalized)) {
            return HookSessionStrategy.ONE_SHOT;
        }
        return defaultSessionStrategy(cmd, peerId);
    }

    /**
     * 无显式 sessionMode 时的默认策略。
     */
    private static HookSessionStrategy defaultSessionStrategy(AgentInvokeCmd cmd, String peerId) {
        if (hasText(peerId) && hasText(cmd.getTaskId())) {
            return HookSessionStrategy.EPHEMERAL_PEER_WITH_CORRELATION;
        }
        if (hasText(peerId) && hasText(cmd.getBusinessAgentId()) && hasText(cmd.getAgentId())) {
            return HookSessionStrategy.STABLE;
        }
        if (hasText(peerId)) {
            return HookSessionStrategy.EPHEMERAL_PEER;
        }
        return HookSessionStrategy.ONE_SHOT;
    }

    /**
     * 向 peerId 追加一段（tenant / user / businessAgent），以 {@code .} 分隔。
     */
    private static void appendPeerSegment(StringBuilder peer, String segment) {
        if (!hasText(segment)) {
            return;
        }
        if (peer.length() > 0) {
            peer.append('.');
        }
        peer.append(segment.trim());
    }

    private static String stringVariable(AgentInvokeCmd cmd, String key, String defaultValue) {
        String value = readVariable(cmd, key);
        return hasText(value) ? value : defaultValue;
    }

    private static String readVariable(AgentInvokeCmd cmd, String key) {
        if (cmd == null || cmd.getVariables() == null || key == null) {
            return null;
        }
        Object value = cmd.getVariables().get(VAR_PREFIX + key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Boolean booleanVariable(AgentInvokeCmd cmd, String key) {
        if (cmd == null || cmd.getVariables() == null || key == null) {
            return null;
        }
        Object value = cmd.getVariables().get(VAR_PREFIX + key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }

    private static int intVariable(AgentInvokeCmd cmd, String key, int defaultValue) {
        if (cmd == null || cmd.getVariables() == null || key == null) {
            return defaultValue;
        }
        Object value = cmd.getVariables().get(VAR_PREFIX + key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
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
