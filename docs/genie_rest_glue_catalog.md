# Genie Built-in Step Catalog

Full reference for all genie-rest, genie-database, and generics built-in steps.
Check this before writing any snippet or Java — most needs are covered here.

---

## genie-rest

### Request Builder Steps

| Step | Purpose |
|---|---|
| `Given start building a new request` | Clears previous request/response state. Call before every independent request. |
| `Given set body to:` | Set request body from DocString (JSON or XML). |
| `Given set body from file '<file>'` | Set request body from a file. |
| `Given set header '<name>' to '<value>'` | Set a single request header. |
| `Given set header '<name>' to:` | Set a header from DocString. |
| `Given set headers using:` | Set multiple headers from a DataTable (`\| name \| value \|`). |
| `Given set the payload content type to '<type>'` | Set Content-Type header. Most common: `application/json`. |
| `Given set the base URL to '<url>'` | Override base URL for this request. |
| `Given accept content of type '<type>'` | Set Accept header. |
| `Given set cookie '<name>' to '<value>'` | Set a cookie. |
| `Given set cookies using:` | Set multiple cookies from DataTable. |
| `Given set parameter '<name>' to '<value>'` | Auto-detects query vs form based on HTTP method. |
| `Given set query parameter '<name>' to '<value>'` | Explicitly set a query parameter. |
| `Given set query parameters using:` | Set multiple query parameters from DataTable. |
| `Given set form parameter '<name>' to '<value>'` | Set a form field. |
| `Given set form parameters using:` | Set multiple form fields from DataTable. |
| `Given set multi-part form parameter '<name>' to '<value>'` | Set a multi-part field. |
| `Given set multi-part form parameters using:` | Set multiple multi-part fields from DataTable. |
| `Given use basic authentication for role '<role>'` | Set Basic Auth using credentials for named role. Requires genie-secrets. |
| `Given use NTLM authentication for role '<role>'` | Set NTLM Auth. Requires genie-secrets. |
| `Given use keystore authentication for role '<role>'` | Set keystore auth. Requires genie-secrets. |
| `Given set client certificate for role '<role>'` | Set client certificate. Requires genie-secrets. |
| `Given ignoring ssl certificate errors` | Skip HTTPS certificate validation. |
| `Given allowing redirections` | Follow redirect responses. |
| `Given follow at most '<n>' redirections` | Cap redirect following. |

### HTTP Action Steps

> In this project, these are encapsulated inside `.snippet` files (including provisioning flows), while `.feature` files stay at business-language level.



| Step | Purpose |
|---|---|
| `When get from path '<path>'` | Send GET request. |
| `When post to path '<path>'` | Send POST request. |
| `When put to path '<path>'` | Send PUT request. |
| `When patch to path '<path>'` | Send PATCH request. |
| `When delete from path '<path>'` | Send DELETE request. |
| `When head of path '<path>'` | Send HEAD request. |
| `When options of path '<path>'` | Send OPTIONS request. |

### Response Assertion Steps

