package org.coroutines.tool.http.support

import com.alibaba.fastjson.JSONObject
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.apache.http.*
import org.apache.http.client.HttpRequestRetryHandler
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.coroutines.tool.http.HttpInterface
import org.coroutines.tool.http.exception.RetryHttpException
import org.coroutines.tool.http.response.HttpResponse
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import javax.net.ssl.*

/**
 * @Project:http-coroutines
 * @PackageName:org.coroutines.tool.http.support
 * @Author:null z
 * @DateTime:2017-09-03 19:22.
 */
class HttpCoroutinesClient : HttpInterface {


    private val log = LoggerFactory.getLogger(HttpCoroutinesClient::class.java)


    private var maxTotal = 10

    private var maxpPerroute = 5

    private var executionCountHttp = 0


    /**
     *       *返回请求连接时使用的超时（毫秒）
     *       *从连接管理器。 解释超时值为零
     *       *作为无限超时。
     *       *
     *
     *
     *       *超时值为零被解释为无限超时。
     *       *负值解释为未定义（系统默认值）。
     *       *  p>
     *       *
     *
     *
     *       *默认值：`-1`
     *       *  p>
     *      
     */
    private var requestTimeout = -1//
    /**
     *       *定义套接字超时（`SO_TIMEOUT`），单位为毫秒，
     *       *这是等待数据的超时时间，换句话说，
     *       *两个连续数据包之间的最长时间不活动）。
     *       *
     *
     *
     *       *超时值为零被解释为无限超时。
     *       *负值解释为未定义（系统默认值）。
     *       *  p>
     *       *
     *
     *
     *       *默认值：`-1`
     *       *  p>
     *      
     */
    private var socketTimeout = -1//


    /**
     *       *确定连接建立之前的超时时间（以毫秒为单位）。
     *       *超时值为零被解释为无限超时。
     *       *
     *
     *
     *       *超时值为零被解释为无限超时。
     *       *负值解释为未定义（系统默认值）。
     *       *  p>
     *       *
     *
     *
     *       *默认值：`-1`
     *       *  p>
     *      
     */
    private var connectTimeout = -1//

    constructor(maxTotal: Int, maxpPerroute: Int, executionCountHttp: Int, requestTimeout: Int, socketTimeout: Int, connectTimeout: Int) {
        this.maxTotal = maxTotal
        this.maxpPerroute = maxpPerroute
        this.executionCountHttp = executionCountHttp
        this.requestTimeout = requestTimeout
        this.socketTimeout = socketTimeout
        this.connectTimeout = connectTimeout
    }


