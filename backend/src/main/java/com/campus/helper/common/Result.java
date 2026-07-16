package com.campus.helper.common;

import lombok.Data;

@Data
public class Result {

    private Integer code;
    private String message;
    private Object data;

    private Result(Integer code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result success(Object data) {
        return new Result(200, "操作成功", data);
    }

    public static Result success() {
        return new Result(200, "操作成功", null);
    }

    public static Result error(Integer code, String message) {
        return new Result(code, message, null);
    }

    public static Result error(String message) {
        return new Result(500, message, null);
    }
}
