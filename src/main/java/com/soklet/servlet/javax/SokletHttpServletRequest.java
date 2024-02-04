/*
 * Copyright 2024 Revetware LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soklet.servlet.javax;

import com.soklet.core.Request;
import com.soklet.core.Utilities;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class SokletHttpServletRequest implements HttpServletRequest {
	@Nonnull
	private static final Charset DEFAULT_CHARSET;

	static {
		DEFAULT_CHARSET = StandardCharsets.ISO_8859_1; // Per Servlet spec
	}

	@Nonnull
	private final Request request;
	@Nonnull
	private final Map<String, Object> attributes;
	@Nonnull
	private final List<Cookie> cookies;
	@Nullable
	private SokletHttpSession httpSession;
	@Nullable
	private Charset charset;
	@Nullable
	private String contentType;
	@Nullable
	private final String host;
	@Nullable
	private final Integer port;

	public SokletHttpServletRequest(@Nonnull Request request) {
		this(request, null, null);
	}

	public SokletHttpServletRequest(@Nonnull Request request,
																	@Nullable String host,
																	@Nullable Integer port) {
		requireNonNull(request);

		this.request = request;
		this.attributes = new HashMap<>();
		this.cookies = parseCookies(request);
		this.charset = parseCharacterEncoding(request).orElse(null);
		this.contentType = parseContentType(request).orElse(null);
		this.host = host;
		this.port = port;
	}

	@Nonnull
	protected Request getRequest() {
		return this.request;
	}

	@Nonnull
	protected Map<String, Object> getAttributes() {
		return this.attributes;
	}

	@Nonnull
	protected List<Cookie> parseCookies(@Nonnull Request request) {
		requireNonNull(request);

		Map<String, Set<String>> cookies = request.getCookies();
		List<Cookie> convertedCookies = new ArrayList<>(cookies.size());

		for (Entry<String, Set<String>> entry : cookies.entrySet()) {
			String name = entry.getKey();
			Set<String> values = entry.getValue();

			// Should never occur...
			if (name == null)
				continue;

			for (String value : values)
				convertedCookies.add(new Cookie(name, value));
		}

		return convertedCookies;
	}

	@Nonnull
	protected Optional<Charset> parseCharacterEncoding(@Nonnull Request request) {
		requireNonNull(request);
		return Utilities.extractCharsetFromHeaders(request.getHeaders());
	}

	@Nonnull
	protected Optional<String> parseContentType(@Nonnull Request request) {
		requireNonNull(request);
		return Utilities.extractContentTypeFromHeaders(request.getHeaders());
	}

	@Nonnull
	protected Optional<SokletHttpSession> getHttpSession() {
		return Optional.ofNullable(this.httpSession);
	}

	protected void setHttpSession(@Nullable SokletHttpSession httpSession) {
		this.httpSession = httpSession;
	}

	@Nonnull
	protected Optional<Charset> getCharset() {
		return Optional.ofNullable(this.charset);
	}

	protected void setCharset(@Nullable Charset charset) {
		this.charset = charset;
	}

	@Nonnull
	protected Optional<String> getHost() {
		return Optional.ofNullable(this.host);
	}

	@Nonnull
	protected Optional<Integer> getPort() {
		return Optional.ofNullable(this.port);
	}

	// Implementation of HttpServletRequest methods below:

	// Helpful reference at https://stackoverflow.com/a/21046620 by Victor Stafusa - BozoNaCadeia
	//
	// Method              URL-Decoded Result
	// ----------------------------------------------------
	// getContextPath()        no      /app
	// getLocalAddr()                  127.0.0.1
	// getLocalName()                  30thh.loc
	// getLocalPort()                  8480
	// getMethod()                     GET
	// getPathInfo()           yes     /a?+b
	// getProtocol()                   HTTP/1.1
	// getQueryString()        no      p+1=c+d&p+2=e+f
	// getRequestedSessionId() no      S%3F+ID
	// getRequestURI()         no      /app/test%3F/a%3F+b;jsessionid=S+ID
	// getRequestURL()         no      http://30thh.loc:8480/app/test%3F/a%3F+b;jsessionid=S+ID
	// getScheme()                     http
	// getServerName()                 30thh.loc
	// getServerPort()                 8480
	// getServletPath()        yes     /test?
	// getParameterNames()     yes     [p 2, p 1]
	// getParameter("p 1")     yes     c d

	@Override
	@Nullable
	public String getAuthType() {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nonnull
	public Cookie[] getCookies() {
		return this.cookies.toArray(new Cookie[0]);
	}

	@Override
	public long getDateHeader(@Nullable String name) {
		if (name == null)
			return -1;

		String value = getHeader(name);

		if (value == null)
			return -1;

		try {
			return Long.valueOf(name, 10);
		} catch (Exception ignored) {
			// Per spec
			throw new IllegalArgumentException(format("Header with name '%s' and value '%s' cannot be converted to a date", name, value));
		}
	}

	@Override
	@Nullable
	public String getHeader(@Nullable String name) {
		if (name == null)
			return null;

		return getRequest().getHeader(name).orElse(null);
	}

	@Override
	@Nonnull
	public Enumeration<String> getHeaders(@Nullable String name) {
		if (name == null)
			return Collections.emptyEnumeration();

		Set<String> values = request.getHeaders().get(name);
		return values == null ? Collections.emptyEnumeration() : Collections.enumeration(values);
	}

	@Override
	@Nonnull
	public Enumeration<String> getHeaderNames() {
		return Collections.enumeration(getRequest().getHeaders().keySet());
	}

	@Override
	public int getIntHeader(@Nullable String name) {
		if (name == null)
			return -1;

		String value = getHeader(name);

		if (value == null)
			return -1;

		// Throws NumberFormatException if parsing fails, per spec
		return Integer.valueOf(name, 10);
	}

	@Override
	@Nonnull
	public String getMethod() {
		return getRequest().getHttpMethod().name();
	}

	@Override
	@Nullable
	public String getPathInfo() {
		return getRequest().getPath();
	}

	@Override
	@Nullable
	public String getPathTranslated() {
		return getRequest().getPath();
	}

	@Override
	@Nonnull
	public String getContextPath() {
		return "";
	}

	@Override
	@Nullable
	public String getQueryString() {
		try {
			URI uri = new URI(request.getUri());
			return uri.getQuery();
		} catch (Exception ignored) {
			return null;
		}
	}

	@Override
	@Nullable
	public String getRemoteUser() {
		// This is legal according to spec
		return null;
	}

	@Override
	public boolean isUserInRole(@Nullable String role) {
		// This is legal according to spec
		return false;
	}

	@Override
	@Nullable
	public Principal getUserPrincipal() {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nullable
	public String getRequestedSessionId() {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nonnull
	public String getRequestURI() {
		return getRequest().getPath();
	}

	@Override
	@Nonnull
	public StringBuffer getRequestURL() {
		// Path only (no query parameters) preceded by remote protocol, host, and port (if available)
		// e.g. https://www.soklet.com/test/abc
		String clientUrlPrefix = Utilities.extractClientUrlPrefixFromHeaders(getRequest().getHeaders()).orElse(null);
		return new StringBuffer(clientUrlPrefix == null ? getRequest().getPath() : format("%s%s", clientUrlPrefix, getRequest().getPath()));
	}

	@Override
	@Nonnull
	public String getServletPath() {
		// This is legal according to spec
		return "";
	}

	@Override
	@Nullable
	public HttpSession getSession(boolean create) {
		SokletHttpSession currentHttpSession = getHttpSession().orElse(null);

		if (create && currentHttpSession == null) {
			currentHttpSession = new SokletHttpSession(getServletContext());
			setHttpSession(currentHttpSession);
		}

		return currentHttpSession;
	}

	@Override
	@Nonnull
	public HttpSession getSession() {
		SokletHttpSession currentHttpSession = getHttpSession().orElse(null);

		if (currentHttpSession == null) {
			currentHttpSession = new SokletHttpSession(getServletContext());
			setHttpSession(currentHttpSession);
		}

		return currentHttpSession;
	}

	@Override
	@Nonnull
	public String changeSessionId() {
		SokletHttpSession currentHttpSession = getHttpSession().orElse(null);

		if (currentHttpSession == null)
			throw new IllegalStateException("No session is present");

		UUID newSessionId = UUID.randomUUID();
		currentHttpSession.setSessionId(newSessionId);
		return String.valueOf(newSessionId);
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		// This is legal according to spec
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		// This is legal according to spec
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		// This is legal according to spec
		return false;
	}

	@Override
	@Deprecated
	public boolean isRequestedSessionIdFromUrl() {
		// This is legal according to spec
		return false;
	}

	@Override
	public boolean authenticate(@Nonnull HttpServletResponse httpServletResponse) throws IOException, ServletException {
		requireNonNull(httpServletResponse);
		// TODO: perhaps revisit this in the future
		throw new UnsupportedEncodingException("Authentication is not supported");
	}

	@Override
	public void login(@Nullable String username,
										@Nullable String password) throws ServletException {
		// This is legal according to spec
		throw new ServletException("Authentication login is not supported");
	}

	@Override
	public void logout() throws ServletException {
		// This is legal according to spec
		throw new ServletException("Authentication logout is not supported");
	}

	@Override
	@Nonnull
	public Collection<Part> getParts() throws IOException, ServletException {
		// Legal if the request body is larger than maxRequestSize, or any Part in the request is larger than maxFileSize,
		// or there is no @MultipartConfig or multipart-config in deployment descriptors
		throw new IllegalStateException("Servlet multipart configuration is not supported");
	}

	@Override
	@Nullable
	public Part getPart(@Nullable String name) throws IOException, ServletException {
		// Legal if the request body is larger than maxRequestSize, or any Part in the request is larger than maxFileSize,
		// or there is no @MultipartConfig or multipart-config in deployment descriptors
		throw new IllegalStateException("Servlet multipart configuration is not supported");
	}

	@Override
	@Nonnull
	public <T extends HttpUpgradeHandler> T upgrade(@Nullable Class<T> handlerClass) throws IOException, ServletException {
		// Legal if the given handlerClass fails to be instantiated
		throw new ServletException("Servlet multipart configuration is not supported");
	}

	@Override
	@Nullable
	public Object getAttribute(@Nullable String name) {
		if (name == null)
			return null;

		return getAttributes().get(name);
	}

	@Override
	@Nonnull
	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(getAttributes().keySet());
	}

	@Override
	@Nonnull
	public String getCharacterEncoding() {
		Charset charset = getCharset().orElse(null);
		return charset == null ? null : charset.name();
	}

	@Override
	public void setCharacterEncoding(@Nullable String env) throws UnsupportedEncodingException {
		// Note that spec says: "This method must be called prior to reading request parameters or
		// reading input using getReader(). Otherwise, it has no effect."
		// ...but we don't need to care about this because Soklet requests are byte arrays of finite size, not streams
		if (env == null) {
			setCharset(null);
		} else {
			try {
				setCharset(Charset.forName(env));
			} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
				throw new UnsupportedEncodingException(format("Not sure how to handle character encoding '%s'", env));
			}
		}
	}

	@Override
	public int getContentLength() {
		byte[] body = request.getBody().orElse(null);
		return body == null ? 0 : body.length;
	}

	@Override
	public long getContentLengthLong() {
		byte[] body = request.getBody().orElse(null);
		return body == null ? 0 : body.length;
	}

	@Override
	@Nullable
	public String getContentType() {
		return this.contentType;
	}

	@Override
	@Nonnull
	public ServletInputStream getInputStream() throws IOException {
		byte[] body = getRequest().getBody().orElse(new byte[]{});
		return new SokletServletInputStream(new ByteArrayInputStream(body));
	}

	@Override
	@Nullable
	public String getParameter(@Nullable String name) {
		String value = null;

		// First, check query parameters.
		if (getRequest().getQueryParameters().keySet().contains(name)) {
			// If there is a query parameter with the given name, return it
			value = getRequest().getQueryParameter(name).orElse(null);
		} else if (getRequest().getFormParameters().keySet().contains(name)) {
			// Otherwise, check form parameters in request body
			value = getRequest().getFormParameter(name).orElse(null);
		}

		return value;
	}

	@Override
	@Nonnull
	public Enumeration<String> getParameterNames() {
		Set<String> queryParameterNames = getRequest().getQueryParameters().keySet();
		Set<String> formParameterNames = getRequest().getFormParameters().keySet();

		Set<String> parameterNames = new HashSet<>(queryParameterNames.size() + formParameterNames.size());
		parameterNames.addAll(queryParameterNames);
		parameterNames.addAll(formParameterNames);

		return Collections.enumeration(parameterNames);
	}

	@Override
	@Nullable
	public String[] getParameterValues(@Nullable String name) {
		if (name == null)
			return null;

		if (!getRequest().getQueryParameters().keySet().contains(name) &&
				!getRequest().getFormParameters().keySet().contains(name))
			return null;

		List<String> parameterValues = new ArrayList<>();

		for (Entry<String, Set<String>> entry : getRequest().getQueryParameters().entrySet())
			parameterValues.addAll(entry.getValue());

		for (Entry<String, Set<String>> entry : getRequest().getFormParameters().entrySet())
			parameterValues.addAll(entry.getValue());

		return parameterValues.toArray(new String[0]);
	}

	@Override
	@Nonnull
	public Map<String, String[]> getParameterMap() {
		Map<String, Set<String>> parameterMap = new HashMap<>();

		// Mutable copy of entries
		for (Entry<String, Set<String>> entry : getRequest().getQueryParameters().entrySet())
			parameterMap.put(entry.getKey(), new HashSet<>(entry.getValue()));

		// Add form parameters to entries
		for (Entry<String, Set<String>> entry : getRequest().getFormParameters().entrySet()) {
			Set<String> existingEntries = parameterMap.get(entry.getKey());

			if (existingEntries != null)
				existingEntries.addAll(entry.getValue());
			else
				parameterMap.put(entry.getKey(), entry.getValue());
		}

		Map<String, String[]> finalParameterMap = new HashMap<>();

		for (Entry<String, Set<String>> entry : parameterMap.entrySet())
			finalParameterMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));

		return Collections.unmodifiableMap(finalParameterMap);
	}

	@Override
	@Nonnull
	public String getProtocol() {
		return "HTTP/1.1";
	}

	@Override
	@Nonnull
	public String getScheme() {
		// Soklet only supports HTTP because it's intended to live behind a load balancer/SSL termination point
		return "http";
	}

	@Override
	@Nonnull
	public String getServerName() {
		// Path only (no query parameters) preceded by remote protocol, host, and port (if available)
		// e.g. https://www.soklet.com/test/abc
		String clientUrlPrefix = Utilities.extractClientUrlPrefixFromHeaders(getRequest().getHeaders()).orElse(null);

		if (clientUrlPrefix == null)
			return getLocalName();

		clientUrlPrefix = clientUrlPrefix.toLowerCase(Locale.ROOT);

		// Remove protocol prefix
		if (clientUrlPrefix.startsWith("https://"))
			clientUrlPrefix = clientUrlPrefix.replace("https://", "");
		else if (clientUrlPrefix.startsWith("http://"))
			clientUrlPrefix = clientUrlPrefix.replace("http://", "");

		// Remove "/" and anything after it
		int indexOfFirstSlash = clientUrlPrefix.indexOf("/");

		if (indexOfFirstSlash != -1)
			clientUrlPrefix = clientUrlPrefix.substring(0, indexOfFirstSlash);

		// Remove ":" and anything after it (port)
		int indexOfColon = clientUrlPrefix.indexOf(":");

		if (indexOfColon != -1)
			clientUrlPrefix = clientUrlPrefix.substring(0, indexOfColon);

		return clientUrlPrefix;
	}

	@Override
	public int getServerPort() {
		// Path only (no query parameters) preceded by remote protocol, host, and port (if available)
		// e.g. https://www.soklet.com/test/abc
		String clientUrlPrefix = Utilities.extractClientUrlPrefixFromHeaders(getRequest().getHeaders()).orElse(null);

		if (clientUrlPrefix == null)
			return getLocalPort();

		clientUrlPrefix = clientUrlPrefix.toLowerCase(Locale.ROOT);

		boolean https = false;

		// Remove protocol prefix
		if (clientUrlPrefix.startsWith("https://")) {
			clientUrlPrefix = clientUrlPrefix.replace("https://", "");
			https = true;
		} else if (clientUrlPrefix.startsWith("http://")) {
			clientUrlPrefix = clientUrlPrefix.replace("http://", "");
		}

		// Remove "/" and anything after it
		int indexOfFirstSlash = clientUrlPrefix.indexOf("/");

		if (indexOfFirstSlash != -1)
			clientUrlPrefix = clientUrlPrefix.substring(0, indexOfFirstSlash);

		String[] hostAndPortComponents = clientUrlPrefix.split(":");

		// No explicit port?  Look at protocol for guidance
		if (hostAndPortComponents.length == 1)
			return https ? 443 : 80;

		try {
			return Integer.parseInt(hostAndPortComponents[1], 10);
		} catch (Exception ignored) {
			return getLocalPort();
		}
	}

	@Override
	@Nonnull
	public BufferedReader getReader() throws IOException {
		Charset charset = getCharset().orElse(DEFAULT_CHARSET);
		InputStream inputStream = new ByteArrayInputStream(getRequest().getBody().orElse(new byte[0]));
		return new BufferedReader(new InputStreamReader(inputStream, charset));
	}

	@Override
	@Nullable
	public String getRemoteAddr() {
		String xForwardedForHeader = getRequest().getHeader("X-Forwarded-For").orElse(null);

		if (xForwardedForHeader == null)
			return null;

		// Example value: 203.0.113.195,2001:db8:85a3:8d3:1319:8a2e:370:7348,198.51.100.178
		String[] components = xForwardedForHeader.split(",");

		if (components.length == 0 || components[0] == null)
			return null;

		String value = components[0].trim();
		return value.length() > 0 ? value : null;
	}

	@Override
	@Nullable
	public String getRemoteHost() {
		// Path only (no query parameters) preceded by remote protocol, host, and port (if available)
		// e.g. https://www.soklet.com/test/abc
		String clientUrlPrefix = Utilities.extractClientUrlPrefixFromHeaders(getRequest().getHeaders()).orElse(null);

		if (clientUrlPrefix != null) {
			clientUrlPrefix = clientUrlPrefix.toLowerCase(Locale.ROOT);

			// Remove protocol prefix
			if (clientUrlPrefix.startsWith("https://"))
				clientUrlPrefix = clientUrlPrefix.replace("https://", "");
			else if (clientUrlPrefix.startsWith("http://"))
				clientUrlPrefix = clientUrlPrefix.replace("http://", "");

			// Remove "/" and anything after it
			int indexOfFirstSlash = clientUrlPrefix.indexOf("/");

			if (indexOfFirstSlash != -1)
				clientUrlPrefix = clientUrlPrefix.substring(0, indexOfFirstSlash);

			String[] hostAndPortComponents = clientUrlPrefix.split(":");

			String host = null;

			if (hostAndPortComponents != null && hostAndPortComponents.length > 0 && hostAndPortComponents[0] != null)
				host = hostAndPortComponents[0].trim();

			if (host != null && host.length() > 0)
				return host;
		}

		// "If the engine cannot or chooses not to resolve the hostname (to improve performance),
		// this method returns the dotted-string form of the IP address."
		return getRemoteAddr();
	}

	@Override
	public void setAttribute(@Nullable String name,
													 @Nullable Object o) {
		if (name == null)
			return;

		if (o == null)
			removeAttribute(name);
		else
			getAttributes().put(name, o);
	}

	@Override
	public void removeAttribute(@Nullable String name) {
		if (name == null)
			return;

		getAttributes().remove(name);
	}

	@Override
	@Nonnull
	public Locale getLocale() {
		List<Locale> locales = getRequest().getLocales();
		return locales.size() == 0 ? Locale.getDefault() : locales.get(0);
	}

	@Override
	@Nonnull
	public Enumeration<Locale> getLocales() {
		List<Locale> locales = getRequest().getLocales();
		return Collections.enumeration(locales.size() == 0 ? List.of(Locale.getDefault()) : locales);
	}

	@Override
	public boolean isSecure() {
		return getScheme().equals("https");
	}

	@Override
	@Nullable
	public RequestDispatcher getRequestDispatcher(@Nullable String path) {
		// "This method returns null if the servlet container cannot return a RequestDispatcher."
		return null;
	}

	@Override
	@Deprecated
	@Nullable
	public String getRealPath(String path) {
		// "As of Version 2.1 of the Java Servlet API, use ServletContext.getRealPath(java.lang.String) instead."
		return getServletContext().getRealPath(path);
	}

	@Override
	public int getRemotePort() {
		// TODO: implement
		return 0;
	}

	@Override
	@Nonnull
	public String getLocalName() {
		if (getHost().isPresent())
			return getHost().get();

		try {
			String hostName = InetAddress.getLocalHost().getHostName();

			if (hostName != null) {
				hostName = hostName.trim();

				if (hostName.length() > 0)
					return hostName;
			}
		} catch (Exception e) {
			// Ignored
		}

		return "localhost";
	}

	@Override
	@Nonnull
	public String getLocalAddr() {
		try {
			String hostAddress = InetAddress.getLocalHost().getHostAddress();

			if (hostAddress != null) {
				hostAddress = hostAddress.trim();

				if (hostAddress.length() > 0)
					return hostAddress;
			}
		} catch (Exception e) {
			// Ignored
		}

		return "127.0.0.1";
	}

	@Override
	public int getLocalPort() {
		return getPort().orElseThrow(() -> new IllegalStateException(format("%s must be initialized with a port in order to call this method",
				getClass().getSimpleName())));
	}

	@Override
	public ServletContext getServletContext() {
		// TODO: implement (make ServletContext available via builder?)
		return null;
	}

	@Override
	@Nonnull
	public AsyncContext startAsync() throws IllegalStateException {
		throw new IllegalStateException("Soklet does not support async servlet operations");
	}

	@Override
	@Nonnull
	public AsyncContext startAsync(@Nonnull ServletRequest servletRequest,
																 @Nonnull ServletResponse servletResponse) throws IllegalStateException {
		requireNonNull(servletResponse);
		requireNonNull(servletResponse);

		throw new IllegalStateException("Soklet does not support async servlet operations");
	}

	@Override
	public boolean isAsyncStarted() {
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		return false;
	}

	@Override
	@Nonnull
	public AsyncContext getAsyncContext() {
		throw new IllegalStateException("Soklet does not support async servlet operations");
	}

	@Override
	@Nonnull
	public DispatcherType getDispatcherType() {
		// Currently Soklet does not support RequestDispatcher, so this is safe to hardcode
		return DispatcherType.REQUEST;
	}
}