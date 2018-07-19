package com.denimgroup.threadfix.cli.endpoints;

import java.util.Map;

import static com.denimgroup.threadfix.CollectionUtils.map;

public class Credentials {
    public String authenticationEndpoint;
    public Map<String, String> parameters = map();

    public Map<String, String> authenticatedParameters = null;
}
