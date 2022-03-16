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

import com.denimgroup.threadfix.data.entities.EndpointStructure;
import com.denimgroup.threadfix.data.entities.RouteParameter;
import com.denimgroup.threadfix.data.enums.EndpointRelevanceStrictness;
import com.denimgroup.threadfix.data.enums.FrameworkType;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.engine.full.EndpointSerialization;
import com.denimgroup.threadfix.framework.util.EndpointUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.denimgroup.threadfix.CollectionUtils.list;

public class EndpointValidation {

    private static Logger logger = LogManager.getLogger(EndpointValidation.class);

    public static boolean validateSerialization(File sourceCodeFolder, List<Endpoint> endpoints) {
        List<Endpoint> allEndpoints = EndpointUtil.flattenWithVariants(endpoints);
        List<String> allUrls = list();

        for (Endpoint endpoint : allEndpoints) {
            allUrls.add(endpoint.getUrlPath());
        }

        try {
            String serializedCollection = EndpointSerialization.serializeAll(allEndpoints);
            Endpoint[] deserializedCollection = EndpointSerialization.deserializeAll(serializedCollection);
            if (deserializedCollection.length != allEndpoints.size()) {
                logger.warn("Collection serialization did not match the original input");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn("Exception thrown during collection serialization");
            return false;
        }

        for (Endpoint endpoint : allEndpoints) {

            if (endpoint.getFilePath().startsWith(sourceCodeFolder.getAbsolutePath().replace('\\', '/'))) {
                logger.warn("Got an absolute file path when a relative path was expected instead, for: " + endpoint.toString());
                return false;
            }

            if (endpoint.getFilePath().isEmpty()) {
                logger.warn("Got an empty file path for: " + endpoint.toString());
            }
            else if (!endpoint.getFilePath().contains("(lib)")) {
                File fullPath = new File(sourceCodeFolder, endpoint.getFilePath());
                if (!fullPath.exists()) {
                    logger.warn("The source code path '" + fullPath.getAbsolutePath() + "' does not exist for: " + endpoint.toString());
                    return false;
                }
            }

            for (String url : allUrls) {
                try {
                    endpoint.isRelevant(url, EndpointRelevanceStrictness.STRICT);
                    endpoint.isRelevant(url, EndpointRelevanceStrictness.LOOSE);
                    endpoint.compareRelevance(url);
                } catch (Exception e) {
                    logger.warn("Exception occurred while testing relevancy comparisons between [" + url + "] and " + endpoint.toString());
                    e.printStackTrace();
                    return false;
                }
            }

            String serialized;
            Endpoint deserialized;
            try {
                serialized = EndpointSerialization.serialize(endpoint);
            } catch (IOException e) {
                logger.warn("Exception occurred while serializing: " + endpoint.toString());
                e.printStackTrace();
                return false;
            }

            try {
                deserialized = EndpointSerialization.deserialize(serialized);
            } catch (IOException e) {
                logger.warn("Exception occurred while deserializing: " + endpoint.toString());
                e.printStackTrace();
                return false;
            }

            if (deserialized == null) {
                logger.warn("Failed to validate serialization due to NULL DESERIALIZED ENDPOINT on " + endpoint.toString());
                return false;
            }

            if (!endpoint.getClass().equals(deserialized.getClass())) {
                logger.warn("Failed to validate serialization due to MISMATCHED ENDPOINT DATATYPES on " + endpoint.toString());
                return false;
            }

            if (!deserialized.getUrlPath().equals(endpoint.getUrlPath())) {
                logger.warn("Failed to validate serialization due to mismatched URL paths on " + endpoint.toString());
                return false;
            }

            if (!deserialized.getFilePath().equals(endpoint.getFilePath())) {
                logger.warn("Failed to validate serialization due to mismatched FILE paths on " + endpoint.toString());
                return false;
            }

            if (deserialized.getParameters().size() != endpoint.getParameters().size()) {
                logger.warn("Failed to validate serialization due to mismatched PARAMETER COUNTS on " + endpoint.toString());
                return false;
            }

            if (!deserialized.getHttpMethod().equals(endpoint.getHttpMethod())) {
                logger.warn("Failed to validate serialization due to mismatched HTTP METHOD on " + endpoint.toString());
                return false;
            }

            Map<String, RouteParameter> endpointParams = endpoint.getParameters();
            Map<String, RouteParameter> deserializedParams = deserialized.getParameters();

            if (!endpointParams.keySet().containsAll(deserializedParams.keySet()) ||
                    !deserializedParams.keySet().containsAll(endpointParams.keySet())) {

                logger.warn("Failed to validate serialization due to mismatched PARAMETER NAMES on " + endpoint.toString());
                return false;
            }

            for (String param : endpointParams.keySet()) {
                RouteParameter endpointParam = endpointParams.get(param);
                RouteParameter deserializedParam = deserializedParams.get(param);

                if (endpointParam.getParamType() != deserializedParam.getParamType()) {
                    logger.warn("Failed to validate serialization due to mismatched PARAM TYPE on " + endpoint.toString());
                    return false;
                }

                if ((endpointParam.getDataTypeSource() == null) != (deserializedParam.getDataTypeSource() == null)) {
                    logger.warn("Failed to validate serialization due to mismatched PARAM DATA TYPE on " + endpoint.toString());
                    return false;
                }

                if (endpointParam.getDataTypeSource() != null && !endpointParam.getDataTypeSource().equals(deserializedParam.getDataTypeSource())) {
                    logger.warn("Failed to validate serialization due to mismatched PARAM DATA TYPE on " + endpoint.toString());
                    return false;
                }

                if (!endpointParam.getName().equals(deserializedParam.getName())) {
                    logger.warn("Failed to validate serialization due to mismatched PARAM NAME on " + endpoint.toString());
                    return false;
                }

                if ((endpointParam.getAcceptedValues() == null) != (deserializedParam.getAcceptedValues() == null)) {
                    logger.warn("Failed to validate serialization due to mismatched ACCEPTED PARAM VALUES on " + endpoint.toString());
                    return false;
                } else if (endpointParam.getAcceptedValues() != null) {
                    if (!endpointParam.getAcceptedValues().containsAll(deserializedParam.getAcceptedValues()) ||
                            !deserializedParam.getAcceptedValues().containsAll(endpointParam.getAcceptedValues())) {
                        logger.warn("Failed to validate serialization due to mismatched ACCEPTED PARAM VALUES on " + endpoint.toString());
                        return false;
                    }
                }
            }
        }

        try {
            EndpointStructure testStructure = new EndpointStructure();
            testStructure.acceptAllEndpoints(endpoints);
        } catch (Exception e) {
            System.out.println("Failed to validate endpoint structure generation due to an exception: \n" + e);
        }

        return true;
    }

    public static boolean validateDuplicates(Collection<Endpoint> endpoints) {
        boolean validated = true;
        List<List<Endpoint>> duplicateEndpoints = detectDuplicates(endpoints);
        if (!duplicateEndpoints.isEmpty()) {
            logger.warn("Found " + duplicateEndpoints.size() + " duplicated endpoints:");
            for (List<Endpoint> duplicateSet : duplicateEndpoints) {
                logger.warn("- " + duplicateSet.size() + ": " + duplicateSet.get(0).toString());
                validated = false;
            }
        }
        return validated;
    }

    private static List<List<Endpoint>> detectDuplicates(Collection<Endpoint> endpoints) {
        List<List<Endpoint>> duplicates = new ArrayList<>();

        for (Endpoint main : endpoints) {
            //  Check if we already know that this endpoint has duplicates
            boolean wasChecked = false;
            for (List<Endpoint> duplicatesSet : duplicates) {
                if (endpointsMatch(main, duplicatesSet.get(0))) {
                    wasChecked = true;
                    break;
                }
            }
            if (wasChecked) {
                continue;
            }

            List<Endpoint> currentDuplicates = list();

            for (Endpoint other : endpoints) {
                if (endpointsMatch(main, other)) {
                    currentDuplicates.add(other);
                }
            }

            if (currentDuplicates.size() > 1) {
                duplicates.add(currentDuplicates);
            }
        }

        return duplicates;
    }

    private static boolean endpointsMatch(Endpoint a, Endpoint b) {
        return
            a.getUrlPath().equals(b.getUrlPath()) &&
            a.getHttpMethod().equals(b.getHttpMethod()) &&
            a.getFilePath().equals(b.getFilePath()) &&
            a.getStartingLineNumber() == b.getStartingLineNumber() &&
            a.getEndingLineNumber() == b.getEndingLineNumber() &&
            endpointParametersMatch(a, b);
    }

    private static boolean endpointParametersMatch(Endpoint a, Endpoint b) {
        Map<String, RouteParameter> bParams = b.getParameters();
        for (Map.Entry<String, RouteParameter> param : a.getParameters().entrySet()) {
            if (!bParams.containsKey(param.getKey()))
                return false;

            RouteParameter aParam = param.getValue();
            RouteParameter bParam = bParams.get(param.getKey());

            if (
                aParam.getParamType() != bParam.getParamType() ||
                aParam.getDataType() != bParam.getDataType() ||
                (aParam.getAcceptedValues() == null) != (bParam.getAcceptedValues() == null)
            ) {
                return false;
            }

            if (
                aParam.getAcceptedValues() != null && !(
                    aParam.getAcceptedValues().containsAll(bParam.getAcceptedValues()) ||
                    bParam.getAcceptedValues().containsAll(aParam.getAcceptedValues())
                )
            ) {
                return false;
            }
        }

        return true;
    }
}
