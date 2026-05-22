package io.github.hiwepy.agent.invoker;

/**
 * AI Agent 回调路由器。根据 providerCode 将原始回调载荷路由到对应的 Provider adapter。
 */
public class CallbackRouter {

    private final AgentInvokerRouter invokerRouter;

    public CallbackRouter(AgentInvokerRouter invokerRouter) {
        this.invokerRouter = java.util.Objects.requireNonNull(invokerRouter, "invokerRouter");
    }

    /**
     * 根据 providerCode 路由回调到对应 adapter 解析。
     * 未指定 providerCode 时默认走 openclaw。
     */
    public CallbackOutcome route(RawCallbackPayload payload) {
        String code = payload.getProviderCode();
        if (code == null || code.isEmpty()) {
            code = "openclaw";
            payload.setProviderCode(code);
        }
        AgentInvoker invoker = invokerRouter.route(code);
        return invoker.handleCallback(payload);
    }
}
