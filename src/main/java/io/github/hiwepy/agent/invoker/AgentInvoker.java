package io.github.hiwepy.agent.invoker;

/**
 * AI 智能体调用抽象。
 * <p>所有外部 AI 能力（OpenClaw / Spring AI / 自研模型）通过本接口接入，
 * 业务层不感知任何 Provider 细节。</p>
 *
 * @author wandl
 * @since 1.0.0
 */
public interface AgentInvoker {

    /** Provider 唯一标识（如 "openclaw"、"spring-ai"），用于多实现路由与可观测打点。 */
    String providerCode();

    /** 提交一次 AI 任务（异步）。 */
    SubmitResult submit(AgentInvokeCmd cmd);

    /** 取消一次进行中的任务。 */
    void cancel(String taskId);

    /** 处理 Provider 回调。实现方负责协议解析与字段翻译，向上仅返回业务语义结果。 */
    CallbackOutcome handleCallback(RawCallbackPayload payload);
}
