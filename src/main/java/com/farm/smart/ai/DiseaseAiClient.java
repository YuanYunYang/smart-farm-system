package com.farm.smart.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.smart.model.vo.DiseaseResultVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 病虫害识别 AI 客户端
 * <p>
 * 调用外部 AI 服务进行病虫害识别:
 * - 本地部署: YOLOv8 + FastAPI HTTP 接口
 * - 云端部署: 可对接百度/阿里 AI 开放平台图像识别 API
 * <p>
 * 本地 YOLOv8 服务返回格式示例:
 * <pre>
 * {
 *   "success": true,
 *   "model": "yolov8n-agri-disease-v1",
 *   "detections": [
 *     {
 *       "class_name": "tomato_early_blight",
 *       "confidence": 0.92,
 *       "bbox": [0.12, 0.15, 0.85, 0.90]
 *     }
 *   ]
 * }
 * </pre>
 *
 * @author Smart Farm Team
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "ai.disease")
public class DiseaseAiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** AI 服务地址 */
    private String baseUrl;

    /** 识别接口路径 */
    private String detectPath;

    /** 默认来源: local / cloud */
    private String defaultSource;

    /** 模型版本 */
    private String modelVersion;

    /** 请求超时 (毫秒) */
    private int timeout;

    /** 是否启用 (未启用时返回模拟结果) */
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用 AI 服务进行病虫害识别
     *
     * @param imageBase64 Base64 编码的图片
     * @param source      识别来源 (local/cloud)
     * @return 识别结果
     */
    public DiseaseResultVO detectDisease(String imageBase64, String source) {
        if (!enabled) {
            log.warn("AI 识别服务未启用, 返回模拟结果");
            return mockDetectionResult(imageBase64, source);
        }

        try {
            String url = baseUrl + detectPath;

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("image", imageBase64);
            requestBody.put("model_version", modelVersion);
            requestBody.put("source", source != null ? source : defaultSource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("调用 AI 识别服务: url={}, source={}", url, source);

            // 发送 POST 请求
            String response = restTemplate.postForObject(url, entity, String.class);

            // 解析返回结果
            return parseAiResponse(response, source);

        } catch (Exception e) {
            log.error("AI 识别服务调用失败: {}", e.getMessage(), e);
            // 返回降级结果
            DiseaseResultVO result = new DiseaseResultVO();
            result.setDiseaseName("识别失败");
            result.setConfidence(0.0);
            result.setConfidencePercent("0%");
            result.setSuggestion("AI 服务暂不可用, 请稍后重试或人工检查");
            result.setSource(source);
            return result;
        }
    }

    /**
     * 批量识别
     */
    public List<DiseaseResultVO> batchDetect(List<String> imageBase64List, String source) {
        List<DiseaseResultVO> results = new ArrayList<>();
        for (String image : imageBase64List) {
            results.add(detectDisease(image, source));
        }
        return results;
    }

    /**
     * 解析 AI 服务返回的 JSON 响应
     */
    @SuppressWarnings("unchecked")
    private DiseaseResultVO parseAiResponse(String response, String source) {
        try {
            Map<String, Object> map = objectMapper.readValue(response, Map.class);
            Boolean success = (Boolean) map.get("success");
            String model = (String) map.getOrDefault("model", modelVersion);

            DiseaseResultVO result = new DiseaseResultVO();
            result.setSource(source != null ? source : defaultSource);
            result.setModelVersion(model);

            if (Boolean.TRUE.equals(success) && map.containsKey("detections")) {
                List<Map<String, Object>> detections = (List<Map<String, Object>>) map.get("detections");

                List<DiseaseResultVO.DetectionItem> items = new ArrayList<>();
                for (Map<String, Object> det : detections) {
                    DiseaseResultVO.DetectionItem item = new DiseaseResultVO.DetectionItem();
                    item.setClassName((String) det.get("class_name"));
                    Double confidence = ((Number) det.get("confidence")).doubleValue();
                    item.setConfidence(confidence);
                    item.setSuggestion(getSuggestionByDisease((String) det.get("class_name")));

                    if (det.containsKey("bbox")) {
                        List<Double> bbox = new ArrayList<>();
                        for (Object coord : (List<?>) det.get("bbox")) {
                            bbox.add(((Number) coord).doubleValue());
                        }
                        item.setBbox(bbox);
                    }
                    items.add(item);
                }
                result.setDetections(items);

                // 取第一个检测结果作为主结果
                if (!items.isEmpty()) {
                    DiseaseResultVO.DetectionItem first = items.get(0);
                    result.setDiseaseName(translateDiseaseName(first.getClassName()));
                    result.setConfidence(first.getConfidence());
                    result.setConfidencePercent(String.format("%.1f%%", first.getConfidence() * 100));
                    result.setSuggestion(first.getSuggestion());
                }
            } else {
                result.setDiseaseName("未检测到病害");
                result.setConfidence(0.0);
                result.setConfidencePercent("0%");
                result.setSuggestion("植株状态正常, 继续保持");
            }

            return result;

        } catch (Exception e) {
            log.error("解析 AI 返回结果失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 返回结果解析失败", e);
        }
    }

    /**
     * 根据 AI 模型分类名获取处理建议
     * 覆盖常见农业病害
     */
    private String getSuggestionByDisease(String className) {
        if (className == null) return "建议人工检查";
        String lower = className.toLowerCase();
        if (lower.contains("early_blight")) {
            return "番茄早疫病: 喷施代森锰锌或苯醚甲环唑, 清除病叶, 加强通风";
        } else if (lower.contains("late_blight")) {
            return "番茄晚疫病: 紧急喷施霜脲氰或烯酰吗啉, 避免大水漫灌";
        } else if (lower.contains("powdery_mildew")) {
            return "白粉病: 喷施醚菌酯或吡唑醚菌酯, 降低棚内湿度";
        } else if (lower.contains("leaf_spot")) {
            return "叶斑病: 喷施咪鲜胺或多菌灵, 清除下部老叶";
        } else if (lower.contains("aphid") || lower.contains("pest")) {
            return "虫害: 喷施吡虫啉或阿维菌素, 悬挂黄板诱杀";
        } else if (lower.contains("healthy") || lower.contains("normal")) {
            return "植株状态正常, 继续保持当前管理";
        }
        return "建议人工确认并咨询农技专家";
    }

    /**
     * 将 AI 模型分类名翻译为中文
     */
    private String translateDiseaseName(String className) {
        if (className == null) return "未知";
        String lower = className.toLowerCase();
        if (lower.contains("early_blight")) return "番茄早疫病";
        if (lower.contains("late_blight")) return "番茄晚疫病";
        if (lower.contains("powdery_mildew")) return "白粉病";
        if (lower.contains("leaf_spot")) return "叶斑病";
        if (lower.contains("aphid")) return "蚜虫虫害";
        if (lower.contains("healthy") || lower.contains("normal")) return "正常/健康";
        return className;
    }

    /**
     * 模拟识别结果 (AI 服务未启用时返回, 方便开发测试)
     */
    private DiseaseResultVO mockDetectionResult(String imageBase64, String source) {
        DiseaseResultVO result = new DiseaseResultVO();
        result.setDiseaseName("番茄早疫病(模拟)");
        result.setConfidence(0.92);
        result.setConfidencePercent("92.0%");
        result.setModelVersion(modelVersion + " (mock)");
        result.setSuggestion("番茄早疫病: 喷施代森锰锌或苯醚甲环唑, 清除病叶, 加强通风 (模拟结果, AI服务未启用)");
        result.setSource(source != null ? source : defaultSource);

        DiseaseResultVO.DetectionItem item = new DiseaseResultVO.DetectionItem();
        item.setClassName("tomato_early_blight");
        item.setConfidence(0.92);
        item.setBbox(List.of(0.1, 0.15, 0.85, 0.9));
        item.setSuggestion(result.getSuggestion());
        result.setDetections(List.of(item));

        return result;
    }
}
