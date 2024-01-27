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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class SokletHttpServletRequest implements HttpServletRequest {
	@Nonnull
	private final Request request;
	@Nonnull
	private final List<Cookie> cookies;
	@Nullable
	private SokletHttpSession httpSession;

	public SokletHttpServletRequest(@Nonnull Request request) {
		requireNonNull(request);
		this.request = request;
		this.cookies = parseCookies(request);
	}

	@Nonnull
	protected Request getRequest() {
		return this.request;
	}

	@Nonnull
	protected List<Cookie> parseCookies(@Nonnull Request request) {
		requireNonNull(request);
		// TODO: cookie parsing
		return List.of();
	}

	@Nonnull
	protected Optional<SokletHttpSession> getHttpSession() {
		return Optional.ofNullable(this.httpSession);
	}

	protected void setHttpSession(@Nullable SokletHttpSession httpSession) {
		this.httpSession = httpSession;
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
	public String getRequestURI() {
		return getRequest().getPath();
	}

	@Override
	public StringBuffer getRequestURL() {
		// Path only (no query parameters) preceded by remote protocol, host, and port (if available)
		// e.g. https://www.soklet.com/test/abc
		String clientUrlPrefix = Utilities.extractClientUrlPrefixFromHeaders(getRequest().getHeaders()).orElse(null);
		return new StringBuffer(clientUrlPrefix == null ? getRequest().getPath() : format("%s%s", clientUrlPrefix, getRequest().getPath()));
	}

	@Override
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
	public HttpSession getSession() {
		// TODO: implement
		return null;
	}

	@Override
	public String changeSessionId() {
		// TODO: implement
		return null;
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		// TODO: implement
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		// TODO: implement
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		// TODO: implement
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromUrl() {
		// TODO: implement
		return false;
	}

	@Override
	public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
		// TODO: implement
		return false;
	}

	@Override
	public void login(String s, String s1) throws ServletException {
		// TODO: implement
	}

	@Override
	public void logout() throws ServletException {
		// TODO: implement
	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		// TODO: implement
		return null;
	}

	@Override
	public Part getPart(String s) throws IOException, ServletException {
		// TODO: implement
		return null;
	}

	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException {
		// TODO: implement
		return null;
	}

	@Override
	public Object getAttribute(String s) {
		// TODO: implement
		return null;
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		// TODO: implement
		return null;
	}

	@Override
	public String getCharacterEncoding() {
		// TODO: implement
		return null;
	}

	@Override
	public void setCharacterEncoding(String s) throws UnsupportedEncodingException {
		// TODO: implement
	}

	@Override
	public int getContentLength() {
		// TODO: implement
		return 0;
	}

	@Override
	public long getContentLengthLong() {
		// TODO: implement
		return 0;
	}

	@Override
	public String getContentType() {
		// TODO: implement
		return null;
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		// TODO: implement
		return null;
	}

	@Override
	public String getParameter(String s) {
		// TODO: implement
		return null;
	}

	@Override
	public Enumeration<String> getParameterNames() {
		// TODO: implement
		return null;
	}

	@Override
	public String[] getParameterValues(String s) {
		// TODO: implement
		return new String[0];
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		// TODO: implement
		return null;
	}

	@Override
	public String getProtocol() {
		// TODO: implement
		return null;
	}

	@Override
	public String getScheme() {
		// TODO: implement
		return null;
	}

	@Override
	public String getServerName() {
		// TODO: implement
		return null;
	}

	@Override
	public int getServerPort() {
		// TODO: implement
		return 0;
	}

	@Override
	public BufferedReader getReader() throws IOException {
		// TODO: implement
		return null;
	}

	@Override
	public String getRemoteAddr() {
		// TODO: implement
		return null;
	}

	@Override
	public String getRemoteHost() {
		// TODO: implement
		return null;
	}

	@Override
	public void setAttribute(String s, Object o) {
		// TODO: implement

	}

	@Override
	public void removeAttribute(String s) {
		// TODO: implement
	}

	@Override
	public Locale getLocale() {
		// TODO: implement
		return null;
	}

	@Override
	public Enumeration<Locale> getLocales() {
		// TODO: implement
		return null;
	}

	@Override
	public boolean isSecure() {
		// TODO: implement
		return false;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String s) {
		// TODO: implement
		return null;
	}

	@Override
	public String getRealPath(String s) {
		// TODO: implement
		return null;
	}

	@Override
	public int getRemotePort() {
		// TODO: implement
		return 0;
	}

	@Override
	public String getLocalName() {
		// TODO: implement
		return null;
	}

	@Override
	public String getLocalAddr() {
		// TODO: implement
		return null;
	}

	@Override
	public int getLocalPort() {
		// TODO: implement
		return 0;
	}

	@Override
	public ServletContext getServletContext() {
		// TODO: implement
		return null;
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		// TODO: implement
		return null;
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
		// TODO: implement
		return null;
	}

	@Override
	public boolean isAsyncStarted() {
		// TODO: implement
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		// TODO: implement
		return false;
	}

	@Override
	public AsyncContext getAsyncContext() {
		// TODO: implement
		return null;
	}

	@Override
	public DispatcherType getDispatcherType() {
		// TODO: implement
		return null;
	}
}