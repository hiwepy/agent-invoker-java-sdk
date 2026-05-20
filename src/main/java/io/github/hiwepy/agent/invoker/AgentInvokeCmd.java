package io.github.hiwepy.agent.invoker;

import java.util.Map;

/**
 * 业务语义的 AI 调用命令。不含任何 Provider 字段，由 adapter 层负责翻译为 Provider 协议。
 *
 * @author wandl
 * @since 1.0.0
 */
public class AgentInvokeCmd {

    private String tenantId;
    private String userId;
    private String channel;
    private String businessAgentId;
    /** 目标 Agent 标识，由 adapter 翻译为各 Provider 协议中的 agent 字段。 */
    private String agentId;
    private String providerCode;
    private String enhancedPrompt;
    private Map<String, Object> variables;
    private String callbackToken;
    private String taskId;
    private String callbackUrl;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getBusinessAgentId() { return businessAgentId; }
    public void setBusinessAgentId(String businessAgentId) { this.businessAgentId = businessAgentId; }

    /** 返回目标 Agent 标识。 */
    public String getAgentId() { return agentId; }

    /** 设置目标 Agent 标识。 */
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getEnhancedPrompt() { return enhancedPrompt; }
    public void setEnhancedPrompt(String enhancedPrompt) { this.enhancedPrompt = enhancedPrompt; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AgentInvokeCmd cmd = new AgentInvokeCmd();
        public Builder tenantId(String v) { cmd.tenantId = v; return this; }
        public Builder userId(String v) { cmd.userId = v; return this; }
        public Builder channel(String v) { cmd.channel = v; return this; }
        public Builder businessAgentId(String v) { cmd.businessAgentId = v; return this; }
        /** 设置目标 Agent 标识。 */
        public Builder agentId(String v) { cmd.agentId = v; return this; }
        public Builder providerCode(String v) { cmd.providerCode = v; return this; }
        public Builder enhancedPrompt(String v) { cmd.enhancedPrompt = v; return this; }
        public Builder variables(Map<String, Object> v) { cmd.variables = v; return this; }
        public Builder callbackToken(String v) { cmd.callbackToken = v; return this; }
        public Builder taskId(String v) { cmd.taskId = v; return this; }
        public Builder callbackUrl(String v) { cmd.callbackUrl = v; return this; }
        public AgentInvokeCmd build() { return cmd; }
    }
}
