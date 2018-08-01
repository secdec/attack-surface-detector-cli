////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2015 Denim Group, Ltd.
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
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s):
//              Denim Group, Ltd.
//              Secure Decisions, a division of Applied Visions, Inc
//
////////////////////////////////////////////////////////////////////////

package com.denimgroup.threadfix.cli.endpoints;

import com.denimgroup.threadfix.data.entities.RouteParameter;
import com.denimgroup.threadfix.data.entities.RouteParameterType;
import com.denimgroup.threadfix.data.entities.WildcardEndpointPathNode;
import com.denimgroup.threadfix.data.enums.FrameworkType;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.data.interfaces.EndpointPathNode;
import com.denimgroup.threadfix.framework.engine.framework.FrameworkCalculator;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabase;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabaseFactory;
import com.denimgroup.threadfix.framework.engine.full.EndpointSerialization;
import com.denimgroup.threadfix.framework.engine.full.TemporaryExtractionLocation;
import com.denimgroup.threadfix.framework.util.EndpointUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.denimgroup.threadfix.CollectionUtils.list;
import static com.denimgroup.threadfix.CollectionUtils.map;
import static com.denimgroup.threadfix.data.interfaces.Endpoint.PrintFormat.FULL_JSON;
import static com.denimgroup.threadfix.data.interfaces.Endpoint.PrintFormat.SIMPLE_JSON;

public class EndpointMain {
    private static final String FRAMEWORK_COMMAND = "-defaultFramework=";
    enum Logging {
        ON, OFF
    }

    static String PRINTLN_SEPARATOR = StringUtils.repeat('-', 10);

    static Logging logging = Logging.OFF;
    static Endpoint.PrintFormat printFormat = Endpoint.PrintFormat.DYNAMIC;
    static FrameworkType defaultFramework = FrameworkType.DETECT;
    static boolean simplePrint = false;
    static String pathListFile = null;
    static String outputFilePath = null;

    static int totalDetectedEndpoints = 0;
    static int totalDistinctEndpoints = 0;
    static int totalDetectedParameters = 0;
    static int totalDistinctParameters = 0;

    static int numProjectsWithDuplicates = 0;

    static String testUrlPath = null;
    static Credentials testCredentials = null;

    private static void println(String line) {
        if (printFormat != SIMPLE_JSON && printFormat != FULL_JSON) {
            System.out.println(line);
        }
    }

