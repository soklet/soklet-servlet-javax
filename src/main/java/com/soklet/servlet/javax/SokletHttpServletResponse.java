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

import com.soklet.MarshaledResponse;
import com.soklet.Request;
import com.soklet.Response;
import com.soklet.ResponseCookie;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link HttpServletResponse}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class SokletHttpServletResponse implements HttpServletResponse {
	@Nonnull
	private static final Integer DEFAULT_RESPONSE_BUFFER_SIZE_IN_BYTES;
	@Nonnull
	private static final Charset DEFAULT_CHARSET;
	@Nonnull
	private static final DateTimeFormatter DATE_TIME_FORMATTER;

	static {
		DEFAULT_RESPONSE_BUFFER_SIZE_IN_BYTES = 1_024;
		DEFAULT_CHARSET = StandardCharsets.ISO_8859_1; // Per Servlet spec
		DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
				.withLocale(Locale.US)
				.withZone(ZoneId.of("GMT"));
	}

	@Nonnull
	private final String rawPath; // Raw path (no query), e.g. "/test/abc". Always starts with "/"
	@Nullable
	private final Request request;
	@Nonnull
	private final ServletContext servletContext;
	@Nonnull
	private final List<Cookie> cookies;
	@Nonnull
	private final Map<String, List<String>> headers;
	@Nonnull
	private ByteArrayOutputStream responseOutputStream;
	@Nonnull
	private ResponseWriteMethod responseWriteMethod;
	@Nonnull
	private Integer statusCode;
	@Nonnull
	private Boolean responseCommitted;
	@Nonnull
	private Boolean responseFinalized;
	@Nullable
	private Locale locale;
	@Nullable
	private String errorMessage;
	@Nullable
	private String redirectUrl;
	@Nullable
	private Charset charset;
	@Nullable
	private String contentType;
	@Nonnull
	private Integer responseBufferSizeInBytes;
	@Nullable
	private SokletServletOutputStream servletOutputStream;
	@Nullable
	private SokletServletPrintWriter printWriter;

	@Nonnull
	public static SokletHttpServletResponse withRequest(@Nonnull Request request) {
		requireNonNull(request);
		return new SokletHttpServletResponse(request, request.getRawPath(), null);
	}

	@Nonnull
	public static SokletHttpServletResponse withRequest(@Nonnull Request request,
																											@Nullable ServletContext servletContext) {
		requireNonNull(request);
		return new SokletHttpServletResponse(request, request.getRawPath(), servletContext);
	}

	/**
	 * Creates a response bound to Soklet's raw path construct.
	 * <p>
	 * This is the exact path component sent by the client, without URL decoding and without a query string
	 * (for example, {@code "/a%20b/c"}). It corresponds to {@link Request#getRawPath()}.
	 *
	 * @param rawPath raw path component of the request (no query string)
	 * @return a response bound to the raw request path
	 */
	@Nonnull
	public static SokletHttpServletResponse withRawPath(@Nonnull String rawPath) {
		requireNonNull(rawPath);
		return new SokletHttpServletResponse(null, rawPath, null);
	}

	@Nonnull
	public static SokletHttpServletResponse withRawPath(@Nonnull String rawPath,
																											@Nullable ServletContext servletContext) {
		requireNonNull(rawPath);
		return new SokletHttpServletResponse(null, rawPath, servletContext);
	}

	private SokletHttpServletResponse(@Nullable Request request,
																		@Nonnull String rawPath,
																		@Nullable ServletContext servletContext) {
		requireNonNull(rawPath);

		this.request = request;
		this.rawPath = rawPath;
		this.servletContext = servletContext == null ? SokletServletContext.withDefaults() : servletContext;
		this.statusCode = HttpServletResponse.SC_OK;
		this.responseWriteMethod = ResponseWriteMethod.UNSPECIFIED;
		this.responseBufferSizeInBytes = DEFAULT_RESPONSE_BUFFER_SIZE_IN_BYTES;
		this.responseOutputStream = new ByteArrayOutputStream(DEFAULT_RESPONSE_BUFFER_SIZE_IN_BYTES);
		this.cookies = new ArrayList<>();
		this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		this.responseCommitted = false;
		this.responseFinalized = false;
	}

	@Nonnull
	public Response toResponse() {
		// In the servlet world, there is really no difference between Response and MarshaledResponse
		MarshaledResponse marshaledResponse = toMarshaledResponse();

		return Response.withStatusCode(marshaledResponse.getStatusCode())
				.body(marshaledResponse.getBody().orElse(null))
				.headers(marshaledResponse.getHeaders())
				.cookies(marshaledResponse.getCookies())
				.build();
	}

	@Nonnull
	public MarshaledResponse toMarshaledResponse() {
		byte[] body = getResponseOutputStream().toByteArray();

		Map<String, Set<String>> headers = getHeaders().entrySet().stream()
				.collect(Collectors.toMap(entry -> entry.getKey(), entry -> new HashSet<>(entry.getValue())));

		Set<ResponseCookie> cookies = getCookies().stream()
				.map(cookie -> {
					ResponseCookie.Builder builder = ResponseCookie.with(cookie.getName(), cookie.getValue())
							.path(cookie.getPath())
							.secure(cookie.getSecure())
							.httpOnly(cookie.isHttpOnly())
							.domain(cookie.getDomain());

					if (cookie.getMaxAge() >= 0)
						builder.maxAge(Duration.ofSeconds(cookie.getMaxAge()));

					return builder.build();
				})
				.collect(Collectors.toSet());

		return MarshaledResponse.withStatusCode(getStatus())
				.body(body)
				.headers(headers)
				.cookies(cookies)
				.build();
	}

	@Nonnull
	private String getRawPath() {
		return this.rawPath;
	}

	@Nonnull
	private Optional<Request> getRequest() {
		return Optional.ofNullable(this.request);
	}

	@Nonnull
	private ServletContext getServletContext() {
		return this.servletContext;
	}

	@Nonnull
	private List<Cookie> getCookies() {
		return this.cookies;
	}

	@Nonnull
	private Map<String, List<String>> getHeaders() {
		return this.headers;
	}

	private void putHeaderValue(@Nonnull String name,
															@Nonnull String value,
															boolean replace) {
		requireNonNull(name);
		requireNonNull(value);

		if (replace) {
			List<String> values = new ArrayList<>();
			values.add(value);
			getHeaders().put(name, values);
		} else {
			getHeaders().computeIfAbsent(name, k -> new ArrayList<>()).add(value);
		}
	}

	@Nonnull
	private Integer getStatusCode() {
		return this.statusCode;
	}

	private void setStatusCode(@Nonnull Integer statusCode) {
		requireNonNull(statusCode);
		this.statusCode = statusCode;
	}

	@Nonnull
	private Optional<String> getErrorMessage() {
		return Optional.ofNullable(this.errorMessage);
	}

	private void setErrorMessage(@Nullable String errorMessage) {
		this.errorMessage = errorMessage;
	}

	@Nonnull
	private Optional<String> getRedirectUrl() {
		return Optional.ofNullable(this.redirectUrl);
	}

	private void setRedirectUrl(@Nullable String redirectUrl) {
		this.redirectUrl = redirectUrl;
	}

	@Nonnull
	private Optional<Charset> getCharset() {
		return Optional.ofNullable(this.charset);
	}

	@Nullable
	private Charset getContextResponseCharset() {
		String encoding = getServletContext().getResponseCharacterEncoding();

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

		Charset context = getContextResponseCharset();
		return context == null ? DEFAULT_CHARSET : context;
	}

	private void setCharset(@Nullable Charset charset) {
		this.charset = charset;
	}

	@Nonnull
	private Boolean getResponseCommitted() {
		return this.responseCommitted;
	}

	private void setResponseCommitted(@Nonnull Boolean responseCommitted) {
		requireNonNull(responseCommitted);
		this.responseCommitted = responseCommitted;
	}

	@Nonnull
	private Boolean getResponseFinalized() {
		return this.responseFinalized;
	}

	private void setResponseFinalized(@Nonnull Boolean responseFinalized) {
		requireNonNull(responseFinalized);
		this.responseFinalized = responseFinalized;
	}

	private void ensureResponseIsUncommitted() {
		if (getResponseCommitted())
			throw new IllegalStateException("Response has already been committed.");
	}

	@Nonnull
	private String dateHeaderRepresentation(@Nonnull Long millisSinceEpoch) {
		requireNonNull(millisSinceEpoch);
		return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(millisSinceEpoch));
	}

	@Nonnull
	private Optional<SokletServletOutputStream> getServletOutputStream() {
		return Optional.ofNullable(this.servletOutputStream);
	}

	private void setServletOutputStream(@Nullable SokletServletOutputStream servletOutputStream) {
		this.servletOutputStream = servletOutputStream;
	}

	@Nonnull
	private Optional<SokletServletPrintWriter> getPrintWriter() {
		return Optional.ofNullable(this.printWriter);
	}

	public void setPrintWriter(@Nullable SokletServletPrintWriter printWriter) {
		this.printWriter = printWriter;
	}

	@Nonnull
	private ByteArrayOutputStream getResponseOutputStream() {
		return this.responseOutputStream;
	}

	private void setResponseOutputStream(@Nonnull ByteArrayOutputStream responseOutputStream) {
		requireNonNull(responseOutputStream);
		this.responseOutputStream = responseOutputStream;
	}

	@Nonnull
	private Integer getResponseBufferSizeInBytes() {
		return this.responseBufferSizeInBytes;
	}

	private void setResponseBufferSizeInBytes(@Nonnull Integer responseBufferSizeInBytes) {
		requireNonNull(responseBufferSizeInBytes);
		this.responseBufferSizeInBytes = responseBufferSizeInBytes;
	}

	@Nonnull
	private ResponseWriteMethod getResponseWriteMethod() {
		return this.responseWriteMethod;
	}

	private void setResponseWriteMethod(@Nonnull ResponseWriteMethod responseWriteMethod) {
		requireNonNull(responseWriteMethod);
		this.responseWriteMethod = responseWriteMethod;
	}

	private enum ResponseWriteMethod {
		UNSPECIFIED,
		SERVLET_OUTPUT_STREAM,
		PRINT_WRITER
	}

	// Implementation of HttpServletResponse methods below:

	@Override
	public void addCookie(@Nullable Cookie cookie) {
		if (isCommitted())
			return;

		if (cookie != null)
			getCookies().add(cookie);
	}

	@Override
	public boolean containsHeader(@Nullable String name) {
		if (name == null)
			return false;

		return getHeaders().containsKey(name);
	}

	@Override
	@Nullable
	public String encodeURL(@Nullable String url) {
		return url;
	}

	@Override
	@Nullable
	public String encodeRedirectURL(@Nullable String url) {
		return url;
	}

	@Override
	@Deprecated
	public String encodeUrl(@Nullable String url) {
		return url;
	}

	@Override
	@Deprecated
	public String encodeRedirectUrl(@Nullable String url) {
		return url;
	}

	@Override
	public void sendError(int sc,
												@Nullable String msg) throws IOException {
		ensureResponseIsUncommitted();
		resetBuffer();
		setStatus(sc);
		setErrorMessage(msg);
		setResponseCommitted(true);
	}

	@Override
	public void sendError(int sc) throws IOException {
		ensureResponseIsUncommitted();
		resetBuffer();
		setStatus(sc);
		setErrorMessage(null);
		setResponseCommitted(true);
	}

	private boolean isAbsoluteUri(@Nonnull String location) {
		requireNonNull(location);

		try {
			return java.net.URI.create(location).isAbsolute();
		} catch (Exception ignored) {
			return false;
		}
	}

	@Nonnull
	private String getRedirectBaseUrl() {
		Request req = getRequest().orElse(null);

		if (req == null)
			return "http://localhost";

		SokletHttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.servletContext(getServletContext())
				.build();
		String scheme = http.getScheme();
		String host = http.getServerName();
		int port = http.getServerPort();
		boolean defaultPort = port <= 0 || ("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80);
		String authorityHost = host;

		if (host != null && host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]"))
			authorityHost = "[" + host + "]";

		String authority = defaultPort ? authorityHost : format("%s:%d", authorityHost, port);
		return format("%s://%s", scheme, authority);
	}

	private static final class ParsedLocation {
		@Nullable
		private final String scheme;
		@Nullable
		private final String rawAuthority;
		@Nonnull
		private final String rawPath;
		@Nullable
		private final String rawQuery;
		@Nullable
		private final String rawFragment;
		private final boolean opaque;

		private ParsedLocation(@Nullable String scheme,
													 @Nullable String rawAuthority,
													 @Nonnull String rawPath,
													 @Nullable String rawQuery,
													 @Nullable String rawFragment,
													 boolean opaque) {
			this.scheme = scheme;
			this.rawAuthority = rawAuthority;
			this.rawPath = rawPath;
			this.rawQuery = rawQuery;
			this.rawFragment = rawFragment;
			this.opaque = opaque;
		}
	}

	@Nonnull
	private ParsedLocation parseLocation(@Nonnull String location) {
		requireNonNull(location);

		try {
			URI uri = URI.create(location);
			String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
			return new ParsedLocation(uri.getScheme(), uri.getRawAuthority(), rawPath, uri.getRawQuery(), uri.getRawFragment(), uri.isOpaque());
		} catch (Exception ignored) {
			String rawPath = location;
			String rawQuery = null;
			String rawFragment = null;

			int hash = rawPath.indexOf('#');
			if (hash >= 0) {
				rawFragment = rawPath.substring(hash + 1);
				rawPath = rawPath.substring(0, hash);
			}

			int question = rawPath.indexOf('?');
			if (question >= 0) {
				rawQuery = rawPath.substring(question + 1);
				rawPath = rawPath.substring(0, question);
			}

			return new ParsedLocation(null, null, rawPath, rawQuery, rawFragment, false);
		}
	}

	@Nonnull
	private String buildSuffix(@Nullable String rawQuery,
														 @Nullable String rawFragment) {
		StringBuilder suffix = new StringBuilder();

		if (rawQuery != null)
			suffix.append('?').append(rawQuery);

		if (rawFragment != null)
			suffix.append('#').append(rawFragment);

		return suffix.toString();
	}

	@Nonnull
	private String normalizePath(@Nonnull String path) {
		requireNonNull(path);

		if (path.isEmpty())
			return path;

		String input = path;
		StringBuilder output = new StringBuilder();

		while (!input.isEmpty()) {
			if (input.startsWith("../")) {
				input = input.substring(3);
			} else if (input.startsWith("./")) {
				input = input.substring(2);
			} else if (input.startsWith("/./")) {
				input = input.substring(2);
			} else if (input.equals("/.")) {
				input = "/";
			} else if (input.startsWith("/../")) {
				input = input.substring(3);
				removeLastSegment(output);
			} else if (input.equals("/..")) {
				input = "/";
				removeLastSegment(output);
			} else if (input.equals(".") || input.equals("..")) {
				input = "";
			} else {
				int start = input.startsWith("/") ? 1 : 0;
				int nextSlash = input.indexOf('/', start);

				if (nextSlash == -1) {
					output.append(input);
					input = "";
				} else {
					output.append(input, 0, nextSlash);
					input = input.substring(nextSlash);
				}
			}
		}

		return output.toString();
	}

	private void removeLastSegment(@Nonnull StringBuilder output) {
		requireNonNull(output);

		int length = output.length();

		if (length == 0)
			return;

		int end = length;

		if (end > 0 && output.charAt(end - 1) == '/')
			end--;

		if (end <= 0) {
			output.setLength(0);
			return;
		}

		int lastSlash = output.lastIndexOf("/", end - 1);

		if (lastSlash >= 0)
			output.delete(lastSlash, output.length());
		else
			output.setLength(0);
	}

	@Override
	public void sendRedirect(@Nullable String location) throws IOException {
		ensureResponseIsUncommitted();

		if (location == null)
			throw new IllegalArgumentException("Redirect location must not be null");

		setStatus(HttpServletResponse.SC_FOUND);
		resetBuffer();

		// This method can accept relative URLs; the servlet container must convert the relative URL to an absolute URL
		// before sending the response to the client. If the location is relative without a leading '/' the container
		// interprets it as relative to the current request URI. If the location is relative with a leading '/'
		// the container interprets it as relative to the servlet container root. If the location is relative with two
		// leading '/' the container interprets it as a network-path reference (see RFC 3986: Uniform Resource
		// Identifier (URI): Generic Syntax, section 4.2 "Relative Reference").
		String baseUrl = getRedirectBaseUrl();
		int schemeIndex = baseUrl.indexOf("://");
		String scheme = schemeIndex > 0 ? baseUrl.substring(0, schemeIndex) : "http";
		String finalLocation;
		ParsedLocation parsed = parseLocation(location);
		String suffix = buildSuffix(parsed.rawQuery, parsed.rawFragment);

		if (parsed.opaque) {
			finalLocation = location;
		} else if (location.startsWith("//")) {
			// Network-path reference: keep host from location but inherit scheme
			if (parsed.rawAuthority == null) {
				finalLocation = scheme + ":" + location;
			} else {
				String normalized = normalizePath(parsed.rawPath);
				finalLocation = scheme + "://" + parsed.rawAuthority + normalized + suffix;
			}
		} else if (isAbsoluteUri(location)) {
			// URL is already absolute
			if (parsed.scheme == null || parsed.rawAuthority == null) {
				finalLocation = location;
			} else {
				String normalized = normalizePath(parsed.rawPath);
				finalLocation = parsed.scheme + "://" + parsed.rawAuthority + normalized + suffix;
			}
		} else if (location.startsWith("/")) {
			// URL is relative with leading /
			String normalized = normalizePath(parsed.rawPath);
			finalLocation = baseUrl + normalized + suffix;
		} else {
			// URL is relative but does not have leading '/', resolve against the parent of the current path
			String base = getRawPath();
			String path = parsed.rawPath;
			String query = parsed.rawQuery;

			if (path.isEmpty() && query == null)
				query = getRequest().flatMap(Request::getRawQuery).orElse(null);

			String relativeSuffix = buildSuffix(query, parsed.rawFragment);

			if (path.isEmpty()) {
				String normalized = normalizePath(base);
				finalLocation = baseUrl + normalized + relativeSuffix;
			} else {
				int idx = base.lastIndexOf('/');
				String parent = (idx <= 0) ? "/" : base.substring(0, idx);
				String resolvedPath = parent.endsWith("/") ? parent + path : parent + "/" + path;
				String normalized = normalizePath(resolvedPath);
				finalLocation = baseUrl + normalized + relativeSuffix;
			}
		}

		setRedirectUrl(finalLocation);
		setHeader("Location", finalLocation);

		flushBuffer();
		setResponseCommitted(true);
	}

	@Override
	public void setDateHeader(@Nullable String name,
														long date) {
		if (isCommitted())
			return;

		setHeader(name, dateHeaderRepresentation(date));
	}

	@Override
	public void addDateHeader(@Nullable String name,
														long date) {
		if (isCommitted())
			return;

		addHeader(name, dateHeaderRepresentation(date));
	}

	@Override
	public void setHeader(@Nullable String name,
												@Nullable String value) {
		if (isCommitted())
			return;

		if (name != null && !name.isBlank() && value != null) {
			if ("Content-Type".equalsIgnoreCase(name)) {
				setContentType(value);
				return;
			}

			putHeaderValue(name, value, true);
		}
	}

	@Override
	public void addHeader(@Nullable String name,
												@Nullable String value) {
		if (isCommitted())
			return;

		if (name != null && !name.isBlank() && value != null) {
			if ("Content-Type".equalsIgnoreCase(name)) {
				setContentType(value);
				return;
			}

			putHeaderValue(name, value, false);
		}
	}

	@Override
	public void setIntHeader(@Nullable String name,
													 int value) {
		setHeader(name, String.valueOf(value));
	}

	@Override
	public void addIntHeader(@Nullable String name,
													 int value) {
		addHeader(name, String.valueOf(value));
	}

	@Override
	public void setStatus(int sc) {
		if (isCommitted())
			return;

		this.statusCode = sc;
	}

	@Override
	@Deprecated
	public void setStatus(int sc,
												@Nullable String sm) {
		if (isCommitted())
			return;

		this.statusCode = sc;
		this.errorMessage = sm;
	}

	@Override
	public int getStatus() {
		return getStatusCode();
	}

	@Override
	@Nullable
	public String getHeader(@Nullable String name) {
		if (name == null)
			return null;

		List<String> values = getHeaders().get(name);
		return values == null || values.size() == 0 ? null : values.get(0);
	}

	@Override
	@Nonnull
	public Collection<String> getHeaders(@Nullable String name) {
		if (name == null)
			return List.of();

		List<String> values = getHeaders().get(name);
		return values == null ? List.of() : Collections.unmodifiableList(values);
	}

	@Override
	@Nonnull
	public Collection<String> getHeaderNames() {
		return Collections.unmodifiableSet(getHeaders().keySet());
	}

	@Override
	@Nonnull
	public String getCharacterEncoding() {
		return getEffectiveCharset().name();
	}

	@Override
	@Nullable
	public String getContentType() {
		String headerValue = getHeader("Content-Type");
		return headerValue != null ? headerValue : this.contentType;
	}

	@Override
	@Nonnull
	public ServletOutputStream getOutputStream() throws IOException {
		// Returns a ServletOutputStream suitable for writing binary data in the response.
		// The servlet container does not encode the binary data.
		// Calling flush() on the ServletOutputStream commits the response.
		// Either this method or getWriter() may be called to write the body, not both, except when reset() has been called.
		ResponseWriteMethod currentResponseWriteMethod = getResponseWriteMethod();

		if (currentResponseWriteMethod == ResponseWriteMethod.UNSPECIFIED) {
			setResponseWriteMethod(ResponseWriteMethod.SERVLET_OUTPUT_STREAM);
			this.servletOutputStream = SokletServletOutputStream.withOutputStream(getResponseOutputStream())
					.onWriteOccurred((ignored1, ignored2) -> {
						// Flip to "committed" if any write occurs
						setResponseCommitted(true);
					}).onWriteFinalized((ignored) -> {
						setResponseCommitted(true);
						setResponseFinalized(true);
					}).build();
			return getServletOutputStream().get();
		} else if (currentResponseWriteMethod == ResponseWriteMethod.SERVLET_OUTPUT_STREAM) {
			return getServletOutputStream().get();
		} else {
			throw new IllegalStateException(format("Cannot use %s for writing response; already using %s",
					ServletOutputStream.class.getSimpleName(), PrintWriter.class.getSimpleName()));
		}
	}

	@Nonnull
	private Boolean writerObtained() {
		return getResponseWriteMethod() == ResponseWriteMethod.PRINT_WRITER;
	}

	@Nonnull
	private Optional<String> extractCharsetFromContentType(@Nullable String type) {
		if (type == null)
			return Optional.empty();

		String[] parts = type.split(";");

		for (int i = 1; i < parts.length; i++) {
			String p = parts[i].trim();
			if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
				String cs = p.substring("charset=".length()).trim();

				if (cs.startsWith("\"") && cs.endsWith("\"") && cs.length() >= 2)
					cs = cs.substring(1, cs.length() - 1);

				return Optional.of(cs);
			}
		}

		return Optional.empty();
	}

	// Helper: remove any charset=... from Content-Type (preserve other params)
	@Nonnull
	private Optional<String> stripCharsetParam(@Nullable String type) {
		if (type == null)
			return Optional.empty();

		String[] parts = type.split(";");
		String base = parts[0].trim();
		List<String> kept = new ArrayList<>();

		for (int i = 1; i < parts.length; i++) {
			String p = parts[i].trim();

			if (!p.toLowerCase(Locale.ROOT).startsWith("charset=") && !p.isEmpty())
				kept.add(p);
		}

		return Optional.ofNullable(kept.isEmpty() ? base : base + "; " + String.join("; ", kept));
	}

	// Helper: ensure Content-Type includes the given charset (replacing any existing one)
	@Nonnull
	private Optional<String> withCharset(@Nullable String type,
																				 @Nonnull String charsetName) {
		requireNonNull(charsetName);

		if (type == null)
			return Optional.empty();

		String baseNoCs = stripCharsetParam(type).orElse("text/plain");
		return Optional.of(baseNoCs + "; charset=" + charsetName);
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		// Returns a PrintWriter object that can send character text to the client.
		// The PrintWriter uses the character encoding returned by getCharacterEncoding().
		// If the response's character encoding has not been specified as described in getCharacterEncoding
		// (i.e., the method just returns the default value), getWriter updates it to the effective default.
		// Calling flush() on the PrintWriter commits the response.
		//
		// Either this method or getOutputStream() may be called to write the body, not both, except when reset() has been called.
		// Returns a PrintWriter that uses the character encoding returned by getCharacterEncoding().
		// If not specified yet, calling getWriter() fixes the encoding to the effective default.
		ResponseWriteMethod currentResponseWriteMethod = getResponseWriteMethod();

		if (currentResponseWriteMethod == ResponseWriteMethod.UNSPECIFIED) {
			// Freeze encoding now
			Charset enc = getEffectiveCharset();
			setCharset(enc); // record the chosen encoding explicitly

			// If a content type is already present and lacks charset, append the frozen charset to header
			String currentContentType = getContentType();

			if (currentContentType != null) {
				Optional<String> csInHeader = extractCharsetFromContentType(currentContentType);
				if (csInHeader.isEmpty() || !csInHeader.get().equalsIgnoreCase(enc.name())) {
					String updated = withCharset(currentContentType, enc.name()).orElse(null);

					if (updated != null) {
						this.contentType = updated;
						putHeaderValue("Content-Type", updated, true);
					} else {
						this.contentType = currentContentType;
						putHeaderValue("Content-Type", currentContentType, true);
					}
				}
			}

			setResponseWriteMethod(ResponseWriteMethod.PRINT_WRITER);

			this.printWriter =
					SokletServletPrintWriter.withWriter(
									new OutputStreamWriter(getResponseOutputStream(), enc))
							.onWriteOccurred((ignored1, ignored2) -> setResponseCommitted(true))   // commit on first write
							.onWriteFinalized((ignored) -> {
								setResponseCommitted(true);
								setResponseFinalized(true);
							})
							.build();

			return getPrintWriter().get();
		} else if (currentResponseWriteMethod == ResponseWriteMethod.PRINT_WRITER) {
			return getPrintWriter().get();
		} else {
			throw new IllegalStateException(format("Cannot use %s for writing response; already using %s",
					PrintWriter.class.getSimpleName(), ServletOutputStream.class.getSimpleName()));
		}
	}

	@Override
	public void setCharacterEncoding(@Nullable String charset) {
		if (isCommitted())
			return;

		// Spec: no effect after getWriter() or after commit
		if (writerObtained())
			return;

		if (charset == null || charset.isBlank()) {
			// Clear explicit charset; default will be chosen at writer time if needed
			setCharset(null);

			// If a Content-Type is set, remove its charset=... parameter
			String currentContentType = getContentType();

			if (currentContentType != null) {
				String updated = stripCharsetParam(currentContentType).orElse(null);
				this.contentType = updated;
				if (updated == null || updated.isBlank()) {
					getHeaders().remove("Content-Type");
				} else {
					putHeaderValue("Content-Type", updated, true);
				}
			}

			return;
		}

		Charset cs = Charset.forName(charset);
		setCharset(cs);

		// If a Content-Type is set, reflect/replace the charset=... in the header
		String currentContentType = getContentType();

		if (currentContentType != null) {
			String updated = withCharset(currentContentType, cs.name()).orElse(null);

			if (updated != null) {
				this.contentType = updated;
				putHeaderValue("Content-Type", updated, true);
			} else {
				this.contentType = currentContentType;
				putHeaderValue("Content-Type", currentContentType, true);
			}
		}
	}

	@Override
	public void setContentLength(int len) {
		if (isCommitted())
			return;

		setHeader("Content-Length", String.valueOf(len));
	}

	@Override
	public void setContentLengthLong(long len) {
		if (isCommitted())
			return;

		setHeader("Content-Length", String.valueOf(len));
	}

	@Override
	public void setContentType(@Nullable String type) {
		// This method may be called repeatedly to change content type and character encoding.
		// This method has no effect if called after the response has been committed.
		// It does not set the response's character encoding if it is called after getWriter has been called
		// or after the response has been committed.
		if (isCommitted())
			return;

		if (!writerObtained()) {
			// Before writer: charset can still be established/overridden
			this.contentType = type;

			if (type == null || type.isBlank()) {
				getHeaders().remove("Content-Type");
				return;
			}

			// If caller specified charset=..., adopt it as the current explicit charset
			Optional<String> cs = extractCharsetFromContentType(type);
			if (cs.isPresent()) {
				setCharset(Charset.forName(cs.get()));
				putHeaderValue("Content-Type", type, true);
			} else {
				// No charset in type. If an explicit charset already exists (via setCharacterEncoding),
				// reflect it in the header; otherwise just set the type as-is.
				if (getCharset().isPresent()) {
					String updated = withCharset(type, getCharset().get().name()).orElse(null);

					if (updated != null) {
						this.contentType = updated;
						putHeaderValue("Content-Type", updated, true);
					} else {
						putHeaderValue("Content-Type", type, true);
					}
				} else {
					putHeaderValue("Content-Type", type, true);
				}
			}
		} else {
			// After writer: charset is frozen. We can change the MIME type, but we must NOT change encoding.
			// If caller supplies a charset, normalize the header back to the locked encoding.
			this.contentType = type;

			if (type == null || type.isBlank()) {
				// Allowed: clear header; does not change actual encoding used by writer
				getHeaders().remove("Content-Type");
				return;
			}

			String locked = getCharacterEncoding(); // the frozen encoding name
			String normalized = withCharset(type, locked).orElse(null);

			if (normalized != null) {
				this.contentType = normalized;
				putHeaderValue("Content-Type", normalized, true);
			} else {
				this.contentType = type;
				putHeaderValue("Content-Type", type, true);
			}
		}
	}

	@Override
	public void setBufferSize(int size) {
		ensureResponseIsUncommitted();

		// Per Servlet spec, setBufferSize must be called before any content is written
		if (writerObtained() || getServletOutputStream().isPresent() || getResponseOutputStream().size() > 0)
			throw new IllegalStateException("setBufferSize must be called before any content is written");

		setResponseBufferSizeInBytes(size);
		setResponseOutputStream(new ByteArrayOutputStream(getResponseBufferSizeInBytes()));
	}

	@Override
	public int getBufferSize() {
		return getResponseBufferSizeInBytes();
	}

	@Override
	public void flushBuffer() throws IOException {
		if (!isCommitted())
			setResponseCommitted(true);
		getResponseOutputStream().flush();
	}

	@Override
	public void resetBuffer() {
		ensureResponseIsUncommitted();
		getResponseOutputStream().reset();
	}

	@Override
	public boolean isCommitted() {
		return getResponseCommitted();
	}

	@Override
	public void reset() {
		// Clears any data that exists in the buffer as well as the status code, headers.
		// The state of calling getWriter() or getOutputStream() is also cleared.
		// It is legal, for instance, to call getWriter(), reset() and then getOutputStream().
		// If getWriter() or getOutputStream() have been called before this method, then the corresponding returned
		// Writer or OutputStream will be staled and the behavior of using the stale object is undefined.
		// If the response has been committed, this method throws an IllegalStateException.

		ensureResponseIsUncommitted();

		setStatusCode(HttpServletResponse.SC_OK);
		setServletOutputStream(null);
		setPrintWriter(null);
		setResponseWriteMethod(ResponseWriteMethod.UNSPECIFIED);
		setResponseOutputStream(new ByteArrayOutputStream(getResponseBufferSizeInBytes()));
		getHeaders().clear();
		getCookies().clear();

		// Clear content-type/charset & locale to a pristine state
		this.contentType = null;
		setCharset(null);
		this.locale = null;
		this.errorMessage = null;
		this.redirectUrl = null;
	}

	@Override
	public void setLocale(@Nullable Locale locale) {
		if (isCommitted())
			return;

		this.locale = locale;

		if (locale == null) {
			getHeaders().remove("Content-Language");
			return;
		}

		String tag = locale.toLanguageTag();

		if (tag.isBlank())
			getHeaders().remove("Content-Language");
		else
			putHeaderValue("Content-Language", tag, true);
	}

	@Override
	public Locale getLocale() {
		return this.locale == null ? Locale.getDefault() : this.locale;
	}
}