    @Throws(Exception::class)
    override fun asynSendPost(url: String, paramMap: Map<String, String>, vararg requestHead: Map<String, String>) {
        val start = System.currentTimeMillis()
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            val nvps = ArrayList<NameValuePair>()
            for (key in paramMap.keys) {
                nvps.add(BasicNameValuePair(key, paramMap[key]))
            }
            httpPost.entity = UrlEncodedFormEntity(nvps, Charset.forName(Consts.UTF_8.name()))
            config(httpPost, *requestHead)
            // 启动协程launch
            executeAsyncHttPost(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
    }

    private fun executeAsyncHttPost(httpClient: CloseableHttpClient, httpPost: HttpPost, countDownLatch: CountDownLatch) {
        launch(CommonPool) {
            var response: CloseableHttpResponse? = null
            try {
                response = httpClient!!.execute(httpPost, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                //关闭流
                EntityUtils.consume(response.entity)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw e
            } finally {
                countDownLatch.countDown()
                try {
                    response?.close()
                    httpClient?.close()
                } catch (e: IOException) {
                    log.error("关闭Http异常", e)
                }

            }
        }

    }


    override fun synSendPost(url: String?, paramMap: Map<String, String>, ssl: Boolean?,  vararg requestHead: Map<String, String>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse:HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url, ssl)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            val nvps = ArrayList<NameValuePair>()
            for (key in paramMap.keys) {
                nvps.add(BasicNameValuePair(key, paramMap[key]))
            }
            httpPost.entity = UrlEncodedFormEntity(nvps, Consts.UTF_8.name())
            config(httpPost, *requestHead)
            // 启动协程抓取
            httpResponse = syncHttpResponse(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }

    @Throws(Exception::class)
    override fun asynSendPost(url: String, paramJson: JSONObject, vararg requestHead: Map<String, String>) {
        val start = System.currentTimeMillis()
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            val entity = StringEntity(paramJson.toJSONString(), Consts.UTF_8.name())//解决中文乱码问题
            httpPost.entity = entity
            config(httpPost, *requestHead)
            // 启动协程
            executeAsyncHttPost(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
    }


    @Throws(Exception::class)
    override fun synSendPost(url: String, rqeustParam: String, requestHead: Array<Map<String, String>>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            httpPost.entity = StringEntity(rqeustParam, Consts.UTF_8.name())
            config(httpPost, *requestHead)
            // 启动协程抓取
            httpResponse = syncHttpResponse(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }

    private fun syncHttpResponse(httpClient: CloseableHttpClient, httpPost: HttpPost, countDownLatch: CountDownLatch): HttpResponse? {
        var resultBlocking = runBlocking <HttpResponse?> {
            var response: CloseableHttpResponse? = null
            var httpResponse: HttpResponse? = null
            try {
                response = httpClient!!.execute(httpPost, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                val statusCode = response.statusLine.statusCode
                //关闭流
                EntityUtils.consume(response.entity)
                httpResponse = HttpResponse(result, statusCode)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()

                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }

            }
            return@runBlocking httpResponse
        }
        return resultBlocking
    }

    @Throws(Exception::class)
    override fun synSendPost(url: String, paramMap: Map<String, String>, vararg requestHead: Map<String, String>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            val nvps = ArrayList<NameValuePair>()
            for (key in paramMap.keys) {
                nvps.add(BasicNameValuePair(key, paramMap[key]))
            }
            httpPost.entity = UrlEncodedFormEntity(nvps, Consts.UTF_8.name())
            config(httpPost, *requestHead)
            // 启动协程抓取
            httpResponse = syncHttpResponse(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }


    @Throws(Exception::class)
    override fun synSendPost(url: String, paramMap: Map<String, String>, timeout: Long, timeUnit: TimeUnit, requestHead: Array<Map<String, String>>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            val nvps = ArrayList<NameValuePair>()
            for (key in paramMap.keys) {
                nvps.add(BasicNameValuePair(key, paramMap[key]))
            }
            httpPost.entity = UrlEncodedFormEntity(nvps, Consts.UTF_8.name())
            config(httpPost, *requestHead)
            // 启动协程抓取
            httpResponse = syncHttpResponse(httpClient, httpPost, countDownLatch)
            // httpResponse = responseFuture.get(timeout, timeUnit)
            countDownLatch.await()
        } catch (e: TimeoutException) {
            log.warn("http请求处理{}超时", timeout, e)
            throw e
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }

        return httpResponse
    }

    @Throws(Exception::class)
    override fun synSendUrlPost(url: String, requestHead: Array<Map<String, String>>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val newrul = if (url.indexOf("?") != -1) url.substring(0, url.indexOf("?")) else url
            val httpClient = builderHttpClient(newrul)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(newrul)
            val split = url.substring(url.indexOf("?") + 1).split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val paramMap = HashMap<String, String>()
            for (item in split) {
                val items = item.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                paramMap.put(items[0], if (items.size > 1) items[1] else "")
            }
            val nvps = ArrayList<NameValuePair>()
            for (key in paramMap.keys) {
                nvps.add(BasicNameValuePair(key, paramMap[key]))
            }
            httpPost.entity = UrlEncodedFormEntity(nvps, Consts.UTF_8.name())
            config(httpPost, *requestHead)
            // 启动协程抓取
            httpResponse = syncHttpResponse(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }

    @Throws(Exception::class)
    override fun synSendPost(url: String, paramJson: JSONObject, vararg requestHead: Map<String, String>): HttpResponse? {
        return synSendPost(url, paramJson, false, *requestHead)
    }


    @Throws(Exception::class)
    override fun synSendPost(url: String, paramJson: JSONObject, isSSL: Boolean, vararg requestHead: Map<String, String>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url, isSSL)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpPost = HttpPost(url)
            val entity = StringEntity(paramJson.toJSONString(), Consts.UTF_8.name())//解决中文乱码问题
            httpPost.entity = entity
            config(httpPost, *requestHead)
            // 启动协程抓取
            httpResponse = syncHttpResponse(httpClient, httpPost, countDownLatch)
            countDownLatch.await()
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }

    @Throws(Exception::class)
    override fun asynSendGet(url: String, param: String, vararg requestHead: Map<String, String>) {
        var url = url
        val start = System.currentTimeMillis()
        try {
            val httpClient = builderHttpClient(url)
            if (url.indexOf("?") == -1) {
                url = url + "?" + param
            } else {
                url += param
            }
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpGet = HttpGet(url)
            config(httpGet, *requestHead)
            // 启动协程抓取
            asyncHttpGet(httpClient, httpGet, countDownLatch)
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
    }

    private fun asyncHttpGet(httpClient: CloseableHttpClient, httpGet: HttpGet, countDownLatch: CountDownLatch) {
        launch(CommonPool) {
            var response: CloseableHttpResponse? = null
            try {
                response = httpClient!!.execute(httpGet, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                //关闭流
                EntityUtils.consume(response.entity)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()

                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun synSendGet(url: String, param: String, vararg requestHead: Map<String, String>): HttpResponse? {
        var url = url
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url)
            if (url.indexOf("?") == -1) {
                url = url + "?" + param
            } else {
                url += param
            }
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpGet = HttpGet(url)
            config(httpGet, *requestHead)
            // 启动协程抓取
            httpResponse = syncGetHttpResponse(httpClient, httpGet, countDownLatch)
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }

    private fun syncGetHttpResponse(httpClient: CloseableHttpClient, httpGet: HttpGet, countDownLatch: CountDownLatch): HttpResponse? {
        var result = runBlocking <HttpResponse?> {
            var resulthttp = async(CommonPool) {
                var response: CloseableHttpResponse? = null
                var httpResponse: HttpResponse? = null
                try {
                    response = httpClient!!.execute(httpGet, HttpClientContext.create())
                    val result = EntityUtils.toString(response!!.entity)
                    val statusCode = response.statusLine.statusCode
                    //关闭流
                    EntityUtils.consume(response.entity)
                    httpResponse = HttpResponse(result, statusCode)
                    if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                        throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                    }
                } catch (e: Exception) {
                    log.error("{}响应异常", Thread.currentThread().name, e)
                    throw e
                } finally {
                    countDownLatch.countDown()
                    try {
                        if (response != null)
                            response.close()

                        httpClient?.close()
                    } catch (e: IOException) {
                        log.warn("关闭Http异常", e)
                    }

                }
                return@async httpResponse
            }
            resulthttp.await()
        }
        return result
    }

    @Throws(Exception::class)
    override fun asynSendGet(url: String, vararg requestHead: Map<String, String>) {
        val start = System.currentTimeMillis()
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpGet = HttpGet(url)
            config(httpGet, *requestHead)
            asyncGetHttp(httpClient, httpGet, countDownLatch)
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
    }

    private fun asyncGetHttp(httpClient: CloseableHttpClient, httpGet: HttpGet, countDownLatch: CountDownLatch) {
        launch(CommonPool) {
            var response: CloseableHttpResponse? = null
            try {
                response = httpClient!!.execute(httpGet, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                //关闭流
                EntityUtils.consume(response.entity)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw  e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()
                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }

            }
        }
    }

    @Throws(Exception::class)
    override fun synSendGet(url: String, vararg requestHead: Map<String, String>): HttpResponse? {
        val start = System.currentTimeMillis()
        var httpResponse: HttpResponse? = null
        try {
            val httpClient = builderHttpClient(url)
            val countDownLatch = CountDownLatch(1)//重试计数器
            val httpGet = HttpGet(url)
            httpGet.config
            config(httpGet, *requestHead)
            httpResponse=syncGetHttp(httpClient, httpGet, countDownLatch);
        } catch (e: Exception) {
            log.error("Http异常", e)
            throw e
        } finally {
            log.debug("ThreadName:{} url:{} time:{}", Thread.currentThread().name, url, System.currentTimeMillis() - start)
        }
        return httpResponse
    }

    private fun syncGetHttp(httpClient: CloseableHttpClient, httpGet: HttpGet, countDownLatch: CountDownLatch): HttpResponse? {
        var result = runBlocking <HttpResponse?> {
            var resulthttp = async(CommonPool) {
                var response: CloseableHttpResponse? = null
                var httpResponse: HttpResponse? = null
                try {
                    response = httpClient!!.execute(httpGet, HttpClientContext.create())
                    val result = EntityUtils.toString(response!!.entity)
                    val statusCode = response.statusLine.statusCode
                    //关闭流
                    EntityUtils.consume(response.entity)
                    httpResponse = HttpResponse(result, statusCode)
                    if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                        throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                    }
                } catch (e: Exception) {
                    log.error("{}响应异常", Thread.currentThread().name, e)
                    throw e
                } finally {
                    countDownLatch.countDown()
                    try {
                        if (response != null)
                            response.close()

                        httpClient?.close()
                    } catch (e: IOException) {
                        log.warn("关闭Http异常", e)
                    }

                }
                return@async httpResponse
            }
            resulthttp.await()
        }
        return result
    }


    /**
     * 异步Post处理
     */
    class AsynPostHandler(private val httpClient: CloseableHttpClient?, private val httpPost: HttpPost, private val countDownLatch: CountDownLatch) : Runnable {

        private val log = LoggerFactory.getLogger(AsynPostHandler::class.java)

        override fun run() {
            var response: CloseableHttpResponse? = null
            try {
                response = httpClient!!.execute(httpPost, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                //关闭流
                EntityUtils.consume(response.entity)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw  e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()

                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }

            }
        }
    }


    /**
     * 同步Post处理
     */
    class SynPostHandler(private val httpClient: CloseableHttpClient?, private val httpPost: HttpPost, private val countDownLatch: CountDownLatch) : Callable<HttpResponse?> {

        private val log = LoggerFactory.getLogger(SynPostHandler::class.java)

        @Throws(Exception::class)
        override fun call(): HttpResponse? {
            var response: CloseableHttpResponse? = null
            var httpResponse: HttpResponse? = null
            try {
                response = httpClient!!.execute(httpPost, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                val statusCode = response.statusLine.statusCode
                //关闭流
                EntityUtils.consume(response.entity)
                //result(result).statusCode(statusCode)
                httpResponse = HttpResponse(result, statusCode)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()

                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }

            }
            return httpResponse
        }
    }


    /**
     * 异步Get处理
     */
    class AsynGetHandler(private val httpClient: CloseableHttpClient?, private val httpGet: HttpGet, private val countDownLatch: CountDownLatch) : Runnable {

        private val log = LoggerFactory.getLogger(AsynGetHandler::class.java)

        override fun run() {
            var response: CloseableHttpResponse? = null
            try {
                response = httpClient!!.execute(httpGet, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                //关闭流
                EntityUtils.consume(response.entity)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw  e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()

                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }

            }
        }
    }


    /**
     * 同步Post处理
     */
    class SynGetHandler(private val httpClient: CloseableHttpClient?, private val httpGet: HttpGet, private val countDownLatch: CountDownLatch) : Callable<HttpResponse?> {

        private val log = LoggerFactory.getLogger(SynGetHandler::class.java)

        @Throws(Exception::class)
        override fun call(): HttpResponse? {
            var response: CloseableHttpResponse? = null
            var httpResponse: HttpResponse? = null
            try {
                response = httpClient!!.execute(httpGet, HttpClientContext.create())
                val result = EntityUtils.toString(response!!.entity)
                val statusCode = response.statusLine.statusCode
                //关闭流
                EntityUtils.consume(response.entity)
                httpResponse = HttpResponse(result, statusCode)
                if (response.statusLine.statusCode != HttpStatus.SC_OK && result == null) {
                    throw RetryHttpException("Http状态码异常:" + response.statusLine.statusCode)
                }
            } catch (e: Exception) {
                log.error("{}响应异常", Thread.currentThread().name, e)
                throw e
            } finally {
                countDownLatch.countDown()
                try {
                    if (response != null)
                        response.close()

                    httpClient?.close()
                } catch (e: IOException) {
                    log.warn("关闭Http异常", e)
                }

            }
            return httpResponse
        }
    }

    private fun config(httpRequestBase: HttpRequestBase, vararg requestHead: Map<String, String>) {
        if (requestHead.size == 0) {//默认请求头
            httpRequestBase.setHeader("User-Agent", "Mozilla/5.0")
            httpRequestBase.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            httpRequestBase.setHeader("Accept-Language", "en-US,en,zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
            httpRequestBase.setHeader("Accept-Charset", "ISO-8859-1,utf-8,UTF-8,gbk,gb2312;q=0.7,*;q=0.7")
        } else {//自定义请求头
            for (head in requestHead) {
                for (key in head.keys) {
                    httpRequestBase.setHeader(key, head[key])
                }
            }
        }
        // 配置请求的超时设置
        //设置代理请求
        val requestConfig = RequestConfig.custom().setConnectionRequestTimeout(requestTimeout)
                .setConnectTimeout(connectTimeout).setProxy(null).setSocketTimeout(socketTimeout).build()
        httpRequestBase.config = requestConfig
    }


    private class TrustAnyTrustManager : X509TrustManager {

        @Throws(CertificateException::class)
        override fun checkClientTrusted(ax509certificate: Array<X509Certificate>, s: String) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(ax509certificate: Array<X509Certificate>, s: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    @Throws(Exception::class)
    private fun builderHttpClient(url: String?, vararg isSSL: Boolean?): CloseableHttpClient {

        val plainsf = PlainConnectionSocketFactory.getSocketFactory()
        // 相信CA和所有自签名的证书
        val sslcontext = SSLContexts.custom().loadTrustMaterial(TrustSelfSignedStrategy()).build()
        // OpenSSLKeyManager openSSLKeyManager=new OpenSSLKeyManager("","");
        sslcontext.init(arrayOf<KeyManager>(), arrayOf<TrustManager>(TrustAnyTrustManager()),
                java.security.SecureRandom())
        var sslConnectionSocketFactory: SSLConnectionSocketFactory? = null

        if (isSSL.size > 0 && isSSL[0] === true) {
            val factory = sslcontext.socketFactory
            val socket = factory.createSocket() as SSLSocket
            //启用所有加密算法
            val pwdsuits = socket.supportedCipherSuites
            //  log.info("加密算法={}", pwdsuits);
            //启用 TLSv1, TLSv1.1, TLSv1.2 所有协议
            // String[] TLSArray = socket.getSupportedProtocols();
            // log.info("加密协议={}", TLSArray);
            sslConnectionSocketFactory = SSLConnectionSocketFactory(sslcontext, arrayOf("TLSv1", "TLSv1.1", "TLSv1.2"), pwdsuits,
                    NoopHostnameVerifier.INSTANCE)
            // 启用 TLSv1, TLSv1.1, TLSv1.2
            //            sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslcontext, new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"}, null,
            //                    NoopHostnameVerifier.INSTANCE);
        } else {
            /**
             * DefaultHostnameVerifier
             * NoopHostnameVerifier

             * 如果出现host和域名不匹配，要自定义

             * 或者不验证域名NoopHostnameVerifier

             * DefaultHostnameVerifier
             */
            sslConnectionSocketFactory = SSLConnectionSocketFactory(sslcontext,
                    NoopHostnameVerifier.INSTANCE)
        }
        val registry = RegistryBuilder.create<ConnectionSocketFactory>()
                .register("http", plainsf).register("https", sslConnectionSocketFactory).build()
        val cm = PoolingHttpClientConnectionManager(registry)
        // 将最大连接数增加到200
        cm.maxTotal = maxTotal
        // 将每个路由基础的连接增加到20
        cm.defaultMaxPerRoute = maxpPerroute
        // 将目标主机的最大连接数增加到50
        val localhost = HttpHost(captureHost(url), captureHostPort(url))
        cm.setMaxPerRoute(HttpRoute(localhost), 5)
        // 请求重试处理
        val httpRequestRetryHandler = HttpRequestRetryHandler { exception, executionCount, context ->
            if (executionCount >= executionCountHttp) {// 如果已经重试了X次，就放弃
                log.warn("return out=" + url)
                return@HttpRequestRetryHandler false
            }
            if (exception is NoHttpResponseException) {// 如果服务器丢掉了连接，那么就重试
                log.info("service out url=" + url)
                return@HttpRequestRetryHandler true
            }
            if (exception is SSLHandshakeException) {// 不要重试SSL握手异常
                log.warn("SSL=" + url)
                return@HttpRequestRetryHandler false
            }
            if (exception is InterruptedIOException) {// 超时
                log.warn("time out=" + url)
                return@HttpRequestRetryHandler true
            }
            if (exception is UnknownHostException) {// 目标服务器不可达
                log.warn("service out=" + url)
                return@HttpRequestRetryHandler false
            }
            if (exception is ConnectTimeoutException) {// 连接被拒绝
                log.warn("service out=" + url)
                return@HttpRequestRetryHandler false
            }
            if (exception is SSLException) {// ssl握手异常
                log.warn("SSL service out=" + url)
                return@HttpRequestRetryHandler false
            }
            val clientContext = HttpClientContext.adapt(context)
            val request = clientContext.request as? HttpEntityEnclosingRequest ?: return@HttpRequestRetryHandler false
            // 如果请求是幂等的，就不再尝试
            false
        }
        return HttpClients.custom().setConnectionManager(cm)
                .setRetryHandler(httpRequestRetryHandler).build()
    }

    private fun captureHost(url: String?): String {
        val m = Pattern.compile("^(https://[^/]+|http://[^/]+)").matcher(url)
        while (m.find()) {
            return m.group()
        }
        return m.group()
    }

    private fun captureHostPort(url: String?): Int {
        val regex = "//(.*?):([0-9]+)"
        val p = Pattern.compile(regex)
        val m = p.matcher(url)
        var port: String? = null
        while (m.find()) {
            port = m.group(2)
        }
        if (port == null) {
            port = "80"
        }
        return Integer.parseInt(port)
    }


    fun getRequestTimeout(): Int {
        return requestTimeout
    }

    fun setRequestTimeout(requestTimeout: Int) {
       this.requestTimeout = requestTimeout
    }

    fun getSocketTimeout(): Int {
        return socketTimeout
    }

    fun setSocketTimeout(socketTimeout: Int) {
        this.socketTimeout = socketTimeout
    }

    fun getConnectTimeout(): Int {
        return connectTimeout
    }

    fun setConnectTimeout(connectTimeout: Int) {
        this.connectTimeout = connectTimeout
    }



}