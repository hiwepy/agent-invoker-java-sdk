package io.github.hiwepy.agent.invoker.openclaw;

import io.github.hiwepy.agent.invoker.AgentInvokeCmd;
import io.github.hiwepy.agent.invoker.AiAgentInvoker;
import io.github.hiwepy.agent.invoker.CallbackOutcome;
import io.github.hiwepy.agent.invoker.RawCallbackPayload;
import io.github.hiwepy.agent.invoker.SubmitResult;
import io.github.hiwepy.openclaw.InvokeAgentRequest;
import io.github.hiwepy.openclaw.InvokeAgentResult;
import io.github.hiwepy.openclaw.OpenClawClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenClaw 适配器：实现 {@link AiAgentInvoker}，将业务语义翻译为 OpenClaw Gateway Webhook 协议。
 *
 * <p>依赖 {@code openclaw-java-sdk}（optional），仅在类路径存在 OpenClawClient 时可用。</p>
 *
 * @author wandl
 * @since 1.0.0
 */
public class OpenClawAiAgentInvoker implements AiAgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(OpenClawAiAgentInvoker.class);

    public static final String PROVIDER_CODE = "openclaw";

    private final OpenClawClient openClawClient;
    private final OpenClawCallbackParser callbackParser;
    private final String callbackBaseUrl;

    public OpenClawAiAgentInvoker(OpenClawClient openClawClient,
                                  String callbackBaseUrl) {
        this.openClawClient = java.util.Objects.requireNonNull(openClawClient, "openClawClient");
        this.callbackBaseUrl = callbackBaseUrl != null ? callbackBaseUrl : "http://localhost:7088";
        this.callbackParser = new OpenClawCallbackParser();
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SubmitResult submit(AgentInvokeCmd cmd) {
        String taskId = cmd.getTaskId();
        String agentId = cmd.getAgentId();
        if (agentId == null || agentId.isEmpty()) {
            return SubmitResult.builder()
                    .taskId(taskId)
                    .status(SubmitResult.InvokeStatus.REJECTED)
                    .message("agentId is required")
                    .build();
        }

        try {
            InvokeAgentRequest request = new InvokeAgentRequest();
            request.setAgentId(agentId);
            request.setMessage(cmd.getEnhancedPrompt());
            request.setName("Generation");
            request.setWakeMode("now");
            request.setTimeoutSeconds(300);

            InvokeAgentResult result = openClawClient.agent(request);
            if (result == null || !result.isSuccess()) {
                int status = result != null ? result.getHttpStatus() : -1;
                String body = result != null ? result.getRawBody() : null;
                log.warn("OpenClaw invoke failed, taskId={}, status={}, body={}", taskId, status, body);
                return SubmitResult.builder()
                        .taskId(taskId)
                        .status(SubmitResult.InvokeStatus.REJECTED)
                        .message("OpenClaw invoke failed, status=" + status)
                        .build();
            }
            log.info("OpenClaw invoke success, taskId={}, runId={}", taskId, result.getRunId());
            return SubmitResult.builder()
                    .taskId(taskId)
                    .providerTaskId(result.getRunId())
                    .status(SubmitResult.InvokeStatus.ACCEPTED)
                    .message("OK")
                    .build();
        } catch (Exception e) {
            log.error("OpenClaw invoke error for task: {}", taskId, e);
            return SubmitResult.builder()
                    .taskId(taskId)
                    .status(SubmitResult.InvokeStatus.REJECTED)
                    .message("OpenClaw invoke error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public void cancel(String taskId) {
        log.info("OpenClaw task cancel requested: {}", taskId);
    }

    @Override
    public CallbackOutcome handleCallback(RawCallbackPayload payload) {
        return callbackParser.parse(payload);
    }

    /** 当前适配器使用的 callbackBaseUrl。 */
    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }
}
