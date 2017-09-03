package org.coroutines.tool.http.exception;

/**
 * @Project:http-coroutines
 * @PackageName:org.coroutines.tool.http.exception
 * @Author:null z
 * @DateTime:2017-09-03 19:28.
 */
public class RetryHttpException extends RuntimeException {


    /**
     *
     */

    private String errCode;
    private String errMsg;

    public RetryHttpException() {
        super();
    }

    public RetryHttpException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryHttpException(String message) {
        super(message);
    }

    public RetryHttpException(Throwable cause) {
        super(cause);
    }

    public RetryHttpException(String errCode, String errMsg) {
        super(errCode + ":" + errMsg);
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public String getErrCode() {
        return this.errCode;
    }

    public String getErrMsg() {
        return this.errMsg;
    }
}
