package io.github.hiwepy.agent.invoker.openclaw;

import io.github.hiwepy.agent.invoker.AgentInvokeCmd;
import io.github.hiwepy.agent.invoker.AgentInvoker;
import io.github.hiwepy.agent.invoker.CallbackOutcome;
import io.github.hiwepy.agent.invoker.RawCallbackPayload;
import io.github.hiwepy.agent.invoker.SubmitResult;
import io.github.hiwepy.openclaw.InvokeAgentRequest;
import io.github.hiwepy.openclaw.InvokeAgentResult;
import io.github.hiwepy.openclaw.OpenClawClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OpenClaw 适配器：实现 {@link AgentInvoker}，将业务语义翻译为 OpenClaw Gateway Webhook 协议。
 *
 * <p>依赖 {@code openclaw-java-sdk}（optional），仅在类路径存在 OpenClawClient 时可用。</p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Slf4j
public class OpenClawAgentInvoker implements AgentInvoker {

    public static final String PROVIDER_CODE = "openclaw";

    private final OpenClawClient openClawClient;
    private final OpenClawCallbackParser callbackParser;
    private final String callbackBaseUrl;
    private final ConcurrentMap<String, String> taskIdToRunId = new ConcurrentHashMap<String, String>();

    /**
     * @param openClawClient   OpenClaw SDK 客户端
     * @param callbackBaseUrl  adapter 级 callback 基础 URL（可被 {@link AgentInvokeCmd#getCallbackUrl()} 覆盖）
     */
    public OpenClawAgentInvoker(OpenClawClient openClawClient,
                                String callbackBaseUrl) {
        this.openClawClient = Objects.requireNonNull(openClawClient, "openClawClient");
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
            InvokeAgentRequest request = OpenClawInvokeRequestMapper.toInvokeRequest(cmd, callbackBaseUrl);
            String callbackUrl = OpenClawInvokeRequestMapper.resolveCallbackUrl(cmd, callbackBaseUrl);
            String peerId = OpenClawInvokeRequestMapper.resolvePeerId(cmd);
            OpenClawInvokeRequestMapper.HookSessionStrategy strategy =
                    OpenClawInvokeRequestMapper.resolveSessionStrategy(cmd);
            log.debug("OpenClaw invoke, taskId={}, agentId={}, peerId={}, sessionStrategy={}, callbackUrl={}",
                    taskId, agentId, peerId, strategy, callbackUrl);

            InvokeAgentResult result = invokeWithSessionStrategy(cmd, request, peerId, strategy);
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
            if (taskId != null && result.getRunId() != null) {
                taskIdToRunId.put(taskId, result.getRunId());
            }
            log.info("OpenClaw invoke success, taskId={}, runId={}, callbackUrl={}",
                    taskId, result.getRunId(), callbackUrl);
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

    /**
     * 取消进行中的任务。
     *
     * <p>OpenClaw HTTP SDK 当前未暴露 Gateway run-cancel API；本方法在已知 {@code taskId → runId}
     * 映射时通过 {@link OpenClawClient#wake(String, String)} 注入取消信号作为 best-effort，
     * 否则记录明确警告日志。</p>
     */
    @Override
    public void cancel(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            log.warn("OpenClaw cancel ignored: taskId is empty");
            return;
        }
        String runId = taskIdToRunId.remove(taskId);
        if (runId == null) {
            log.warn("OpenClaw cancel: no runId mapped for taskId={}. "
                    + "OpenClaw HTTP SDK has no run-cancel endpoint; cancel is not applied.", taskId);
            return;
        }
        log.info("OpenClaw cancel requested: taskId={}, runId={}. "
                + "Applying best-effort wake cancel signal (HTTP SDK has no native run-cancel API).", taskId, runId);
        try {
            openClawClient.wake("Cancel agent run " + runId + " for business task " + taskId, "now");
            log.info("OpenClaw cancel wake signal sent for taskId={}, runId={}", taskId, runId);
        } catch (Exception e) {
            log.error("OpenClaw cancel failed for taskId={}, runId={}: {}. "
                    + "Gateway run-cancel is not available in openclaw-java-sdk HTTP client.",
                    taskId, runId, e.getMessage(), e);
        }
    }

    /**
     * 按会话策略调用 {@link OpenClawClient} 便捷方法，与 {@link OpenClawSessionKeys} 语义一致。
     *
     * @param cmd       业务命令
     * @param request   已组装的 Hook 请求（不含动态 sessionKey，{@link OpenClawInvokeRequestMapper.HookSessionStrategy#EXPLICIT} 除外）
     * @param peerId    业务 peer（可为 null）
     * @param strategy  会话策略
     * @return Gateway 响应
     */
    private InvokeAgentResult invokeWithSessionStrategy(AgentInvokeCmd cmd,
                                                        InvokeAgentRequest request,
                                                        String peerId,
                                                        OpenClawInvokeRequestMapper.HookSessionStrategy strategy) {
        return switch (strategy) {
            case ONE_SHOT -> openClawClient.agentOneShot(request);
            case EPHEMERAL_PEER -> openClawClient.agentOneShotForPeer(requirePeerId(peerId), request);
            case EPHEMERAL_PEER_WITH_CORRELATION ->
                    openClawClient.agentOneShotForPeer(requirePeerId(peerId), cmd.getTaskId().trim(), request);
            case STABLE -> openClawClient.agentWithStableSession(cmd.getAgentId(), requirePeerId(peerId), request);
            case EXPLICIT -> openClawClient.agent(request);
        };
    }

    /**
     * 校验 peerId 非空；策略要求 peer 但命令未提供时回退为匿名一次性 Hook 的语义由 mapper 保证，此处作防御性校验。
     */
    private static String requirePeerId(String peerId) {
        if (peerId == null || peerId.isEmpty()) {
            throw new IllegalArgumentException("peerId is required for this session strategy");
        }
        return peerId;
    }

    @Override
    public CallbackOutcome handleCallback(RawCallbackPayload payload) {
        return callbackParser.parse(payload);
    }

}
