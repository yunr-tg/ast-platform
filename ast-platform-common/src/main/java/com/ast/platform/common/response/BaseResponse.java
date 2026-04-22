package com.ast.platform.common.response;

public record BaseResponse<T>(boolean success, String code, String message, T data) {

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, "OK", "success", data);
    }

    public static <T> BaseResponse<T> failure(String code, String message) {
        return new BaseResponse<>(false, code, message, null);
    }
}
