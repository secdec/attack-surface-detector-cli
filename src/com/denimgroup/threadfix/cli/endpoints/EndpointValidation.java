package com.denimgroup.threadfix.cli.endpoints;

import com.denimgroup.threadfix.data.entities.EndpointStructure;
import com.denimgroup.threadfix.data.entities.RouteParameter;
import com.denimgroup.threadfix.data.enums.FrameworkType;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.engine.full.EndpointSerialization;
import com.denimgroup.threadfix.framework.util.EndpointUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EndpointValidation {

    private static Logger logger = Logger.getLogger(EndpointValidation.class);

    public static boolean validateSerialization(File sourceCodeFolder, List<Endpoint> endpoints) {
        List<Endpoint> allEndpoints = EndpointUtil.flattenWithVariants(endpoints);

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
}
