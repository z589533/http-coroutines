package org.coroutines.tool.http;

import com.alibaba.fastjson.JSONObject;
import org.coroutines.tool.http.response.HttpResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 实现接口必须全部实现TLSv1, TLSv1.1, TLSv1.2
 * @Project:http-coroutines
 * @PackageName:org.coroutines.tool.http
 * @Author:null z
 * @DateTime:2017-09-03 19:17.
 */
public interface HttpInterface {



    /**
     * 异步发送Post请求
     */
    void asynSendPost(String url, Map<String, String> paramMap, Map<String,String>... requestHead) throws Exception;


    /**
     * 异步发送Post请求
     */
    void asynSendPost(String url, JSONObject paramJson, Map<String,String>... requestHead) throws Exception;

    /**
     * 同步发送post请求
     *
     * @param url
     * @param paramMap
     * @return
     */
    HttpResponse synSendPost(String url, Map<String, String> paramMap, Map<String,String>... requestHead) throws Exception;


    /**
     * 同步发送post请求
     *
     * @param url
     * @param paramMap
     * @return
     */
    HttpResponse synSendPost(String url, Map<String, String> paramMap,Boolean ssl,Map<String,String>... requestHead) throws Exception;


    /**
     * 同步发送post请求
     *
     * @param url
     * @param paramMap
     * @return
     */
    HttpResponse synSendPost(String url, Map<String, String> paramMap, long timeout, TimeUnit unit, Map<String,String>... requestHead) throws Exception;


    /**
     * 同步发送post请求
     *
     * @param url
     * @return
     */
    HttpResponse synSendUrlPost(String url,Map<String,String>... requestHead) throws Exception;


    /**
     * 同步发送post请求
     *
     * @param url
     * @return
     */
    HttpResponse synSendPost(String url,JSONObject paramJson,Map<String,String>... requestHead) throws Exception;


    /**
     * 同步发送post请求 SSL请求
     *
     * @param url
     * @return
     */
    HttpResponse synSendPost(String url,JSONObject paramJson,Boolean isSSL,Map<String,String>... requestHead) throws Exception;



    /**
     * 同步发送post请求
     *
     * @param url
     * @return
     */
    HttpResponse synSendPost(String url,String rqeustParam,Map<String,String>... requestHead) throws Exception;


    /**
     * 异步发送Get请求
     *
     * @param url
     * @param param
     */
    void asynSendGet(String url, String param,Map<String,String>... requestHead) throws Exception;

    /**
     * 异步发送Get请求
     *
     * @param url
     */
    void asynSendGet(String url,Map<String,String>... requestHead) throws Exception;


    /**
     * 同步发送Get请求
     *
     * @param url
     */
    HttpResponse synSendGet(String url,Map<String,String>... requestHead) throws Exception;

    /**
     * 同步发送Get请求
     *
     * @param url
     * @param param
     */
    HttpResponse synSendGet(String url, String param,Map<String,String>... requestHead) throws Exception;
}
