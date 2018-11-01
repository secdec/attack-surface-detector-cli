# attack-surface-detector-cli

### _[Latest Release - 1.3.5](https://github.com/secdec/attack-surface-detector-cli/releases/tag/v1.3.5)_

The `attack-surface-detector-cli` program is a command-line tool that takes in a folder location and outputs the set of endpoints detected within that codebase. It uses the [ASTAM Correlator's](https://github.com/secdec/astam-correlator) `threadfix-ham` module to generate these endpoints. The endpoints are output to the console by default, and can save a JSON version of those endpoints through the `-output-file` and `-json` flags. See the [Wiki](https://github.com/secdec/attack-surface-detector-cli/wiki/Usage,-Parameters,-and-Output) for more details.

This tool supports the following frameworks, as supported by the `threadfix-ham` module:

- ASP.NET MVC / Web API / Core / Web Forms
- Struts
- Django
- Ruby on Rails
- Spring MVC
- JSP

---

Licensed under the [MPL](https://github.com/secdec/attack-surface-detector-cli/blob/master/LICENSE.md) License.

---

_This material is based on research sponsored by the Department of Homeland Security (DHS) Science and Technology Directorate, Cyber Security Division (DHS S&T/CSD) via contract number HHSP233201600058C._
