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
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
	private final String requestPath; // e.g. "/test/abc".  Always starts with "/"
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
		return new SokletHttpServletResponse(request.getPath());
	}

	@Nonnull
	public static SokletHttpServletResponse withRequestPath(@Nonnull String requestPath) {
		requireNonNull(requestPath);
		return new SokletHttpServletResponse(requestPath);
	}

	private SokletHttpServletResponse(@Nonnull String requestPath) {
		requireNonNull(requestPath);

		this.requestPath = requestPath;
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
	private String getRequestPath() {
		return this.requestPath;
	}

	@Nonnull
	private List<Cookie> getCookies() {
		return this.cookies;
	}

	@Nonnull
	private Map<String, List<String>> getHeaders() {
		return this.headers;
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
		ensureResponseIsUncommitted();

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
		setStatus(sc);
		setErrorMessage(msg);
		setResponseCommitted(true);
	}

	@Override
	public void sendError(int sc) throws IOException {
		ensureResponseIsUncommitted();
		setStatus(sc);
		setErrorMessage(null);
		setResponseCommitted(true);
	}

	@Override
	public void sendRedirect(@Nullable String location) throws IOException {
		ensureResponseIsUncommitted();
		setStatus(HttpServletResponse.SC_FOUND);

		// This method can accept relative URLs; the servlet container must convert the relative URL to an absolute URL
		// before sending the response to the client. If the location is relative without a leading '/' the container
		// interprets it as relative to the current request URI. If the location is relative with a leading '/'
		// the container interprets it as relative to the servlet container root. If the location is relative with two
		// leading '/' the container interprets it as a network-path reference (see RFC 3986: Uniform Resource
		// Identifier (URI): Generic Syntax, section 4.2 "Relative Reference").
		String finalLocation;

		if (location.startsWith("/")) {
			// URL is relative with leading /
			finalLocation = location;
		} else {
			try {
				new URL(location);
				// URL is absolute
				finalLocation = location;
			} catch (MalformedURLException ignored) {
				// URL is relative but does not have leading '/', resolve against the parent of the current path
				String base = getRequestPath();
				int idx = base.lastIndexOf('/');
				String parent = (idx <= 0) ? "/" : base.substring(0, idx);
				finalLocation = parent.endsWith("/") ? parent + location : parent + "/" + location;
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
		ensureResponseIsUncommitted();
		setHeader(name, dateHeaderRepresentation(date));
	}

	@Override
	public void addDateHeader(@Nullable String name,
														long date) {
		ensureResponseIsUncommitted();
		addHeader(name, dateHeaderRepresentation(date));
	}

	@Override
	public void setHeader(@Nullable String name,
												@Nullable String value) {
		ensureResponseIsUncommitted();

		if (name != null && !name.isBlank() && value != null) {
			List<String> values = new ArrayList<>();
			values.add(value);
			getHeaders().put(name, values);
		}
	}

	@Override
	public void addHeader(@Nullable String name,
												@Nullable String value) {
		ensureResponseIsUncommitted();

		if (name != null && !name.isBlank() && value != null)
			getHeaders().computeIfAbsent(name, k -> new ArrayList<>()).add(value);
	}

	@Override
	public void setIntHeader(@Nullable String name,
													 int value) {
		ensureResponseIsUncommitted();
		setHeader(name, String.valueOf(value));
	}

	@Override
	public void addIntHeader(@Nullable String name,
													 int value) {
		ensureResponseIsUncommitted();
		addHeader(name, String.valueOf(value));
	}

	@Override
	public void setStatus(int sc) {
		ensureResponseIsUncommitted();
		this.statusCode = sc;
	}

	@Override
	@Deprecated
	public void setStatus(int sc,
												@Nullable String sm) {
		ensureResponseIsUncommitted();
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
		return getCharset().orElse(DEFAULT_CHARSET).name();
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
		// (i.e., the method just returns the default value ISO-8859-1), getWriter updates it to ISO-8859-1.
		// Calling flush() on the PrintWriter commits the response.
		//
		// Either this method or getOutputStream() may be called to write the body, not both, except when reset() has been called.
		// Returns a PrintWriter that uses the character encoding returned by getCharacterEncoding().
		// If not specified yet, calling getWriter() fixes the encoding to ISO-8859-1 per spec.
		ResponseWriteMethod currentResponseWriteMethod = getResponseWriteMethod();

		if (currentResponseWriteMethod == ResponseWriteMethod.UNSPECIFIED) {
			// Freeze encoding now
			Charset enc = getCharset().orElse(DEFAULT_CHARSET);
			setCharset(enc); // record the chosen encoding explicitly

			// If a content type is already present and lacks charset, append the frozen charset to header
			if (this.contentType != null) {
				Optional<String> csInHeader = extractCharsetFromContentType(this.contentType);
				if (csInHeader.isEmpty() || !csInHeader.get().equalsIgnoreCase(enc.name())) {
					String updated = withCharset(this.contentType, enc.name()).orElse(null);

					if (updated != null) {
						this.contentType = updated;
						setHeader("Content-Type", updated);
					} else {
						setHeader("Content-Type", this.contentType);
					}
				}
			}

			setResponseWriteMethod(ResponseWriteMethod.PRINT_WRITER);

			this.printWriter =
					SokletServletPrintWriter.withWriter(
									new OutputStreamWriter(getResponseOutputStream(), enc))
							.onWriteOccurred((ignored1, ignored2) -> setResponseCommitted(true))   // commit on first write
							.onWriteFinalized((ignored) -> setResponseFinalized(true))
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
		ensureResponseIsUncommitted();

		// Spec: no effect after getWriter() or after commit
		if (writerObtained())
			return;

		if (charset == null || charset.isBlank()) {
			// Clear explicit charset; default will be chosen at writer time if needed
			setCharset(null);

			// If a Content-Type is set, remove its charset=... parameter
			if (this.contentType != null) {
				String updated = stripCharsetParam(this.contentType).orElse(null);
				this.contentType = updated;
				if (updated == null || updated.isBlank()) {
					getHeaders().remove("Content-Type");
				} else {
					setHeader("Content-Type", updated);
				}
			}

			return;
		}

		Charset cs = Charset.forName(charset);
		setCharset(cs);

		// If a Content-Type is set, reflect/replace the charset=... in the header
		if (this.contentType != null) {
			String updated = withCharset(this.contentType, cs.name()).orElse(null);

			if (updated != null) {
				this.contentType = updated;
				setHeader("Content-Type", updated);
			} else {
				setHeader("Content-Type", this.contentType);
			}
		}
	}

	@Override
	public void setContentLength(int len) {
		ensureResponseIsUncommitted();
		setHeader("Content-Length", String.valueOf(len));
	}

	@Override
	public void setContentLengthLong(long len) {
		ensureResponseIsUncommitted();
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
				setHeader("Content-Type", type);
			} else {
				// No charset in type. If an explicit charset already exists (via setCharacterEncoding),
				// reflect it in the header; otherwise just set the type as-is.
				if (getCharset().isPresent()) {
					String updated = withCharset(type, getCharset().get().name()).orElse(null);

					if (updated != null) {
						this.contentType = updated;
						setHeader("Content-Type", updated);
					} else {
						setHeader("Content-Type", type);
					}
				} else {
					setHeader("Content-Type", type);
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
				setHeader("Content-Type", normalized);
			} else {
				this.contentType = type;
				setHeader("Content-Type", type);
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
	}

	@Override
	public void setLocale(@Nullable Locale locale) {
		ensureResponseIsUncommitted();
		this.locale = locale;
	}

	@Override
	public Locale getLocale() {
		return this.locale == null ? Locale.getDefault() : this.locale;
	}
}