    public static void main(String[] args) {
        if (checkArguments(args)) {
            resetLoggingConfiguration();
            List<String> projectsMissingEndpoints = list();
            int numProjectsWithEndpoints = 0;
            int numProjects = 0;

            List<Endpoint> allEndpoints = list();

            if (outputFilePath != null && !(printFormat == SIMPLE_JSON || printFormat == FULL_JSON)) {
                System.out.println("An output file path was specified but neither -json nor -simple-json flags were set, output file path will be ignored");
            }

            if (pathListFile != null) {
                println("Loading path list file at '" + pathListFile + "'");
                List<String> fileContents;
                boolean isLongComment = false;

                try {
                    fileContents = FileUtils.readLines(new File(pathListFile));
                    List<EndpointJob> requestedTargets = list();
                    int lineNo = 1;
                    for (String line : fileContents) {
                        line = line.trim();
                        if (line.startsWith("#!")) {
                            isLongComment = true;
                        } else if (line.startsWith("!#")) {
                            isLongComment = false;
                        } else if (!line.startsWith("#") && !line.isEmpty() && !isLongComment) {

                            List<FrameworkType> compositeFrameworkTypes = list();
                            FrameworkType frameworkType = FrameworkType.DETECT;
                            File asFile;
                            if (line.contains(":") && !(new File(line)).exists()) {
                                String[] parts = StringUtils.split(line, ":", 2);
                                frameworkType = FrameworkType.getFrameworkType(parts[0].trim());
                                asFile = new File(parts[1].trim());
                            } else {
                                asFile = new File(line);
                            }

                            if (!asFile.exists()) {
                                println("WARN - Unable to find input path '" + line + "' at line " + lineNo + " of " + pathListFile);
                            } else if (!asFile.isDirectory() && !isZipFile(asFile.getAbsolutePath())) {
                                println("WARN - Input path '" + line + "' is not a directory or ZIP, at line " + lineNo + " of " + pathListFile);
                            } else {
                                if (frameworkType == FrameworkType.NONE) {
                                    println("WARN: Couldn't parse framework type: '" + frameworkType + "', for '" + asFile.getName() + "' using DETECT");
                                }

                                if (frameworkType == FrameworkType.DETECT) {
                                    compositeFrameworkTypes = FrameworkCalculator.getTypes(asFile);
                                }

                                EndpointJob newJob = new EndpointJob();
                                if (compositeFrameworkTypes.isEmpty()) {
                                    compositeFrameworkTypes.add(frameworkType);
                                }
                                newJob.frameworkTypes = compositeFrameworkTypes;
                                newJob.sourceCodePath = asFile;
                                requestedTargets.add(newJob);
                            }
                        }
                        ++lineNo;
                    }

                    numProjects = requestedTargets.size();

                    boolean isFirst = true;
                    for (EndpointJob job : requestedTargets) {
                        if (isFirst) {
                            println(PRINTLN_SEPARATOR);
                            isFirst = false;
                        }
                        println("Beginning endpoint detection for '" + job.sourceCodePath.getAbsolutePath() + "' with " + job.frameworkTypes.size() + " framework types");
                        for (FrameworkType subType : job.frameworkTypes) {
                            println("Using framework=" + subType);
                        }
                        List<Endpoint> generatedEndpoints = listEndpoints(job.sourceCodePath, job.frameworkTypes);
                        println("Finished endpoint detection for '" + job.sourceCodePath.getAbsolutePath() + "'");
                        println(PRINTLN_SEPARATOR);

                        if (!generatedEndpoints.isEmpty()) {
                            ++numProjectsWithEndpoints;

                            if (printFormat == SIMPLE_JSON || printFormat == FULL_JSON) {
                                allEndpoints.addAll(generatedEndpoints);
                            }
                        } else {
                            projectsMissingEndpoints.add(job.sourceCodePath.getAbsolutePath());
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    println("Unable to read path-list at " + pathListFile);
                    printError();
                }
            } else {
    	        ++numProjects;

    	        File rootFolder = new File(args[0]);

    	        List<FrameworkType> compositeFrameworkTypes = list();
    	        if (defaultFramework == FrameworkType.DETECT) {
    	            compositeFrameworkTypes.addAll(FrameworkCalculator.getTypes(rootFolder));
                } else {
    	            compositeFrameworkTypes.add(defaultFramework);
                }

                println("Beginning endpoint detection for '" + rootFolder.getAbsolutePath() + "' with " + compositeFrameworkTypes.size() + " framework types");
                for (FrameworkType subType : compositeFrameworkTypes) {
                    println("Using framework=" + subType);
                }

                Collection<Endpoint> newEndpoints = listEndpoints(rootFolder, compositeFrameworkTypes);

                println("Finished endpoint detection for '" + rootFolder.getAbsolutePath() + "'");
                println(PRINTLN_SEPARATOR);

                if (!newEndpoints.isEmpty()) {
                    ++numProjectsWithEndpoints;
                    if (printFormat == SIMPLE_JSON || printFormat == FULL_JSON) {
                        allEndpoints.addAll(newEndpoints);
                    }
                } else {
                    projectsMissingEndpoints.add(rootFolder.getAbsolutePath());
                }
            }

            if (!simplePrint) {
                if (printFormat == SIMPLE_JSON) {
                    Endpoint.Info[] infos = getEndpointInfo(allEndpoints);

                    try {
                        String s = new ObjectMapper().writeValueAsString(infos);
                        System.out.println(s);

                        if (outputFilePath != null) {
                            FileUtils.writeStringToFile(new File(outputFilePath), s);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (printFormat == FULL_JSON) {
                    try {
                        String s = EndpointSerialization.serializeAll(allEndpoints);
                        System.out.println(s);

                        if (outputFilePath != null) {
                            FileUtils.writeStringToFile(new File(outputFilePath), s);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }



            println("-- DONE --");

            println(numProjectsWithDuplicates + " projects had duplicate endpoints");

            println("Generated " + totalDistinctEndpoints + " distinct endpoints");
            println("Generated " + totalDetectedEndpoints + " total endpoints");
            println("Generated " + totalDistinctParameters + " distinct parameters");
            println("Generated " + totalDetectedParameters + " total parameters");
            println(numProjectsWithEndpoints + "/" + numProjects + " projects had endpoints generated");
            if (!projectsMissingEndpoints.isEmpty()) {
                println("The following projects were missing endpoints:");
                for (String path : projectsMissingEndpoints) {
                    println("--- " + path);
                }
            }

            if (printFormat != SIMPLE_JSON && printFormat != FULL_JSON) {
                println("To enable logging include the -debug argument");
            }
        } else {
            printError();
        }
    }

    private static boolean isZipFile(String filePath) {
    	String ext = FilenameUtils.getExtension(filePath).toLowerCase();
    	return
	        ext.equals("zip") ||
	        ext.equals("war");
    }

    private static boolean checkArguments(String[] args) {
        if (args.length == 0) {
            return false;
        }

        File rootFile = new File(args[0]);

        if (rootFile.exists() && rootFile.isDirectory() || args[0].startsWith("-path-list-file")) {

            List<String> arguments = list(args);

            if (rootFile.exists()) {
                arguments.remove(0);
            }

            for (String arg : arguments) {
                if (arg.equals("-debug")) {
                    logging = Logging.ON;
                } else if (arg.equals("-lint")) {
                    printFormat = Endpoint.PrintFormat.LINT;
                } else if (arg.equals("-json")) {
                    printFormat = FULL_JSON;
                } else if (arg.equals("-simple-json")) {
                    printFormat = SIMPLE_JSON;
                } else if (arg.contains(FRAMEWORK_COMMAND)) {
                    String frameworkName = arg.substring(arg.indexOf(
                            FRAMEWORK_COMMAND) + FRAMEWORK_COMMAND.length(), arg.length());
                    defaultFramework = FrameworkType.getFrameworkType(frameworkName);
                } else if (arg.equals("-simple")) {
                    simplePrint = true;
                } else if (arg.startsWith("-output-file=")) {
                    String[] parts = arg.split("=");
                    String path = parts[1];
                    File outputFile = new File(path).getAbsoluteFile();
                    File parentDirectory = outputFile.getParentFile();
                    if (parentDirectory.isDirectory()) {
                        parentDirectory.mkdirs();
                    }
                    outputFilePath = outputFile.getAbsolutePath();
                    println("Writing output to file at: \"" + outputFilePath + "\"");
                } else if (arg.startsWith("-path-list-file=")) {
                    String[] parts = arg.split("=");
                    String path = parts[1];
                    if (path == null || path.isEmpty()) {
                        println("Invalid -path-list-file argument, value is empty");
                        continue;
                    }
                    if (path.startsWith("\"") || path.startsWith("'")) {
                        path = path.substring(1);
                    }
                    if (path.endsWith("\"") || path.endsWith("'")) {
                        path = path.substring(0, path.length() - 1);
                    }
                    pathListFile = path;
                } else if (arg.startsWith("-validation-server=")) {
                    String[] parts = arg.split("=");
                    testUrlPath = parts[1];
                } else if (arg.startsWith("-validation-server-auth=")) {
                    arg = arg.substring("-validation-server-auth=".length());
                    String[] parts = arg.split(";");
                    testCredentials = new Credentials();
                    testCredentials.parameters = map();

                    for (String part : parts) {
                        if (testCredentials.authenticationEndpoint == null) {
                            testCredentials.authenticationEndpoint = part;
                        } else {
                            String[] paramParts = part.split("=");
                            if (paramParts.length != 2) {
                                println("Invalid authentication parameter format: " + part);
                            } else {
                                testCredentials.parameters.put(paramParts[0], paramParts[1]);
                            }
                        }
                    }

                } else {
                    println("Received unsupported option " + arg + ", valid arguments are -lint, -debug, -simple-json, -json, -path-list-file, and -simple");
                    return false;
                }
            }

            return true;

        } else {
            println("Please enter a valid file path as the first parameter.");
        }

        return false;
    }

    static void printError() {
        println("The first argument should be a valid file path to scan. Other flags supported: -lint, -debug, -simple-json, -json, -path-list-file=..., -output-file=..., -simple");
    }

    private static int printEndpointWithVariants(int i, int currentDepth, Endpoint endpoint) {

        int numPrinted = 1;

        StringBuilder line = new StringBuilder();

        line.append('[');
        line.append(i);
        line.append("] ");

        for (int s = 0; s < currentDepth * 2; s++) {
            line.append('-');
        }
        if (currentDepth > 0) {
            line.append(' ');
        }

        line.append(endpoint.getHttpMethod());
        line.append(": ");
        line.append(endpoint.getUrlPath());

        line.append(" (");
        line.append(endpoint.getVariants().size());
        line.append(" variants): PARAMETERS=");
            line.append(endpoint.getParameters());

        line.append("; FILE=");
        line.append(endpoint.getFilePath());

        line.append(" (lines '");
        line.append(endpoint.getStartingLineNumber());
        line.append("'-'");
        line.append(endpoint.getEndingLineNumber());
        line.append("')");

        println(line.toString());

        for (Endpoint variant : endpoint.getVariants()) {
            numPrinted += printEndpointWithVariants(i + numPrinted, currentDepth + 1, variant);
        }

        return numPrinted;
    }

    private static List<Endpoint> listEndpoints(File rootFile, Collection<FrameworkType> frameworkTypes) {
        List<Endpoint> endpoints = list();

        File sourceRootFile = rootFile;
        TemporaryExtractionLocation zipExtractor = null;
        if (TemporaryExtractionLocation.isArchive(rootFile.getAbsolutePath())) {
            zipExtractor = new TemporaryExtractionLocation(rootFile.getAbsolutePath());
            zipExtractor.extract();

            sourceRootFile = zipExtractor.getOutputPath();
        }

        if (frameworkTypes.size() == 1 && frameworkTypes.iterator().next() == FrameworkType.DETECT) {
            frameworkTypes.addAll(FrameworkCalculator.getTypes(rootFile));
        }

        List<EndpointDatabase> databases = list();
        for (FrameworkType frameworkType : frameworkTypes) {
            EndpointDatabase database = EndpointDatabaseFactory.getDatabase(sourceRootFile, frameworkType);
            if (database != null) {
                databases.add(database);
            } else {
                println("EndpointDatabaseFactory.getDatabase returned null for framework type " + frameworkType);
            }
        }

        for (EndpointDatabase db : databases) {
            endpoints.addAll(db.generateEndpoints());
        }

        //  Don't do any validation if we're just writing JSON without any output
        if (printFormat == FULL_JSON || printFormat == SIMPLE_JSON) {
            return endpoints;
        }

        List<Endpoint> allEndpoints = EndpointUtil.flattenWithVariants(endpoints);

        int numPrimaryEndpoints = endpoints.size();
        int numEndpoints = allEndpoints.size();

        totalDetectedEndpoints += numEndpoints;
        totalDistinctEndpoints += numPrimaryEndpoints;

        int i = 0;
        for (Endpoint endpoint : endpoints) {
            printEndpointWithVariants(i++, 0, endpoint);
        }

        if (endpoints.isEmpty()) {
            println("No endpoints were found.");

        } else {
            println("Generated " + numPrimaryEndpoints +
                                " distinct endpoints with " +
                                (numEndpoints - numPrimaryEndpoints) +
                                " variants for a total of " + numEndpoints +
                                " endpoints");
        }

        if (EndpointValidation.validateSerialization(sourceRootFile, endpoints)) {
            println("Successfully validated serialization for these endpoints");
        } else {
            println("Failed to validate serialization for at least one of these endpoints");
        }

        if (!EndpointValidation.validateDuplicates(endpoints)) {
        	numProjectsWithDuplicates++;
        }

        //  Run endpoint testing against a given server
        if (testUrlPath != null) {
            EndpointTester tester = new EndpointTester(testUrlPath);

            println("Testing endpoints against server at: " + testUrlPath);

            if (testCredentials != null) {
                try {
                    if (tester.authorize(testCredentials, null) < 400) {
                        println("Successfully authenticated");
                    }
                } catch (IOException e) {
                    println("Warning - unable to authorize against server");
                }
            }

            List<Endpoint> successfulEndpoints = list();
            List<Endpoint> failedEndpoints = list();
            for (Endpoint endpoint : allEndpoints) {
                boolean skip = false;
                for (EndpointPathNode node : endpoint.getUrlPathNodes()) {
                    if (node.getClass().equals(WildcardEndpointPathNode.class)) {
                        skip = true;
                        break;
                    }
                }

                if (skip) {
                    continue;
                }

                try {
                    int responseCode = tester.test(endpoint, testCredentials);
                    if (responseCode != 404) {
                        successfulEndpoints.add(endpoint);
                    } else {
                        failedEndpoints.add(endpoint);
                    }
                } catch (IOException e) {
                    //  Any non-404 error is considered "successful", since any other 4xx or 5xx may indicate
                    //  that the endpoint exists but incorrect parameters were provided
                    if (e.getMessage().contains("Server returned HTTP response code") && !e.getMessage().contains("code: 404")) {
                        successfulEndpoints.add(endpoint);
                    } else {
                        failedEndpoints.add(endpoint);
                    }
                }
            }

            for (Endpoint endpoint : failedEndpoints) {
                println("Failed: " + endpoint.getUrlPath() + "[" + endpoint.getHttpMethod() + "]");
            }

            println(successfulEndpoints.size() + "/" + (successfulEndpoints.size() + failedEndpoints.size()) + " endpoints were queryable");
            println("(" + (allEndpoints.size() - successfulEndpoints.size() - failedEndpoints.size()) + " endpoints skipped since they had a wildcard in the URL)");
        }

        int numMissingStartLine = 0;
        int numMissingEndLine = 0;
        int numSameLineRange = 0;
        for (Endpoint endpoint : EndpointUtil.flattenWithVariants(endpoints)) {
            if (endpoint.getStartingLineNumber() < 0) {
                numMissingStartLine++;
            }
            if (endpoint.getEndingLineNumber() < 0) {
                numMissingEndLine++;
            }
            if (endpoint.getStartingLineNumber() >= 0 && endpoint.getStartingLineNumber() == endpoint.getEndingLineNumber()) {
                numSameLineRange++;
            }
        }

        println(numMissingStartLine + " endpoints were missing code start line");
        println(numMissingEndLine + " endpoints were missing code end line");
        println(numSameLineRange + " endpoints had the same code start and end line");

        List<RouteParameter> distinctParameters = list();
        for (Endpoint endpoint : endpoints) {
            distinctParameters.addAll(endpoint.getParameters().values());
        }

        int numTotalParameters = 0;
        for (Endpoint endpoint : allEndpoints) {
            numTotalParameters += endpoint.getParameters().size();
        }

        totalDistinctParameters += distinctParameters.size();
        totalDetectedParameters += numTotalParameters;

        println("Generated " + distinctParameters.size() + " distinct parameters");
        println("Generated " + numTotalParameters + " total parameters");

        Map<RouteParameterType, Integer> typeOccurrences = map();
        int numHaveDataType = 0;
        int numHaveParamType = 0;
        int numHaveAcceptedValues = 0;
        for (RouteParameter param : distinctParameters) {
            if (param.getDataType() != null) {
                ++numHaveDataType;
            }
            if (param.getParamType() != RouteParameterType.UNKNOWN) {
                ++numHaveParamType;
            }
            if (param.getAcceptedValues() != null && param.getAcceptedValues().size() > 0) {
                ++numHaveAcceptedValues;
            }

            if (!typeOccurrences.containsKey(param.getParamType())) {
                typeOccurrences.put(param.getParamType(), 1);
            } else {
                int o = typeOccurrences.get(param.getParamType());
                typeOccurrences.put(param.getParamType(), o + 1);
            }
        }

        int numParams = distinctParameters.size();
        println("- " + numHaveDataType + "/" + numParams + " have their data type");
        println("- " + numHaveAcceptedValues + "/" + numParams + " have a list of accepted values");
        println("- " + numHaveParamType + "/" + numParams + " have their parameter type");
        for (RouteParameterType paramType : typeOccurrences.keySet()) {
            println("--- " + paramType.name() + ": " + typeOccurrences.get(paramType));
        }

        if (zipExtractor != null) {
            zipExtractor.release();
        }

        return endpoints;
    }

    private static Endpoint.Info[] getEndpointInfo(List<Endpoint> endpoints) {
        List<Endpoint> allEndpoints = EndpointUtil.flattenWithVariants(endpoints);
        Endpoint.Info[] endpointsInfos = new Endpoint.Info[allEndpoints.size()];

        for (int i = 0; i < allEndpoints.size(); i++) {
            endpointsInfos[i] = Endpoint.Info.fromEndpoint(allEndpoints.get(i));
        }

        return endpointsInfos;
    }

    private static void resetLoggingConfiguration() {
        ConsoleAppender console = new ConsoleAppender(); //create appender
        String pattern = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(pattern));

        if (logging == Logging.ON) {
            console.setThreshold(Level.DEBUG);
        } else {
            console.setThreshold(Level.ERROR);
        }

        console.activateOptions();
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(console);
    }
}