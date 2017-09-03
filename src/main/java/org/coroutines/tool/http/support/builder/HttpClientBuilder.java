package org.coroutines.tool.http.support.builder;

import org.coroutines.tool.http.support.HttpCoroutinesClient;

/**
 * @Project:http-coroutines
 * @PackageName:org.coroutines.tool.http.support.builder
 * @Author:null z
 * @DateTime:2017-09-03 20:23.
 */
public class HttpClientBuilder {


    public static HttpClientBuilder create() {
        return new HttpClientBuilder();
    }


    private int maxTotal = 10;

    private int maxpPerroute = 5;

    private int executionCountHttp = 0;


    /**
     *       *返回请求连接时使用的超时（毫秒）
     *       *从连接管理器。 解释超时值为零
     *       *作为无限超时。
     *       *
     * <p>
     * <p>
     *       *超时值为零被解释为无限超时。
     *       *负值解释为未定义（系统默认值）。
     *       *  p>
     *       *
     * <p>
     * <p>
     *       *默认值：`-1`
     *       *  p>
     *      
     */
    private int requestTimeout = -1;
    /**
     *       *定义套接字超时（`SO_TIMEOUT`），单位为毫秒，
     *       *这是等待数据的超时时间，换句话说，
     *       *两个连续数据包之间的最长时间不活动）。
     *       *
     * <p>
     * <p>
     *       *超时值为零被解释为无限超时。
     *       *负值解释为未定义（系统默认值）。
     *       *  p>
     *       *
     * <p>
     * <p>
     *       *默认值：`-1`
     *       *  p>
     *      
     */
    private int socketTimeout = -1;//


    /**
     *       *确定连接建立之前的超时时间（以毫秒为单位）。
     *       *超时值为零被解释为无限超时。
     *       *
     * <p>
     * <p>
     *       *超时值为零被解释为无限超时。
     *       *负值解释为未定义（系统默认值）。
     *       *  p>
     *       *
     * <p>
     * <p>
     *       *默认值：`-1`
     *       *  p>
     *      
     */
    private int connectTimeout = -1;//


    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public int getMaxpPerroute() {
        return maxpPerroute;
    }

    public void setMaxpPerroute(int maxpPerroute) {
        this.maxpPerroute = maxpPerroute;
    }

    public int getExecutionCountHttp() {
        return executionCountHttp;
    }

    public void setExecutionCountHttp(int executionCountHttp) {
        this.executionCountHttp = executionCountHttp;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public HttpCoroutinesClient build() {
        return new HttpCoroutinesClient(maxTotal, maxpPerroute, executionCountHttp, requestTimeout, connectTimeout, socketTimeout);
    }
}
