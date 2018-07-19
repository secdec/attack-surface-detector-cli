# attack-surface-detector-cli

The `attack-surface-detector-cli` program is a command-line tool that takes in a folder location and outputs the set of endpoints detected within that codebase. It uses the [ASTAM Correlator's](https://github.com/secdec/astam-correlator) `threadfix-ham` module to generate these endpoints.
## Usage

Once you have a compiled JAR, run the program with:

    java -jar attack-surface-detector-cli.jar <root-folder> [-flags]

If successful, you should see various output in the console regarding endpoints declared in the given code.

    > java -jar attack-surface-detector-cli.jar "C:\.....\AltoroJ 3.1.1"
    Beginning endpoint detection for 'C:\.....\AltoroJ 3.1.1'
    Using framework=JSP
    Generated 47 distinct endpoints with 26 variants for a total of 73 endpoints
    [0] GET: /admin/admin.jsp (1 variants): PARAMETERS={}; FILE=/WebContent/admin/admin.jsp (lines '1'-'194')
    [1] -- POST: /admin/admin.jsp (0 variants): PARAMETERS={}; FILE=/WebContent/admin/admin.jsp (lines '1'-'194')
    [2] GET: /admin/feedbackReview.jsp (1 variants): PARAMETERS={}; FILE=/WebContent/admin/feedbackReview.jsp (lines '1'-'75')
    [3] -- POST: /admin/feedbackReview.jsp (0 variants): PARAMETERS={}; FILE=/WebContent/admin/feedbackReview.jsp (lines '1'-'75')
    
    ...
    
    -- DONE --
    Generated 73 total endpoints
    Generated 50 total parameters
    To enable logging include the -debug argument

## Saving to JSON

The detected endpoints can be serialized and stored in a JSON file. This is done using the `-json` and `-output=...` parameters:

    > java -jar attack-surface-detector-cli.jar C:\...\SourceCode -json -output=C:\...\endpoints.json
    
This `json` output carries extra information and is intended to be used with the `threadfix-ham` module from the ASTAM Correlator through `com.denimgroup.threadfix.framework.engine.full.EndpointSerialization.deserializeAll(..)`. A simplified output can be created by using the `-simple-json` flag instead of `-json`. See the Options section below for more details.

## Options

    <folder-path>
Runs endpoint detection on code location in the given folder path. _(Required, unless -path-list-file=... is specified)_

***

    -framework=<framework>
Specifies the web framework used in the given code location. If undefined, the HAM module will attempt to detect the framework type automatically. Accepted values at time of writing are:
1. `JSP` (Java JSP and Servlets)
2. `DOT_NET_MVC` (ASP.NET MVC)
3. `DOT_NET_WEB_FORMS` (ASP.NET Web Forms)
4. `STRUTS`
5. `SPRING_MVC`
6. `RAILS` (Ruby on Rails)
7. `PYTHON` (Django)

***

    -debug
Enables `DEBUG` log messages.

***

    -simple
Disables diagnostic messages that are usually output by the tool.

***

    -path-list-file="/path/to/list.txt"
Runs endpoint detection on each code location specified in the given file list. An example can be found [here.](https://github.com/secdec/astam-correlator/blob/master/threadfix-cli-endpoints/sample-project-list.txt)

***

    -json
Outputs a complete JSON-serialized version of the detected endpoints, intended for deserialization by the `threadfix-ham` module. Endpoints are stored with framework-specific content, wrapped in an object indicating the framework type for that endpoint.

If multiple projects are scanned at once, the JSON output will contain a single array of all endpoints from all scanned projects.

***

    -simple-json
Outputs a simplified JSON-serialized version of the detected endpoints, intended for use by any JSON parser. It provides a consistent and simplified format for all generated endpoints.

If multiple projects are scanned at once, the JSON output will contain a single array of all endpoints from all scanned projects.

***

    -output=...
Specifies an output file that endpoints will be written to when using JSON serialization. This requires either `-json` or `-simple-json` to also be set, otherwise this flag has no effect.


***

    -validation-server=http://localhost:1234/abc...
Specifies a base URL path that will be used and queried against to test all detected endpoints. Endpoints that return `404` will be marked as "failed" and listed in the terminal.

***

    - validation-server-auth=<login-endpoint>;usename=foo;password=bar;...
Specifies how to authenticate against the server provided with `-validation-server`. Arguments are separated by semicolons `;`. The first argument will be the endpoint to use for authentication. Subsequent arguments will be sent to the endpoint while authenticating.

The specified endpoint will be POSTed to, and the provided query parameters will be encoded as Form parameters. Result of authentication will be output in the console.

Any cookies found in the response will be attached to all subsequent requests during testing.

## Build Instructions
The module can be built with maven:

    C:\...\attack-surface-detector-cli> mvn clean package

You'll find the compiled JAR at `.../target/attack-surface-detector-cli-<version>-jar-with-dependencies.jar`.

For simplicity, this `jar` is referred to as `attack-surface-detector-cli.jar` throughout this guide.


## Debug Information

The console output can include various debug information at the end of a scan for validation. This typically looks like:

    Got an absolute file path when a relative path was expected instead, for: GET,/^(?P<i18>[\w\-_]+)/^admin/^mypageextension/$,{}
    Failed to validate serialization for at least one of these endpoints
    251 endpoints were missing code start line
    251 endpoints were missing code end line
    0 endpoints had the same code start and end line
    Generated 38 parameters
    - 38/38 have their data type
    - 0/38 have a list of accepted values
    - 0/38 have their parameter type
    --- UNKNOWN: 38

Endpoints without a line range will have their start and end lines set to `-1`. Parameters are associated with a name, data type, and parameter type, which are summarized at the end. The parameter types are:

- `UNKNOWN` - The parameter type could not be detected
- `QUERY_STRING` - A parameter stored in the URL of the request ie `/index.php?query=value`
- `FORM_DATA` - Any form-type data. Can also be used to store the body of a request
- `PARAMETRIC_ENDPOINT` - A parameter embedded in the URL, ie `/books/{bookId}/order`
- `SESSION` - A parameter stored as session data
- `COOKIE` - A parameter stored as a cookie
- `FILES` - A parameter for file uploads

-----

_*This material is based on research sponsored by the Department of Homeland
Security (DHS) Science and Technology Directorate, Cyber Security Division
(DHS S&T/CSD) via contract number HHSP233201600058C.*_

