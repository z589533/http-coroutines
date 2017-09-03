package org.coroutines.tool.http.support;

import org.coroutines.tool.http.support.builder.HttpClientBuilder;

/**
 * @Project:http-coroutines
 * @PackageName:org.coroutines.tool.http.support
 * @Author:null z
 * @DateTime:2017-09-03 20:18.
 */
public class HttpClients {

    private HttpClients() {
        super();
    }

    public static HttpClientBuilder custom() {
        return HttpClientBuilder.create();
    }



}
