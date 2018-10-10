////////////////////////////////////////////////////////////////////////
//
//     Copyright (C) 2018 Applied Visions - http://securedecisions.com
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     This material is based on research sponsored by the Department of Homeland
//     Security (DHS) Science and Technology Directorate, Cyber Security Division
//     (DHS S&T/CSD) via contract number HHSP233201600058C.
//
//     Contributor(s):
//              Secure Decisions, a division of Applied Visions, Inc
//
////////////////////////////////////////////////////////////////////////

package com.denimgroup.threadfix.cli.endpoints;

import com.denimgroup.threadfix.data.entities.RouteParameter;
import com.denimgroup.threadfix.data.entities.RouteParameterType;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.util.PathUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static com.denimgroup.threadfix.CollectionUtils.list;
import static com.denimgroup.threadfix.CollectionUtils.map;

public class EndpointTester {
    String basePath;

    public EndpointTester(String basePath) {
        this.basePath = basePath;
    }

    public int test(Endpoint endpoint, Credentials credentials) throws IOException {
        URL url = new URL(PathUtil.combine(this.basePath, endpoint.getUrlPath()));
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod(endpoint.getHttpMethod());

        if (credentials != null && credentials.authenticatedParameters != null) {
            for (Map.Entry<String, String> header : credentials.authenticatedParameters.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        conn.getInputStream().close();
        return conn.getResponseCode();
    }

    public int authorize(Credentials credentials, Endpoint endpoint) throws IOException {
        //  Get query settings
        String httpMethod = endpoint != null ? endpoint.getHttpMethod() : "POST";

        HttpURLConnection.setFollowRedirects(false);

        //  Try auth by best-match

        if (endpoint != null) {
            try {
                String urlParams = configureRequestWithBestMatchParameters(endpoint.getParameters(), credentials, null);
                String finalizedPath = credentials.authenticationEndpoint;
                if (!urlParams.isEmpty()) {
                    finalizedPath += "?" + urlParams;
                }

                URL url = new URL(PathUtil.combine(this.basePath, finalizedPath));
                // Configure connection
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod(httpMethod);
                configureRequestWithBestMatchParameters(endpoint.getParameters(), credentials, conn);

                conn.getInputStream().close();
                if (conn.getResponseCode() < 400 && saveCredentialsResponse(conn, credentials)) {
                    return conn.getResponseCode();
                }
            } catch (IOException e) {
                System.out.println("Unable to authorize using best-match parameters:");
                e.printStackTrace();
            }
        }

        try {
            URL url = new URL(PathUtil.combine(this.basePath, credentials.authenticationEndpoint));
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod(httpMethod);
            configureRequestWithFormParameters(credentials, conn);

            conn.getInputStream().close();
            if (conn.getResponseCode() < 400 && saveCredentialsResponse(conn, credentials)) {
                return conn.getResponseCode();
            }
        } catch (IOException e) {
            System.out.println("Unable to authorize using all-forms parameters:");
            e.printStackTrace();
        }

        return 0xffff;
    }

    private boolean saveCredentialsResponse(HttpURLConnection conn, Credentials creds) {
        if (conn.getHeaderField("Set-Cookie") != null) {
            List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
            List<String> sanitizeCookies = list();

            for (String cookie : cookies) {
                String mainPart = cookie;
                if (mainPart.contains(";")) {
                    mainPart = mainPart.substring(0, mainPart.indexOf(';'));
                }
                sanitizeCookies.add(mainPart);
            }

            creds.authenticatedParameters = map();
            creds.authenticatedParameters.put("Cookie", String.join("; ", sanitizeCookies));

            return true;
        }
        return false;
    }

    private String configureRequestWithBestMatchParameters(Map<String, RouteParameter> parsedParameters, Credentials credentials, HttpURLConnection conn) throws IOException {
        StringJoiner queryString = new StringJoiner("&");
        StringJoiner formParams = new StringJoiner("&");

        for (Map.Entry<String, String> credParam : credentials.parameters.entrySet()) {
            RouteParameter paramSpec = null;
            if (parsedParameters != null) {
                if (parsedParameters.containsKey(credParam.getKey())) {
                    paramSpec = parsedParameters.get(credParam.getKey());
                }
            }

            if (paramSpec == null) {
                paramSpec = RouteParameter.fromDataType(credParam.getKey(), "String");
                paramSpec.setParamType(RouteParameterType.FORM_DATA);
            }

            String encodedKey, encodedValue;
            try {
                encodedKey = URLEncoder.encode(credParam.getKey(), "UTF-8");
                encodedValue = URLEncoder.encode(credParam.getValue(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                continue;
            }

            String encodedPair = encodedKey + "=" + encodedValue;

            switch (paramSpec.getParamType()) {
                case QUERY_STRING:
                    queryString.add(encodedPair);
                    break;

                default:
                    // Assume form data for anything else
                    formParams.add(encodedPair);
            }
        }

        if (formParams.length() > 0 && conn != null) {

            byte[] out = formParams.toString().getBytes(StandardCharsets.UTF_8);

            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(out.length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            conn.connect();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }
        }

        if (queryString.length() == 0) {
            return "";
        } else {
            return "?" + queryString.toString();
        }
    }

    private void configureRequestWithFormParameters(Credentials credentials, HttpURLConnection conn) throws IOException {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> param : credentials.parameters.entrySet()) {
            try {
                joiner.add(URLEncoder.encode(param.getKey(), "UTF-8") + "=" + URLEncoder.encode(param.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        byte[] out = joiner.toString().getBytes(StandardCharsets.UTF_8);

        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(out.length);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
        }
    }
}