| Step | Purpose |
|---|---|
| `Then response status code is '<code>'` | Assert exact status code. |
| `Then response status code is not '<code>'` | Assert status code is absent. |
| `Then response status code is between '<low>' and '<high>'` | Assert code in range (lower inclusive, upper exclusive). |
| `Then response type is successful` | 2xx shortcut. |
| `Then response type is client error` | 4xx shortcut. |
| `Then response type is server error` | 5xx shortcut. |
| `Then response type is redirectional` | 3xx shortcut. |
| `Then response body contains '<text>'` | Assert body contains substring. |
| `Then response body contains:` | Assert body contains DocString substring. |
| `Then response body does not contain '<text>'` | Assert body does not contain substring. |
| `Then response body contains a key '<path>' with value '<val>'` | Assert nested key value. Supports dot-path: `data.trade.id`. |
| `Then response body contains an element '<path>' of size <n>` | Assert collection size at path. |
| `Then response body contains the following key and values:` | Assert multiple key-value pairs from DataTable. |
| `Then response body matches '<text>'` | Assert body exactly matches text. |
| `Then response body matches json '<file-or-inline>'` | Assert body matches json-unit expression. |
| `Then response body matches json:` | Assert body matches json-unit expression from DocString. |
| `Then response body does not match json '<file-or-inline>'` | Inverse json-unit match. |
| `Then response body is not empty` | Assert body is present. |
| `Then the response body is empty` | Assert body is absent. |
| `Then response header '<name>' matches '<val>'` | Assert header exact match. |
| `Then response header '<name>' contains '<val>'` | Assert header contains substring. |
| `Then response header '<name>' does not contain '<val>'` | Assert header does not contain. |
| `Then response has header '<name>'` | Assert header exists. |
| `Then response does not have header '<name>'` | Assert header absent. |
| `Then response has cookie '<name>'` | Assert cookie exists. |
| `Then response cookie '<name>' matches '<val>'` | Assert cookie exact match. |
| `Then response cookie '<name>' contains '<val>'` | Assert cookie contains substring. |
| `Then response content type matches '<type>'` | Assert Content-Type. |
| `Then response took less than <n> <unit>` | Timing assertion. Units: `milliseconds`, `second`, `minutes`. |
| `Then response took between <n> and <m> <unit>` | Timing range assertion. |
| `Then attach the response body to the report` | Attach body to Genie HTML report. |
| `Then attach the response headers to the report` | Attach headers to Genie HTML report. |
| `Then attach the request body to the report` | Attach request body to Genie HTML report. |
| `Then attach the request headers to the report` | Attach request headers to Genie HTML report. |

---

## genie-database

### Setup Steps

| Step | Purpose |
|---|---|
| `Given switch to data source '<name>'` | Switch to named datasource. |
| `Given switch to default data source` | Return to default datasource. |
| `Given set sql script folder as '<path>'` | Set folder for SQL script loading. |
| `Given define query token '<name>' as '<value>'` | Define named substitution token for SQL. |
| `Given define query token '<name>' from list:` | Define token from DataTable. |

### Action Steps

| Step | Purpose |
|---|---|
| `When execute sql query:` | Run SELECT from DocString. Result available for assertions. |
| `When execute sql script '<file>' for query` | Run SELECT from .sql file. |
| `When execute sql updates:` | Run INSERT/UPDATE/DELETE from DocString. |
| `When execute sql script '<file>' for update` | Run DML from .sql file. |
| `When execute templated sql update on data:` | Run DML with DataTable row substitution. |
| `When set sql update template:` | Set reusable DML template from DocString. |

### Assertion Steps

| Step | Purpose |
|---|---|
| `Then assert query result is '<value>'` | Assert single-row single-column result equals value. |
| `Then assert query result is <n>` | Assert numeric result. |
| `Then assert query result is empty` | Assert no rows returned. |
| `Then assert query result is not empty` | Assert at least one row returned. |
| `Then assert query result matches:` | Assert multi-row DataTable result. |
| `Then assert query result list contains:` | Assert DataTable rows exist in result (any order). |

> **Snippet token escaping:** When referencing a ctx variable inside a snippet SQL DataTable, use `\${tokenName}` (backslash prefix).
> The snippet engine would otherwise intercept the `${...}` syntax. The backslash is stripped before the DatabaseGlue processes it.

---

## generics

| Step | Purpose |
|---|---|
| `Then assert '<var>' equals '<value>'` | Assert stored variable equals value. |
| `Then assert '<var>' NOT equals '<value>'` | Assert stored variable does not equal value. |
| `Then assert '<var>' is null` | Assert stored variable is null. |
| `Then assert '<var>' is NOT null` | Assert stored variable is not null. |
| `Then assert '<var>' is true` | Assert stored variable is boolean true. |
| `Then assert '<var>' is false` | Assert stored variable is boolean false. |
| `Then assert '<a>' and '<b>' is the same` | Assert two variables are equal. |
| `Then assert '<a>' and '<b>' is NOT the same` | Assert two variables differ. |
| `Then assert array '<var>' equals '<value>'` | Assert array variable equals value. |

---
