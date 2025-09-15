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
* [`HttpSessionContext`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpSessionContext.html)
* [`ServletContext`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletContext.html)
* [`ServletInputStream`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletInputStream.html)
* [`ServletOutputStream`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletOutputStream.html)
* [`ServletPrintWriter`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletServletPrintWriter.html)

This library is for the legacy `javax.servlet` API. If your system uses `jakarta.servlet`, use [`soklet-servlet-jakarta`](https://github.com/soklet/soklet-servlet-jakarta) instead.

This library has zero dependencies (not counting Soklet). Just add the JAR to your project and you're good to go. 

**Note: this README provides a high-level overview of Soklet's Servlet Integration.**<br/>
**For details, please refer to the official documentation at [https://www.soklet.com/docs/servlet-integration](https://www.soklet.com/docs/servlet-integration).**

## Installation

### Maven

Like Soklet, this library assumes Java 17+.

```xml
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet-servlet-javax</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle

```js
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.soklet:soklet-servlet-javax:1.0.0'
}
```

## Usage

A normal Servlet API integration looks like the following:

1. Given a Soklet [`Request`](https://javadoc.soklet.com/com/soklet/core/Request.html), create both an [`HttpServletRequest`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletRequest.html) and an [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html).
2. Write whatever is needed to [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html)
3. Convert the [`HttpServletResponse`](https://javax.javadoc.soklet.com/com/soklet/servlet/javax/SokletHttpServletResponse.html) to a Soklet [`MarshaledResponse`](https://javadoc.soklet.com/com/soklet/core/MarshaledResponse.html)

```java
@GET("/servlet-example")
public MarshaledResponse servletExample(Request request) {
  // Create an HttpServletRequest from the Soklet Request
  HttpServletRequest httpServletRequest = 
    SokletHttpServletRequest.withRequest(request);

  // Create an HttpServletResponse from the Soklet Request
  SokletHttpServletResponse httpServletResponse = 
    SokletHttpServletResponse.withRequest(request);

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