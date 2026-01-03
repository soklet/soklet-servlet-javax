/*
 * Copyright 2024-2025 Revetware LLC.
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

import com.soklet.Request;
import com.soklet.QueryFormat;
import com.soklet.Utilities;

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
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
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
import static java.util.Locale.ROOT;
import static java.util.Locale.US;
import static java.util.Locale.getDefault;
import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link HttpServletRequest}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class SokletHttpServletRequest implements HttpServletRequest {
	@Nonnull
	private static final Charset DEFAULT_CHARSET;
	@Nonnull
	private static final DateTimeFormatter RFC_1123_PARSER;
	@Nonnull
	private static final DateTimeFormatter RFC_1036_PARSER;
	@Nonnull
	private static final DateTimeFormatter ASCTIME_PARSER;

	static {
		DEFAULT_CHARSET = StandardCharsets.ISO_8859_1; // Per Servlet spec
		RFC_1123_PARSER = DateTimeFormatter.RFC_1123_DATE_TIME;
		// RFC 1036: spaces between day/month/year + 2-digit year reduced to 19xx baseline.
		RFC_1036_PARSER = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern("EEE, dd MMM ")
				.appendValueReduced(ChronoField.YEAR, 2, 2, 1900) // 94 -> 1994
				.appendPattern(" HH:mm:ss zzz")
				.toFormatter(US)
				.withZone(ZoneOffset.UTC);

		// asctime: "EEE MMM  d HH:mm:ss yyyy" — allow 1 or 2 spaces before day, no zone in text → default GMT.
		ASCTIME_PARSER = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern("EEE MMM")
				.appendLiteral(' ')
				.optionalStart().appendLiteral(' ').optionalEnd() // tolerate double space before single-digit day
				.appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
				.appendPattern(" HH:mm:ss yyyy")
				.toFormatter(US)
				.withZone(ZoneOffset.UTC);
	}

	@Nonnull
	private final Request request;
	@Nullable
	private final String host;
	@Nullable
	private final Integer port;
	@Nonnull
	private final ServletContext servletContext;
	@Nullable
	private HttpSession httpSession;
	@Nonnull
	private final Map<String, Object> attributes;
	@Nonnull
	private final List<Cookie> cookies;
	@Nullable
	private Charset charset;
	@Nullable
	private String contentType;
	@Nullable
	private Map<String, Set<String>> queryParameters;
	@Nullable
	private Map<String, Set<String>> formParameters;
	private boolean parametersAccessed;
	private boolean bodyParametersAccessed;
	@Nullable
	private SokletServletInputStream servletInputStream;
	@Nullable
	private BufferedReader reader;
	@Nonnull
	private RequestReadMethod requestReadMethod;

	@Nonnull
	public static Builder withRequest(@Nonnull Request request) {
		return new Builder(request);
	}

	private SokletHttpServletRequest(@Nonnull Builder builder) {
		requireNonNull(builder);
		requireNonNull(builder.request);

		this.request = builder.request;
		this.attributes = new HashMap<>();
		this.cookies = parseCookies(request);
		this.charset = parseCharacterEncoding(request).orElse(null);
		this.contentType = parseContentType(request).orElse(null);
		this.host = builder.host;
		this.port = builder.port;
		this.servletContext = builder.servletContext == null ? SokletServletContext.withDefaults() : builder.servletContext;
		this.httpSession = builder.httpSession;
		this.requestReadMethod = RequestReadMethod.UNSPECIFIED;
	}

	@Nonnull
	private Request getRequest() {
		return this.request;
	}

	@Nonnull
	private Map<String, Object> getAttributes() {
		return this.attributes;
	}

	@Nonnull
	private List<Cookie> parseCookies(@Nonnull Request request) {
		requireNonNull(request);

		List<Cookie> convertedCookies = new ArrayList<>();
		Map<String, Set<String>> headers = request.getHeaders();

		for (Entry<String, Set<String>> entry : headers.entrySet()) {
			String headerName = entry.getKey();

			if (headerName == null || !"cookie".equalsIgnoreCase(headerName.trim()))
				continue;

			Set<String> headerValues = entry.getValue();

			if (headerValues == null)
				continue;

			for (String headerValue : headerValues) {
				headerValue = Utilities.trimAggressivelyToNull(headerValue);

				if (headerValue == null)
					continue;

				for (String cookieComponent : splitCookieHeaderRespectingQuotes(headerValue)) {
					cookieComponent = Utilities.trimAggressivelyToNull(cookieComponent);

					if (cookieComponent == null)
						continue;

					String[] cookiePair = cookieComponent.split("=", 2);
					String rawName = Utilities.trimAggressivelyToNull(cookiePair[0]);
					String rawValue = (cookiePair.length == 2 ? Utilities.trimAggressivelyToNull(cookiePair[1]) : null);

					if (rawName == null)
						continue;

					String cookieValue = null;

					if (rawValue != null)
						cookieValue = unquoteCookieValueIfNeeded(rawValue);

					if (cookieValue != null)
						convertedCookies.add(new Cookie(rawName, cookieValue));
				}
			}
		}

		return convertedCookies;
	}

	/**
	 * Splits a Cookie header string into components on ';' but ONLY when not inside a quoted value.
	 * Supports backslash-escaped quotes within quoted strings.
	 */
	@Nonnull
	private static List<String> splitCookieHeaderRespectingQuotes(@Nonnull String headerValue) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder(headerValue.length());
		boolean inQuotes = false;
		boolean escape = false;

		for (int i = 0; i < headerValue.length(); i++) {
			char c = headerValue.charAt(i);

			if (escape) {
				current.append(c);
				escape = false;
				continue;
			}

			if (c == '\\') {
				escape = true;
				current.append(c);
				continue;
			}

			if (c == '"') {
				inQuotes = !inQuotes;
				current.append(c);
				continue;
			}

			if (c == ';' && !inQuotes) {
				parts.add(current.toString());
				current.setLength(0);
				continue;
			}

			current.append(c);
		}

		if (current.length() > 0)
			parts.add(current.toString());

		return parts;
	}

	/**
	 * If the cookie value is a quoted-string, remove surrounding quotes and unescape \" \\ and \; .
	 * Otherwise returns the input as-is.
	 */
	@Nonnull
	private static String unquoteCookieValueIfNeeded(@Nonnull String rawValue) {
		requireNonNull(rawValue);

		if (rawValue.length() >= 2 && rawValue.charAt(0) == '"' && rawValue.charAt(rawValue.length() - 1) == '"') {
			String inner = rawValue.substring(1, rawValue.length() - 1);
			StringBuilder sb = new StringBuilder(inner.length());
			boolean escape = false;

			for (int i = 0; i < inner.length(); i++) {
				char c = inner.charAt(i);

				if (escape) {
					sb.append(c);
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else {
					sb.append(c);
				}
			}

			if (escape)
				sb.append('\\');

			return sb.toString();
		}

		return rawValue;
	}

	@Nonnull
	private Optional<Charset> parseCharacterEncoding(@Nonnull Request request) {
		requireNonNull(request);
		return Utilities.extractCharsetFromHeaders(request.getHeaders());
	}

	@Nonnull
	private Optional<String> parseContentType(@Nonnull Request request) {
		requireNonNull(request);
		return Utilities.extractContentTypeFromHeaders(request.getHeaders());
	}

	@Nonnull
	private Optional<HttpSession> getHttpSession() {
		HttpSession current = this.httpSession;

		if (current instanceof SokletHttpSession && ((SokletHttpSession) current).isInvalidated()) {
			this.httpSession = null;
			return Optional.empty();
		}

		return Optional.ofNullable(current);
	}

	private void setHttpSession(@Nullable HttpSession httpSession) {
		this.httpSession = httpSession;
	}

	@Nonnull
	private Optional<Charset> getCharset() {
		return Optional.ofNullable(this.charset);
	}

	@Nullable
	private Charset getContextRequestCharset() {
		String encoding = getServletContext().getRequestCharacterEncoding();

		if (encoding == null || encoding.isBlank())
			return null;

		try {
			return Charset.forName(encoding);
		} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
			return null;
		}
	}

	@Nonnull
	private Charset getEffectiveCharset() {
		Charset explicit = this.charset;

		if (explicit != null)
			return explicit;

		Charset context = getContextRequestCharset();
		return context == null ? DEFAULT_CHARSET : context;
	}

	@Nullable
	private Long getContentLengthHeaderValue() {
		String value = getHeader("Content-Length");

		if (value == null)
			return null;

		value = value.trim();

		if (value.isEmpty())
			return null;

		try {
			long parsed = Long.parseLong(value, 10);
			return parsed < 0 ? null : parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void setCharset(@Nullable Charset charset) {
		this.charset = charset;
	}

	@Nonnull
	private Map<String, Set<String>> getQueryParameters() {
		if (this.queryParameters != null)
			return this.queryParameters;

		String rawQuery = getRequest().getRawQuery().orElse(null);

		if (rawQuery == null || rawQuery.isEmpty()) {
			this.queryParameters = Map.of();
			return this.queryParameters;
		}

		Charset charset = getEffectiveCharset();
		Map<String, Set<String>> parsed = Utilities.extractQueryParametersFromQuery(rawQuery, QueryFormat.X_WWW_FORM_URLENCODED, charset);
		this.queryParameters = Collections.unmodifiableMap(parsed);
		return this.queryParameters;
	}

	@Nonnull
	private Map<String, Set<String>> getFormParameters() {
		if (this.formParameters != null)
			return this.formParameters;

		if (getRequestReadMethod() != RequestReadMethod.UNSPECIFIED) {
			this.formParameters = Map.of();
			return this.formParameters;
		}

		if (this.contentType == null || !this.contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
			this.formParameters = Map.of();
			return this.formParameters;
		}

		markBodyParametersAccessed();

		byte[] body = getRequest().getBody().orElse(null);

		if (body == null || body.length == 0) {
			this.formParameters = Map.of();
			return this.formParameters;
		}

		String bodyAsString = new String(body, StandardCharsets.ISO_8859_1);
		Charset charset = getEffectiveCharset();
		Map<String, Set<String>> parsed = Utilities.extractQueryParametersFromQuery(bodyAsString, QueryFormat.X_WWW_FORM_URLENCODED, charset);
		this.formParameters = Collections.unmodifiableMap(parsed);
		return this.formParameters;
	}

	private void markParametersAccessed() {
		this.parametersAccessed = true;
	}

	private void markBodyParametersAccessed() {
		this.bodyParametersAccessed = true;
	}

	@Nonnull
	private Optional<String> getHost() {
		return Optional.ofNullable(this.host);
	}

	@Nonnull
	private Optional<Integer> getPort() {
		return Optional.ofNullable(this.port);
	}

	@Nonnull
	private Optional<String> getEffectiveOrigin() {
		return Utilities.extractEffectiveOrigin(
				Utilities.EffectiveOriginResolver.withRequest(
						getRequest(),
						Utilities.EffectiveOriginResolver.TrustPolicy.TRUST_ALL
				)
		);
	}

	@Nonnull
	private Optional<URI> getEffectiveOriginUri() {
		String effectiveOrigin = getEffectiveOrigin().orElse(null);

		if (effectiveOrigin == null)
			return Optional.empty();

		try {
			return Optional.of(URI.create(effectiveOrigin));
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}

	private int defaultPortForScheme(@Nullable String scheme) {
		if (scheme == null)
			return 0;

		if ("https".equalsIgnoreCase(scheme))
			return 443;

		if ("http".equalsIgnoreCase(scheme))
			return 80;

		return 0;
	}

	@Nullable
	private String hostFromAuthority(@Nullable String authority) {
		if (authority == null)
			return null;

		String normalized = authority.trim();

		if (normalized.isEmpty())
			return null;

		int at = normalized.lastIndexOf('@');

		if (at >= 0)
			normalized = normalized.substring(at + 1);

		if (normalized.startsWith("[")) {
			int close = normalized.indexOf(']');

			if (close > 0)
				return normalized.substring(1, close);

			return null;
		}

		int colon = normalized.indexOf(':');
		return colon > 0 ? normalized.substring(0, colon) : normalized;
	}

	@Nullable
	private Integer portFromAuthority(@Nullable String authority) {
		if (authority == null)
			return null;

		String normalized = authority.trim();

		if (normalized.isEmpty())
			return null;

		int at = normalized.lastIndexOf('@');

		if (at >= 0)
			normalized = normalized.substring(at + 1);

		if (normalized.startsWith("[")) {
			int close = normalized.indexOf(']');

			if (close > 0 && normalized.length() > close + 1 && normalized.charAt(close + 1) == ':') {
				String portString = normalized.substring(close + 2).trim();

				try {
					return Integer.parseInt(portString, 10);
				} catch (Exception ignored) {
					return null;
				}
			}

			return null;
		}

		int colon = normalized.indexOf(':');

		if (colon > 0 && normalized.indexOf(':', colon + 1) == -1) {
			String portString = normalized.substring(colon + 1).trim();

			try {
				return Integer.parseInt(portString, 10);
			} catch (Exception ignored) {
				return null;
			}
		}

		return null;
	}

	@Nonnull
	private Optional<SokletServletInputStream> getServletInputStream() {
		return Optional.ofNullable(this.servletInputStream);
	}

	private void setServletInputStream(@Nullable SokletServletInputStream servletInputStream) {
		this.servletInputStream = servletInputStream;
	}

	@Nonnull
	private Optional<BufferedReader> getBufferedReader() {
		return Optional.ofNullable(this.reader);
	}

	private void setBufferedReader(@Nullable BufferedReader reader) {
		this.reader = reader;
	}

	@Nonnull
	private RequestReadMethod getRequestReadMethod() {
		return this.requestReadMethod;
	}

	private void setRequestReadMethod(@Nonnull RequestReadMethod requestReadMethod) {
		requireNonNull(requestReadMethod);
		this.requestReadMethod = requestReadMethod;
	}

	private enum RequestReadMethod {
		UNSPECIFIED,
		INPUT_STREAM,
		READER
	}

	/**
	 * Builder used to construct instances of {@link SokletHttpServletRequest}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://www.revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@Nonnull
		private Request request;
		@Nullable
		private Integer port;
		@Nullable
		private String host;
		@Nullable
		private ServletContext servletContext;
		@Nullable
		private HttpSession httpSession;

		@Nonnull
		private Builder(@Nonnull Request request) {
			requireNonNull(request);
			this.request = request;
		}

		@Nonnull
		public Builder request(@Nonnull Request request) {
			requireNonNull(request);
			this.request = request;
			return this;
		}

		@Nonnull
		public Builder host(@Nullable String host) {
			this.host = host;
			return this;
		}

		@Nonnull
		public Builder port(@Nullable Integer port) {
			this.port = port;
			return this;
		}

		@Nonnull
		public Builder servletContext(@Nullable ServletContext servletContext) {
			this.servletContext = servletContext;
			return this;
		}

		@Nonnull
		public Builder httpSession(@Nullable HttpSession httpSession) {
			this.httpSession = httpSession;
			return this;
		}

		@Nonnull
		public SokletHttpServletRequest build() {
			return new SokletHttpServletRequest(this);
		}
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
	@Nullable
	public Cookie[] getCookies() {
		return this.cookies.isEmpty() ? null : this.cookies.toArray(new Cookie[0]);
	}

	@Override
	public long getDateHeader(@Nullable String name) {
		if (name == null)
			return -1;

		String value = getHeader(name);

		if (value == null)
			return -1;

		// Try HTTP-date formats (RFC 1123 → RFC 1036 → asctime)
		for (DateTimeFormatter fmt : List.of(RFC_1123_PARSER, RFC_1036_PARSER, ASCTIME_PARSER)) {
			try {
				return Instant.from(fmt.parse(value)).toEpochMilli();
			} catch (Exception ignored) {
				// try next
			}
		}

		// Fallback: epoch millis
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					String.format("Header with name '%s' and value '%s' cannot be converted to a date", name, value),
					e
			);
		}
	}

	@Override
	@Nullable
	public String getHeader(@Nullable String name) {
		if (name == null)
			return null;

		Set<String> values = getRequest().getHeaders().get(name);

		if (values == null || values.isEmpty())
			return null;

		return values.iterator().next();
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
		return Integer.valueOf(value, 10);
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
		return null;
	}

	@Override
	@Nonnull
	public String getContextPath() {
		return "";
	}

	@Override
	@Nullable
	public String getQueryString() {
		return getRequest().getRawQuery().orElse(null);
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
		return getRequest().getRawPath();
	}

	@Override
	@Nonnull
	public StringBuffer getRequestURL() {
		// Try forwarded/synthesized absolute prefix first
		String effectiveOrigin = getEffectiveOrigin().orElse(null);
		String rawPath = getRequest().getRawPath();

		if (effectiveOrigin != null)
			return new StringBuffer(format("%s%s", effectiveOrigin, rawPath));

		// Fall back to builder-provided host/port when available
		String scheme = getScheme(); // Soklet returns "http" by design
		String host = getServerName();
		int port = getServerPort();
		boolean defaultPort = port <= 0 || ("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80);
		String authorityHost = host;

		if (host != null && host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]"))
			authorityHost = "[" + host + "]";

		String authority = defaultPort ? authorityHost : format("%s:%d", authorityHost, port);
		return new StringBuffer(format("%s://%s%s", scheme, authority, rawPath));
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
		HttpSession currentHttpSession = getHttpSession().orElse(null);

		if (create && currentHttpSession == null) {
			currentHttpSession = SokletHttpSession.withServletContext(getServletContext());
			setHttpSession(currentHttpSession);
		}

		return currentHttpSession;
	}

	@Override
	@Nonnull
	public HttpSession getSession() {
		HttpSession currentHttpSession = getHttpSession().orElse(null);

		if (currentHttpSession == null) {
			currentHttpSession = SokletHttpSession.withServletContext(getServletContext());
			setHttpSession(currentHttpSession);
		}

		return currentHttpSession;
	}

	@Override
	@Nonnull
	public String changeSessionId() {
		HttpSession currentHttpSession = getHttpSession().orElse(null);

		if (currentHttpSession == null)
			throw new IllegalStateException("No session is present");

		if (!(currentHttpSession instanceof SokletHttpSession))
			throw new IllegalStateException(format("Cannot change session IDs. Session must be of type %s; instead it is of type %s",
					SokletHttpSession.class.getSimpleName(), currentHttpSession.getClass().getSimpleName()));

		UUID newSessionId = UUID.randomUUID();
		((SokletHttpSession) currentHttpSession).setSessionId(newSessionId);
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
		throw new ServletException("Authentication is not supported");
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
		throw new ServletException("HTTP upgrade is not supported");
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
	@Nullable
	public String getCharacterEncoding() {
		Charset explicit = getCharset().orElse(null);

		if (explicit != null)
			return explicit.name();

		Charset context = getContextRequestCharset();
		return context == null ? null : context.name();
	}

	@Override
	public void setCharacterEncoding(@Nullable String env) throws UnsupportedEncodingException {
		// Note that spec says: "This method must be called prior to reading request parameters or
		// reading input using getReader(). Otherwise, it has no effect."
		if (this.parametersAccessed || getRequestReadMethod() != RequestReadMethod.UNSPECIFIED)
			return;

		if (env == null) {
			setCharset(null);
		} else {
			try {
				setCharset(Charset.forName(env));
			} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
				throw new UnsupportedEncodingException(format("Not sure how to handle character encoding '%s'", env));
			}
		}

		this.queryParameters = null;
		this.formParameters = null;
	}

	@Override
	public int getContentLength() {
		Long length = getContentLengthHeaderValue();

		if (length == null || length > Integer.MAX_VALUE)
			return -1;

		return length.intValue();
	}

	@Override
	public long getContentLengthLong() {
		Long length = getContentLengthHeaderValue();
		return length == null ? -1 : length;
	}

	@Override
	@Nullable
	public String getContentType() {
		String headerValue = getHeader("Content-Type");
		return headerValue != null ? headerValue : this.contentType;
	}

	@Override
	@Nonnull
	public ServletInputStream getInputStream() throws IOException {
		RequestReadMethod currentReadMethod = getRequestReadMethod();

		if (currentReadMethod == RequestReadMethod.UNSPECIFIED) {
			setRequestReadMethod(RequestReadMethod.INPUT_STREAM);
			byte[] body = this.bodyParametersAccessed ? new byte[]{} : getRequest().getBody().orElse(new byte[]{});
			setServletInputStream(SokletServletInputStream.withInputStream(new ByteArrayInputStream(body)));
			return getServletInputStream().get();
		} else if (currentReadMethod == RequestReadMethod.INPUT_STREAM) {
			return getServletInputStream().get();
		} else {
			throw new IllegalStateException("getReader() has already been called for this request");
		}
	}

	@Override
	@Nullable
	public String getParameter(@Nullable String name) {
		if (name == null)
			return null;

		markParametersAccessed();

		Set<String> queryValues = getQueryParameters().get(name);

		if (queryValues != null && !queryValues.isEmpty())
			return queryValues.iterator().next();

		Set<String> formValues = getFormParameters().get(name);

		if (formValues != null && !formValues.isEmpty())
			return formValues.iterator().next();

		return null;
	}

	@Override
	@Nonnull
	public Enumeration<String> getParameterNames() {
		markParametersAccessed();

		Set<String> queryParameterNames = getQueryParameters().keySet();
		Set<String> formParameterNames = getFormParameters().keySet();

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

		markParametersAccessed();

		List<String> parameterValues = new ArrayList<>();

		Set<String> queryValues = getQueryParameters().get(name);

		if (queryValues != null)
			parameterValues.addAll(queryValues);

		Set<String> formValues = getFormParameters().get(name);

		if (formValues != null)
			parameterValues.addAll(formValues);

		return parameterValues.isEmpty() ? null : parameterValues.toArray(new String[0]);
	}

	@Override
	@Nonnull
	public Map<String, String[]> getParameterMap() {
		markParametersAccessed();

		Map<String, Set<String>> parameterMap = new HashMap<>();

		// Mutable copy of entries
		for (Entry<String, Set<String>> entry : getQueryParameters().entrySet())
			parameterMap.put(entry.getKey(), new HashSet<>(entry.getValue()));

		// Add form parameters to entries
		for (Entry<String, Set<String>> entry : getFormParameters().entrySet()) {
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
		URI effectiveOriginUri = getEffectiveOriginUri().orElse(null);

		if (effectiveOriginUri != null && effectiveOriginUri.getScheme() != null)
			return effectiveOriginUri.getScheme().trim().toLowerCase(ROOT);

		// Honor common reverse-proxy header; fall back to http
		String proto = getRequest().getHeader("X-Forwarded-Proto").orElse(null);

		if (proto != null) {
			proto = proto.trim().toLowerCase(ROOT);
			if (proto.equals("https") || proto.equals("http"))
				return proto;
		}

		return "http";
	}

	@Override
	@Nonnull
	public String getServerName() {
		URI effectiveOriginUri = getEffectiveOriginUri().orElse(null);

		if (effectiveOriginUri != null) {
			String host = effectiveOriginUri.getHost();

			if (host == null)
				host = hostFromAuthority(effectiveOriginUri.getAuthority());

			if (host != null) {
				if (host.startsWith("[") && host.endsWith("]") && host.length() > 2)
					host = host.substring(1, host.length() - 1);

				return host;
			}
		}

		String hostHeader = getRequest().getHeader("Host").orElse(null);

		if (hostHeader != null) {
			String host = hostFromAuthority(hostHeader);

			if (host != null && !host.isBlank())
				return host;
		}

		return getLocalName();
	}

	@Override
	public int getServerPort() {
		URI effectiveOriginUri = getEffectiveOriginUri().orElse(null);

		if (effectiveOriginUri != null) {
			int port = effectiveOriginUri.getPort();
			if (port >= 0)
				return port;

			Integer authorityPort = portFromAuthority(effectiveOriginUri.getAuthority());

			if (authorityPort != null)
				return authorityPort;

			return defaultPortForScheme(effectiveOriginUri.getScheme());
		}

		String hostHeader = getRequest().getHeader("Host").orElse(null);

		if (hostHeader != null) {
			Integer hostPort = portFromAuthority(hostHeader);

			if (hostPort != null)
				return hostPort;

			int defaultPort = defaultPortForScheme(getScheme());

			if (defaultPort > 0)
				return defaultPort;
		}

		Integer port = getPort().orElse(null);

		if (port != null)
			return port;

		int defaultPort = defaultPortForScheme(getScheme());
		return defaultPort > 0 ? defaultPort : 0;
	}

	@Override
	@Nonnull
	public BufferedReader getReader() throws IOException {
		RequestReadMethod currentReadMethod = getRequestReadMethod();

		if (currentReadMethod == RequestReadMethod.UNSPECIFIED) {
			setRequestReadMethod(RequestReadMethod.READER);
			Charset charset = getEffectiveCharset();
			byte[] body = this.bodyParametersAccessed ? new byte[]{} : getRequest().getBody().orElse(new byte[0]);
			InputStream inputStream = new ByteArrayInputStream(body);
			setBufferedReader(new BufferedReader(new InputStreamReader(inputStream, charset)));
			return getBufferedReader().get();
		} else if (currentReadMethod == RequestReadMethod.READER) {
			return getBufferedReader().get();
		} else {
			throw new IllegalStateException("getInputStream() has already been called for this request");
		}
	}

	@Override
	@Nullable
	public String getRemoteAddr() {
		String xForwardedForHeader = getRequest().getHeader("X-Forwarded-For").orElse(null);

		if (xForwardedForHeader != null) {
			// Example value: 203.0.113.195,2001:db8:85a3:8d3:1319:8a2e:370:7348,198.51.100.178
			String[] components = xForwardedForHeader.split(",");

			for (String component : components) {
				if (component == null)
					continue;

				String value = component.trim();

				if (!value.isEmpty())
					return value;
			}
		}

		InetSocketAddress remoteAddress = getRequest().getRemoteAddress().orElse(null);

		if (remoteAddress != null) {
			InetAddress address = remoteAddress.getAddress();
			String host = address != null ? address.getHostAddress() : remoteAddress.getHostString();

			if (host != null && !host.isBlank())
				return host;
		}

		return null;
	}

	@Override
	@Nullable
	public String getRemoteHost() {
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
		return locales.size() == 0 ? getDefault() : locales.get(0);
	}

	@Override
	@Nonnull
	public Enumeration<Locale> getLocales() {
		List<Locale> locales = getRequest().getLocales();
		return Collections.enumeration(locales.size() == 0 ? List.of(getDefault()) : locales);
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
		InetSocketAddress remoteAddress = getRequest().getRemoteAddress().orElse(null);
		return remoteAddress == null ? 0 : remoteAddress.getPort();
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
		Integer port = getPort().orElse(null);
		return port == null ? 0 : port;
	}

	@Override
	@Nonnull
	public ServletContext getServletContext() {
		return this.servletContext;
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
		requireNonNull(servletRequest);
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
