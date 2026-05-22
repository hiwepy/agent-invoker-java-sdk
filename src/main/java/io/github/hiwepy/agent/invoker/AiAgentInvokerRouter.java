package io.github.hiwepy.agent.invoker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI Agent 调用路由器。根据 providerCode 选择对应的 Provider 实现。
 *
 * <p>使用方式：
 * <pre>
 * Router router = new AiAgentInvokerRouter();
 * router.setDefaultProvider("openclaw");
 * router.register(new OpenClawAiAgentInvoker(...));
 * AiAgentInvoker invoker = router.route("openclaw");
 * </pre>
 */
public class AiAgentInvokerRouter {

    private static final String FALLBACK_DEFAULT_PROVIDER = "openclaw";

    private final List<AiAgentInvoker> invokers = new ArrayList<AiAgentInvoker>();
    private String defaultProvider = FALLBACK_DEFAULT_PROVIDER;

    public AiAgentInvokerRouter() {}

    public AiAgentInvokerRouter(List<AiAgentInvoker> invokers) {
        if (invokers != null) {
            this.invokers.addAll(invokers);
        }
    }

    /**
     * 设置默认 Provider 代码（如 {@code agents.provider.default-provider}）。
     *
     * @param defaultProvider provider 标识；null 或空字符串时回退为 {@code openclaw}
     */
    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = (defaultProvider == null || defaultProvider.isEmpty())
                ? FALLBACK_DEFAULT_PROVIDER
                : defaultProvider;
    }

    /** 返回当前默认 Provider 代码。 */
    public String getDefaultProvider() {
        return defaultProvider;
    }

    /** 注册一个 Provider 实现。 */
    public void register(AiAgentInvoker invoker) {
        this.invokers.add(Objects.requireNonNull(invoker, "invoker"));
    }

    /** 根据 providerCode 路由；null 或空字符串回退到 {@link #getDefaultProvider()}。 */
    public AiAgentInvoker route(String providerCode) {
        String code = (providerCode == null || providerCode.isEmpty()) ? defaultProvider : providerCode;
        return invokers.stream()
                .filter(it -> it.providerCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AgentInvokeException("No AiAgentInvoker for provider: " + code));
    }

    /** 根据 cmd 中的 providerCode 路由。 */
    public AiAgentInvoker route(AgentInvokeCmd cmd) {
        return route(cmd != null ? cmd.getProviderCode() : null);
    }

    public List<AiAgentInvoker> getInvokers() { return invokers; }
}
