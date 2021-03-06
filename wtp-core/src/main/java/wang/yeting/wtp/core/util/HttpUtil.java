package wang.yeting.wtp.core.util;

import com.google.gson.Gson;
import org.apache.http.*;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.springframework.util.CollectionUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Type;
import java.net.UnknownHostException;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author : weipeng
 * @date : 2020-07-23 21:23
 */

public class HttpUtil<T> {

    /**
     * ????????????????????????
     */
    private static final Integer CONN_TIMEOUT = 5000;
    /**
     * ????????????????????????
     */
    private static final Integer SO_TIMEOUT = 5000;
    /**
     * ????????????????????????
     */
    private static final Integer RETRY_COUNT = 0;

    /**
     * ????????????
     */
    private static final String CHARSET_CODE = "UTF-8";

    /**
     * ?????????
     */
    static HttpClientConnectionManager POOLING_CONNECTION_MANAGER;

    private static Gson gson;

    /**
     * ??????????????????
     */
    static {
        try {
            gson = new Gson();
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial((X509Certificate[] x509Certificates, String s) -> true).build();
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    NoopHostnameVerifier.INSTANCE);
            Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslConnectionSocketFactory).build();
            // ??????????????????
            PoolingHttpClientConnectionManager pooling = new PoolingHttpClientConnectionManager(r);
            // ?????????????????????
            pooling.setMaxTotal(Integer.MAX_VALUE);
            // ?????????????????????????????????????????????
            pooling.setDefaultMaxPerRoute(20);
            POOLING_CONNECTION_MANAGER = pooling;
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get??????
     *
     * @param url
     * @return
     * @throws Exception
     */
    public static String get(String url) throws Exception {
        return get(url, null, SO_TIMEOUT, CONN_TIMEOUT, RETRY_COUNT);
    }

    /**
     * get??????
     *
     * @param url
     * @param retryCount
     * @return
     * @throws Exception
     */
    public static String get(String url, Integer retryCount) throws Exception {
        return get(url, null, SO_TIMEOUT, CONN_TIMEOUT, retryCount);
    }

    /**
     * post json
     *
     * @param url
     * @param param
     * @return
     * @throws IOException
     * @since 2.0.0
     */
    public static <T> T postJson(String url, Object param, final Type type) throws IOException {
        String postJson = postJson(url, null, gson.toJson(param), SO_TIMEOUT, CONN_TIMEOUT, RETRY_COUNT);
        return gson.fromJson(postJson, type);
    }

    /**
     * http getUrl ??????
     *
     * @param url
     * @param headerMap
     * @param soTimeout
     * @param connTimeout
     * @param retryCount
     * @return
     * @throws Exception
     * @since 2.0.0
     */
    private static String get(String url, Map<String, String> headerMap, Integer soTimeout, Integer connTimeout, Integer retryCount) throws Exception {
        HttpClient client = getHttpClient(retryCount);
        HttpRequestBase requestBase = new HttpGet(url);

        requestBase.setHeader(HttpHeaders.ACCEPT_CHARSET, CHARSET_CODE);
        requestBase.setHeader(HttpHeaders.CONTENT_ENCODING, CHARSET_CODE);

        if (!CollectionUtils.isEmpty(headerMap)) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                requestBase.addHeader(entry.getKey(), entry.getValue());
            }
        }

        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(soTimeout).setConnectTimeout(connTimeout).build();
        requestBase.setConfig(requestConfig);
        return execute(client, requestBase);

    }

    /**
     * post json??????
     *
     * @param url
     * @param headerMap
     * @param json
     * @param connTimeout
     * @param soTimeout
     * @param retryCount
     * @return
     * @throws IOException
     * @since 2.0.0
     */
    public static String postJson(String url, Map<String, String> headerMap, String json, Integer soTimeout, Integer connTimeout, Integer retryCount) throws IOException {
        HttpClient client = getHttpClient(retryCount);
        HttpPost requestBase = new HttpPost(url);

        requestBase.setHeader(HttpHeaders.ACCEPT_CHARSET, CHARSET_CODE);
        requestBase.setHeader(HttpHeaders.CONTENT_ENCODING, CHARSET_CODE);
        requestBase.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());

        if (headerMap != null && !headerMap.isEmpty()) {
            for (Map.Entry<String, String> headerParam : headerMap.entrySet()) {
                requestBase.addHeader(headerParam.getKey(), String.valueOf(headerParam.getValue()));
            }
        }
        try {
            requestBase.setEntity(new StringEntity(json, CHARSET_CODE));
        } catch (UnsupportedCharsetException e) {
            e.printStackTrace();
        }

        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(soTimeout).setConnectTimeout(connTimeout).build();
        requestBase.setConfig(requestConfig);

        return execute(client, requestBase);
    }

    /**
     * ????????????
     *
     * @param client
     * @param requestBase
     * @return
     * @since 2.0.0
     */
    private static String execute(HttpClient client, HttpRequestBase requestBase) throws IOException {
        // ????????????
        try {
            HttpResponse response = client.execute(requestBase);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity, CHARSET_CODE);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            requestBase.releaseConnection();
        }
        return null;
    }

    /**
     * ????????????????????? httpclient
     *
     * @param retryCount
     * @since 2.0.0
     */
    private static CloseableHttpClient getHttpClient(Integer retryCount) {
        return HttpClients.custom().setConnectionManager(
                POOLING_CONNECTION_MANAGER)
                .setRetryHandler(getHttpRequestRetryHandler(retryCount))
                .build();
    }

    /**
     * ??????????????????
     *
     * @param retryCount
     * @return
     * @since 2.0.0
     */
    private static HttpRequestRetryHandler getHttpRequestRetryHandler(int retryCount) {
        return (IOException exception, int executionCount, HttpContext context) -> {
            if (executionCount > retryCount) {
                return false;
            }
            // ????????????????????????????????????????????????
            if (exception instanceof NoHttpResponseException) {
                return true;
            }
            // ????????????SSL????????????
            if (exception instanceof SSLHandshakeException) {
                return false;
            }
            // ???????????????
            if (exception instanceof ConnectTimeoutException) {
                return false;
            }
            // ??????
            if (exception instanceof InterruptedIOException) {
                return false;
            }
            // ????????????????????????
            if (exception instanceof UnknownHostException) {
                return false;
            }
            // SSL????????????
            if (exception instanceof SSLException) {
                return false;
            }
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            // ??????????????????????????????????????????
            return !(request instanceof HttpEntityEnclosingRequest);
        };
    }

}
