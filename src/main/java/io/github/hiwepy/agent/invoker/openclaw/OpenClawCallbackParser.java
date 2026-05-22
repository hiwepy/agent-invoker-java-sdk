package io.github.hiwepy.agent.invoker.openclaw;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hiwepy.agent.invoker.CallbackOutcome;
import io.github.hiwepy.agent.invoker.RawCallbackPayload;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * OpenClaw 回调解析器。将 OpenClaw Webhook 回调 JSON 解析为业务语义的 {@link CallbackOutcome}。
 */
@Slf4j
public class OpenClawCallbackParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CallbackOutcome parse(RawCallbackPayload payload) {
        String rawBody = payload.getRawBody();
        if (rawBody == null || rawBody.isEmpty()) {
            return CallbackOutcome.builder()
                    .status(CallbackOutcome.CallbackStatus.FAILED)
                    .errorMessage("Empty callback body")
                    .build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(rawBody, Map.class);
            if (map == null) {
                return CallbackOutcome.builder()
                        .status(CallbackOutcome.CallbackStatus.FAILED)
                        .errorMessage("Failed to parse callback body")
                        .build();
            }

            String taskId = stringValue(map, "task_id");
            String apiKey = stringValue(map, "api_key");
            // api_key 可从 query params 补充
            if ((apiKey == null || apiKey.isEmpty()) && payload.getQueryParams() != null) {
                apiKey = payload.getQueryParams().get("api_key");
            }

            return CallbackOutcome.builder()
                    .taskId(taskId)
                    .apiKey(apiKey)
                    .status(CallbackOutcome.CallbackStatus.SUCCESS)
                    .title(stringValue(map, "title"))
                    .subtitle(stringValue(map, "subtitle"))
                    .content(stringValue(map, "content"))
                    .coverUrl(stringValue(map, "cover_url"))
                    .imageUrls(stringListValue(map, "image_urls"))
                    .tags(stringListValue(map, "tags"))
                    .topics(stringListValue(map, "topics"))
                    .sourceChannel(stringValue(map, "source_channel"))
                    .outputType(stringValue(map, "output_type"))
                    .imagePrompt(stringValue(map, "image_prompt"))
                    .extra(mapValue(map, "extra"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse OpenClaw callback payload", e);
            return CallbackOutcome.builder()
                    .status(CallbackOutcome.CallbackStatus.FAILED)
                    .errorMessage("Callback parse error: " + e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) return (List<String>) v;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return null;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
