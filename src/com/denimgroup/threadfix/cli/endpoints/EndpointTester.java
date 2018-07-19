package com.denimgroup.threadfix.cli.endpoints;

import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.util.PathUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class EndpointTester {
    String basePath;

    public EndpointTester(String basePath) {
        this.basePath = basePath;
    }

    public int test(Endpoint endpoint) throws IOException {
        URL url = new URL(PathUtil.combine(this.basePath, endpoint.getUrlPath()));
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod(endpoint.getHttpMethod());

        conn.getInputStream().close();
        return conn.getResponseCode();
    }
}
