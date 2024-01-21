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
import com.soklet.core.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class SokletHttpServletResponse implements HttpServletResponse {
	@Nonnull
	private static final Charset DEFAULT_CHARSET;
	@Nonnull
	private static final DateTimeFormatter DATE_TIME_FORMATTER;

	static {
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
	private Integer statusCode;
	@Nonnull
	private Boolean responseCommitted;
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

	public SokletHttpServletResponse(@Nonnull Request request) {
		this(requireNonNull(request).getPath());
	}

	public SokletHttpServletResponse(@Nonnull String requestPath) {
		requireNonNull(requestPath);

		this.requestPath = requestPath;
		this.statusCode = HttpServletResponse.SC_OK;
		this.cookies = new ArrayList<>();
		this.headers = new HashMap<>();
		this.responseCommitted = false;
	}

	@Nonnull
	public Response toResponse() {
		throw new UnsupportedOperationException("TODO");
	}

	@Nonnull
	protected String getRequestPath() {
		return this.requestPath;
	}

	@Nonnull
	protected List<Cookie> getCookies() {
		return this.cookies;
	}

	@Nonnull
	protected Map<String, List<String>> getHeaders() {
		return this.headers;
	}

	@Nonnull
	protected Integer getStatusCode() {
		return this.statusCode;
	}

	protected void setStatusCode(@Nonnull Integer statusCode) {
		requireNonNull(statusCode);
		this.statusCode = statusCode;
	}

	@Nonnull
	protected Optional<String> getErrorMessage() {
		return Optional.ofNullable(this.errorMessage);
	}

	protected void setErrorMessage(@Nullable String errorMessage) {
		this.errorMessage = errorMessage;
	}

	@Nonnull
	protected Optional<String> getRedirectUrl() {
		return Optional.ofNullable(this.redirectUrl);
	}

	protected void setRedirectUrl(@Nullable String redirectUrl) {
		this.redirectUrl = redirectUrl;
	}

	@Nonnull
	protected Optional<Charset> getCharset() {
		return Optional.ofNullable(this.charset);
	}

	protected void setCharset(@Nullable Charset charset) {
		this.charset = charset;
	}

	@Nonnull
	protected Boolean getResponseCommitted() {
		return this.responseCommitted;
	}

	protected void setResponseCommitted(@Nonnull Boolean responseCommitted) {
		requireNonNull(responseCommitted);
		this.responseCommitted = responseCommitted;
	}

	protected void ensureResponseIsUncommitted() {
		if (getResponseCommitted())
			throw new IllegalStateException("Response has already been committed.");
	}

	@Nonnull
	protected String dateHeaderRepresentation(@Nonnull Long millisSinceEpoch) {
		requireNonNull(millisSinceEpoch);
		return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(millisSinceEpoch));
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
		if (location.startsWith("/")) {
			// URL is relative with leading /
			setRedirectUrl(location);
		} else {
			try {
				new URL(location);
				// URL is absolute
				setRedirectUrl(location);
			} catch (MalformedURLException ignored) {
				// URL is relative but does not have leading /
				setRedirectUrl(format("%s/%s", getRequestPath(), location));
			}
		}

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

		if (name == null || name.trim().length() == 0 || value == null)
			return;

		List<String> values = new ArrayList<>();
		values.add(value);
		getHeaders().put(name, values);
	}

	@Override
	public void addHeader(@Nullable String name,
												@Nullable String value) {
		ensureResponseIsUncommitted();

		if (name == null || name.trim().length() == 0 || value == null)
			return;

		List<String> values = getHeaders().get(name);

		if (values == null)
			setHeader(name, value);
		else
			values.add(value);
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
		return this.contentType;
	}

	@Override
	@Nonnull
	public ServletOutputStream getOutputStream() throws IOException {
		throw new UnsupportedOperationException(); // TODO
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		throw new UnsupportedOperationException(); // TODO
	}

	@Override
	public void setCharacterEncoding(String s) {
		// TODO
	}

	@Override
	public void setContentLength(int i) {
		// TODO
	}

	@Override
	public void setContentLengthLong(long l) {
		// TODO
	}

	@Override
	public void setContentType(String s) {
		// TODO
	}

	@Override
	public void setBufferSize(int i) {
		// TODO
	}

	@Override
	public int getBufferSize() {
		return 0;
		// TODO
	}

	@Override
	public void flushBuffer() throws IOException {
		ensureResponseIsUncommitted();
		setResponseCommitted(true);
		// TODO
	}

	@Override
	public void resetBuffer() {
		ensureResponseIsUncommitted();
		// TODO
	}

	@Override
	public boolean isCommitted() {
		return getResponseCommitted();
	}

	@Override
	public void reset() {
		ensureResponseIsUncommitted();
		// TODO
	}

	@Override
	public void setLocale(@Nullable Locale locale) {
		ensureResponseIsUncommitted();
		this.locale = locale;
	}

	@Override
	public Locale getLocale() {
		return this.locale;
	}
}