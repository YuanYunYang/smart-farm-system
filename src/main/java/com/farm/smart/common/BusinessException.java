package com.farm.smart.common;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 携带 {@link ResultCode} 用于全局异常处理器统一转换为标准 Result 响应。
 * 当存在自定义消息时使用自定义消息, 否则使用 ResultCode 默认消息。
 *
 * @author Smart Farm Team
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;
    private final String customMessage;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.customMessage = null;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.customMessage = message;
    }

    /**
     * 获取最终用于响应的消息 (优先使用自定义消息)
     */
    public String getResponseMessage() {
        return customMessage != null ? customMessage : resultCode.getMessage();
    }
}
