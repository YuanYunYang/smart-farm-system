package com.farm.smart.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一API响应结果封装
 *
 * @param <T> 数据载荷类型
 * @author Smart Farm Team
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /** 时间戳 */
    private Long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应 (带数据)
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 成功响应 (无数据)
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMsg(resultCode.getMessage());
        return result;
    }

    /**
     * 失败响应 (自定义消息)
     */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败响应 (使用ResultCode + 自定义消息)
     */
    public static <T> Result<T> error(ResultCode resultCode, String msg) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMsg(msg);
        return result;
    }
}
