package vn.erg.explorer.web;

import lombok.Getter;
import lombok.Setter;

/**
 * Envelope of every REST answer: {@code { "data": ... }} on success, {@code { "errorCode": 404, "error": "..." }}
 * on failure. {@link JsonModule} copies {@code errorCode} to the HTTP status when it is set.
 */
@Getter
@Setter
public class JsonResult {

    private Integer errorCode;

    private Object error;

    private Object data;

    public JsonResult() {
    }

    public JsonResult(Object data) {
        this.data = data;
    }

    public static JsonResult ok(Object data) {
        return new JsonResult(data);
    }

    public static JsonResult error(Object error, int errorCode) {
        JsonResult result = new JsonResult();
        result.setError(error);
        result.setErrorCode(errorCode);
        return result;
    }

    public static JsonResult notfound() {
        return error("404 Not found", 404);
    }

}
