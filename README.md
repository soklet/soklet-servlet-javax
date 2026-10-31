<a href="https://www.soklet.com">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://cdn.soklet.com/soklet-gh-logo-dark-v2.png">
        <img alt="Soklet" src="https://cdn.soklet.com/soklet-gh-logo-light-v2.png" width="300" height="101">
    </picture>
</a>

## Soklet Servlet Integration (javax) 

[Soklet](https://www.soklet.com) is not a [Servlet Container](https://en.wikipedia.org/wiki/Jakarta_Servlet) - it has its own in-process HTTP server, its own approach to request and response constructs, and so forth.  Soklet applications are intended to be "vanilla" Java applications, as opposed to a [WAR file](https://en.wikipedia.org/wiki/WAR_(file_format)) deployed onto a Java EE App Server.

However, there is a large body of existing code that relies on the Servlet API. To support it, Soklet provides its own implementations of the following Servlet interfaces, which enable interoperability for many common use cases:

* [`HttpServletRequest`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletRequest.html)
* [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html)
* [`HttpSession`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpSession.html)
* [`HttpSessionContext`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpSessionContext.html) (only available for `javax.servlet`)
* [`ServletContext`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletContext.html)
* [`ServletInputStream`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletInputStream.html)
* [`ServletOutputStream`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletOutputStream.html)
* [`ServletPrintWriter`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletPrintWriter.html)

This library is for the legacy `javax.servlet` API. If you need to integrate with the `jakarta.servlet` API, use [`soklet-servlet-jakarta`](https://github.com/soklet/soklet-servlet-jakarta).

This library has no implementation dependencies beyond Soklet and the chosen Servlet API. Applications must supply both: they are provided dependencies of the adapter.

**Note: this README provides a high-level overview of Soklet's Servlet Integration.**<br/>
**For details, please refer to the official documentation at [https://www.soklet.com/docs/servlet-integration](https://www.soklet.com/docs/servlet-integration).**

## Installation

Like Soklet, this library assumes Java 21+.

Version 2.0.0 requires Soklet 4.0.0 or later and is not compatible with Soklet
3.x. Upgrade build toolchains and deployment runtimes that still use Java 17 to
Java 21 or later before adopting these releases. Soklet and the Servlet API remain
provided dependencies: standalone
applications must declare both explicitly, as shown below. When upgrading, update both
the adapter and core coordinates. The four nested adapter builder classes are
now final. The internal `SokletHttpSession.setSessionId(...)` and
`SokletHttpServletResponse.setPrintWriter(...)` hooks are no longer public;
use `HttpServletRequest.changeSessionId()` and `HttpServletResponse.getWriter()`
for the supported servlet operations.

For upgrade details, see the [servlet migration guide](https://github.com/soklet/soklet/blob/master/MIGRATING_TO_4_0.md#servlet-adapters).
Annotated Soklet routes also require the Soklet annotation processor and
`-parameters`; see [Building and Running](https://github.com/soklet/soklet#building-and-running).

Both response conversions preserve empty `204 No Content`, `205 Reset Content`,
and `304 Not Modified` responses as bodyless. Nonempty bodies on these statuses
remain invalid when returned from a Soklet resource method; ordinary empty
`200 OK` responses retain their existing byte-array representation.

Request URLs and redirect authorities always use ASCII port digits, independent
of the JVM's formatting locale. This does not change the locale-sensitive
formatting methods applications use on `ServletPrintWriter`.

### Maven

```xml
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet-servlet-javax</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet</artifactId>
  <version>4.0.0</version>
</dependency>
<dependency>
  <groupId>javax.servlet</groupId>
  <artifactId>javax.servlet-api</artifactId>
  <version>4.0.1</version>
</dependency>
```

### Gradle

```js
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.soklet:soklet-servlet-javax:2.0.0'
  implementation 'com.soklet:soklet:4.0.0'
  implementation 'javax.servlet:javax.servlet-api:4.0.1'
}
```

## Correctness and compatibility notes

- Request parameter methods share one snapshot, retain every repeated value,
  and put query values before form values. Automatic form-body parsing applies
  only to POST with `application/x-www-form-urlencoded`; other bodies remain
  available through the reader or input stream. The first parameter-family call
  consumes an eligible form body, including when looking up a query parameter.
  Query text uses UTF-8 and form text uses the selected request-body charset.
  Malformed or unmappable bytes in parameter names or values raise
  `IllegalRequestException` (HTTP 400 with Soklet's default error marshaler),
  instead of replacement characters. A valid encoded U+FFFD remains legal.
  The shared parameter snapshot retains decoding failures, and later accessors
  rethrow them. At most 1,000 nonempty query and form pairs combined are
  accepted; exceeding that limit raises `IllegalRequestException`. Set the body
  encoding before reading parameters or acquiring a body reader.
- Unsupported request charset declarations remain visible through
  `getCharacterEncoding()`. `getReader()` raises `UnsupportedEncodingException`
  without acquiring the reader; a supported `setCharacterEncoding(...)` override
  or raw input-stream access remains possible. Unsupported form-body encodings
  raise `IllegalRequestException`; URL-query decoding remains UTF-8.
  Reader acquisition and form-body parsing retain the selected encoding, so a
  later context-default change cannot alter the request's reported encoding.
  Acquiring or reading the raw input stream does not lock the encoding metadata;
  an explicit override remains effective until reader or parameter access. The
  reader/input-stream exclusivity rule remains unchanged.
- Session-cookie lookup uses the exact, case-sensitive `JSESSIONID` name.
  Client cookie pairs that the chosen Servlet API cannot represent are ignored
  individually; valid neighboring cookies are retained.
  Session invalidation detaches attributes even if a binding listener throws;
  listener failures can still propagate after cleanup.
- `filesystemResourceRoot` requires an existing, trusted, immutable directory.
  The adapter pins its real path and rejects links outside it during resource
  lookup and listing. Concurrent tree changes are unsupported because a returned
  resource URL is opened later by application code.
- Concurrent setters of an identical session attribute share an in-progress
  binding callback. An ordinary follower waits at most 200 milliseconds or
  until interrupted, then returns without another `valueBound`; it may precede
  attribute publication. Interrupted followers restore their interrupt status.
  The owner remains responsible for publication and balanced unbinding. A later
  replacement or removal supersedes pending publication for that name, so a slow
  listener cannot overwrite the newer value or resurrect a removed attribute.
  A later setter for the same instance reuses its unfinished binding callback
  before publication is decided, restoring publication unless another removal
  or replacement follows.
  Completed listener failures propagate to the owner and followers that observe
  completion before returning. Listener reentry does not wait. Binding
  listeners should avoid waiting for work or locks a concurrent setter may hold.
- Raw `Set-Cookie` headers and cookies added with `addCookie` preserve their
  occurrence order. `setHeader("Set-Cookie", value)` replaces both forms.
  Cookies preceding a later raw header are represented as header values during
  conversion; trailing ordinary cookies remain structured response cookies.
- A response charset that cannot encode text (for example, the JDK's
  decoder-only `ISO-2022-CN`) makes `getWriter()` raise
  `UnsupportedEncodingException` without acquiring the writer or locking the
  output method. Before commitment, a supported encoding override can recover;
  raw output-stream access also remains available.
- Generated error responses use safe plain text and replace stale representation
  metadata. Redirects that clear the body also remove the previous representation's
  metadata. Cookies and unrelated application headers are preserved.
- [`resetBuffer()`](<https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html#resetBuffer()>) clears the writer's pending characters and encoding state as
  well as its bytes, while retaining the acquired writer and its locked charset.
- `getDateHeader()` recognizes IMF-fixdate, RFC 850, and asctime dates using
  strict calendar validation and English names independent of JVM locale
  providers. Impossible dates raise `IllegalArgumentException`. The numeric
  epoch-millisecond fallback remains supported.

These are interoperability adapters, not full servlet containers; unsupported
container features are not implicitly enabled by these corrections.

Query parameters always use UTF-8, independently of the request-body charset.
Context request/response encodings are unspecified (`null`) until configured;
body readers and response writers fall back to ISO-8859-1 when no encoding is
specified. This preserves
the standard `getCharacterEncoding() == null` guard for selecting UTF-8 before
request parameter parsing or reader acquisition.

`println`, `printf`, and `format` do not commit a response merely by being
called. Explicit flush/close and filling the configured buffer still commit.
`sendError` never generates a body for a bodyless HTTP status.

New sessions snapshot the context timeout from minutes into seconds; nonpositive
values mean no timeout, and values above the seconds range saturate at
`Integer.MAX_VALUE`. This is session metadata, not an eviction service:
applications remain responsible for session persistence, expiration, and
invalidation. Updating the context timeout does not change existing sessions.

## HEAD responses and date headers

Create responses with `fromRequest(request, servletContext)` or
`fromHttpServletRequest(servletRequest)` when adapting HEAD requests. These
factories know the request method and preserve a declared representation length
when the servlet captures no body. `fromRawPath` receives only a path; its empty
capture remains an ordinary empty byte-array body, which can replace a declared
HEAD length with zero during Soklet's normal projection.

A servlet's default `doHead` may discard GET bytes and retain only their count.
That count describes the identity representation. Once those bytes are discarded,
the adapter cannot derive dynamic gzip metadata or its compressed length. If HEAD
must match a dynamically compressed GET, generate the GET representation for
Soklet to project, or supply the selected representation's `Content-Encoding`,
`Vary` and length explicitly in a response bound to the HEAD request. A bodyless
servlet HEAD with only an identity count does not enter dynamic compression.

`setDateHeader` and `addDateHeader` accept only dates whose UTC year is 1 through
9999, the range representable by a four-digit HTTP date. Values outside that
range raise `IllegalArgumentException` before changing headers. As with other
header setters, calls after local commitment have no effect.

## Response buffering

The default local commit threshold is 1,024 bytes; `setBufferSize(...)` changes it.
This threshold is not a body-size or memory limit.

`SokletHttpServletResponse` captures its entire body in memory. Writes through
`getWriter()` and `getOutputStream()` accumulate locally; `toMarshaledResponse()`
and `toResponse()` copy the captured body into a finite Soklet response.
Conversion does not send it; delivery begins when the application hands the
resulting response to Soklet.

`flushBuffer()`, writer/output-stream flushes, and buffer-size commits affect only
local commitment. They do not flush network bytes to the client. `isCommitted()`
reports the adapter state, not whether Soklet has sent response headers.
`setBufferSize(...)` controls the local commit threshold, not a body-size or
memory cap: capture continues beyond it. Memory grows with the body, and conversion
requires additional body copies. Bound generated response sizes and concurrent
captures in the application; HTTP request-size settings and native streaming queue
bounds do not limit this output.

The response adapter has no `ResponseStream`-backed mode. For large known-length
files, use [native file responses](https://www.soklet.com/docs/response-writing#file-and-buffer-responses).
For incremental production or live feeds, use [native HTTP streaming](https://www.soklet.com/docs/response-writing#streaming-responses);
for event streams, use [Server-Sent Events](https://www.soklet.com/docs/server-sent-events).
The standalone `SokletServletOutputStream` helper delegates to a supplied sink;
it does not bridge the response adapter's status, headers or commitment state
to that sink. See [response buffering guidance](https://www.soklet.com/docs/servlet-integration#response-buffering-and-streaming).

## Usage

A normal Servlet API integration looks like the following:

1. Given a Soklet [`Request`](https://javadoc.soklet.com/com/soklet/Request.html), create both an [`HttpServletRequest`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletRequest.html) and an [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html).
2. Write whatever is needed to [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html)
3. Convert the [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html) to a Soklet [`MarshaledResponse`](https://javadoc.soklet.com/com/soklet/MarshaledResponse.html)

```java
@GET("/servlet-example")
public MarshaledResponse servletExample(Request request) throws java.io.IOException {
  // Create an HttpServletRequest from the Soklet Request
  HttpServletRequest httpServletRequest =
    SokletHttpServletRequest.fromRequest(request);

  // Create an HttpServletResponse from the HttpServletRequest
  SokletHttpServletResponse httpServletResponse = 
    SokletHttpServletResponse.fromHttpServletRequest(httpServletRequest);

  // Write some data to the response using Servlet APIs
  Cookie cookie = new Cookie("name", "value");
  cookie.setDomain("soklet.com");
  cookie.setMaxAge(60);
  cookie.setPath("/");

  httpServletResponse.setStatus(200);
  httpServletResponse.addHeader("test", "one");
  httpServletResponse.addHeader("test", "two");
  httpServletResponse.addCookie(cookie);
  httpServletResponse.setCharacterEncoding("ISO-8859-1");
  httpServletResponse.getWriter().print("test");    
  
  // Convert HttpServletResponse into a Soklet MarshaledResponse and return it
  return httpServletResponse.toMarshaledResponse();
}

```

Additional documentation is available at [https://www.soklet.com/docs/servlet-integration](https://www.soklet.com/docs/servlet-integration).
