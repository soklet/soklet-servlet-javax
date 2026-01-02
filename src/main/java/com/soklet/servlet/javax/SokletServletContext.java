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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link ServletContext}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class SokletServletContext implements ServletContext {
	@Nonnull
	private final Writer logWriter;
	@Nonnull
	private final Map<String, Object> attributes;
	@Nonnull
	private int sessionTimeout;
	@Nullable
	private Charset requestCharset;
	@Nullable
	private Charset responseCharset;

	@Nonnull
	public static SokletServletContext withDefaults() {
		return new SokletServletContext(null);
	}

	@Nonnull
	public static SokletServletContext withLogWriter(@Nullable Writer logWriter) {
		return new SokletServletContext(logWriter);
	}

	private SokletServletContext(@Nullable Writer logWriter) {
		this.logWriter = logWriter == null ? new NoOpWriter() : logWriter;
		this.attributes = new HashMap<>();
		this.sessionTimeout = -1;
		this.requestCharset = StandardCharsets.UTF_8;
		this.responseCharset = StandardCharsets.UTF_8;
	}

	@Nonnull
	private Writer getLogWriter() {
		return this.logWriter;
	}

	@Nonnull
	private Map<String, Object> getAttributes() {
		return this.attributes;
	}

	@ThreadSafe
	private static class NoOpWriter extends Writer {
		@Override
		public void write(@Nonnull char[] cbuf,
											int off,
											int len) throws IOException {
			requireNonNull(cbuf);
			// No-op
		}

		@Override
		public void flush() throws IOException {
			// No-op
		}

		@Override
		public void close() throws IOException {
			// No-op
		}
	}

	// Implementation of ServletContext methods below:

	@Override
	@Nullable
	public String getContextPath() {
		return "";
	}

	@Override
	@Nullable
	public ServletContext getContext(@Nullable String uripath) {
		if (uripath == null)
			return null;

		String normalized = uripath.trim();

		if (normalized.isEmpty() || "/".equals(normalized))
			return this;

		return null;
	}

	@Override
	public int getMajorVersion() {
		return 4;
	}

	@Override
	public int getMinorVersion() {
		return 0;
	}

	@Override
	public int getEffectiveMajorVersion() {
		return 4;
	}

	@Override
	public int getEffectiveMinorVersion() {
		return 0;
	}

	@Override
	@Nullable
	public String getMimeType(@Nullable String file) {
		if (file == null)
			return null;

		return URLConnection.guessContentTypeFromName(file);
	}

	@Override
	@Nullable
	public Set<String> getResourcePaths(@Nullable String path) {
		// TODO: revisit https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletContext.html#getResourcePaths-java.lang.String-
		if (path == null || !path.startsWith("/"))
			return null;

		try {
			String normalized = path.equals("/") ? "" : path.substring(1);

			if (!normalized.endsWith("/") && !normalized.isEmpty())
				normalized += "/";

			Enumeration<URL> roots =
					Thread.currentThread().getContextClassLoader().getResources(normalized);

			Set<String> out = new java.util.TreeSet<>();

			while (roots.hasMoreElements()) {
				URL url = roots.nextElement();
				String protocol = url.getProtocol();

				if ("file".equals(protocol)) {
					Path p = Paths.get(url.toURI());

					try (Stream<Path> s = Files.list(p)) {
						s.forEach(child -> {
							String name = child.getFileName().toString();
							boolean dir = java.nio.file.Files.isDirectory(child);
							out.add((path.endsWith("/") ? path : path + "/") + name + (dir ? "/" : ""));
						});
					}
				} else if ("jar".equals(protocol)) {
					String spec = url.getFile();           // e.g. file:/app.jar!/static/
					int bang = spec.indexOf("!");
					String jarPath = spec.substring(0, bang);
					java.net.URL jarUrl = new java.net.URL(jarPath);
					try (JarFile jar = new JarFile(new File(jarUrl.toURI()))) {
						String prefix = normalized;
						jar.stream()
								.map(JarEntry::getName)
								.filter(n -> n.startsWith(prefix) && !n.equals(prefix))
								.map(n -> {
									String remainder = n.substring(prefix.length());
									int slash = remainder.indexOf('/');
									if (slash == -1)
										return (path.endsWith("/") ? path : path + "/") + remainder;

									return (path.endsWith("/") ? path : path + "/") + remainder.substring(0, slash + 1);
								})
								.forEach(out::add);
					}
				}
			}
			return out.isEmpty() ? null : out;
		} catch (Exception ignored) {
			return null;
		}
	}

	@Override
	@Nullable
	public URL getResource(@Nullable String path) throws MalformedURLException {
		// TODO: revisit https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletContext.html#getResource-java.lang.String-
		if (path == null || !path.startsWith("/"))
			return null;

		return getClass().getResource(path); // may be null
	}

	@Override
	@Nullable
	public InputStream getResourceAsStream(@Nullable String path) {
		// TODO: revisit https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletContext.html#getResourceAsStream-java.lang.String-
		if (path == null || !path.startsWith("/"))
			return null;

		return getClass().getResourceAsStream(path); // may be null
	}

	@Override
	@Nullable
	public RequestDispatcher getRequestDispatcher(@Nullable String path) {
		if (path == null || path.isBlank())
			return null;

		return null;
	}

	@Override
	@Nullable
	public RequestDispatcher getNamedDispatcher(@Nullable String name) {
		// TODO: revisit https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletContext.html#getNamedDispatcher-java.lang.String-
		// This is legal according to spec, but we likely want a real instance returned
		return null;
	}

	@Override
	@Deprecated
	@Nullable
	public Servlet getServlet(@Nullable String name) throws ServletException {
		// Deliberately null per spec b/c this method is deprecated
		return null;
	}

	@Override
	@Deprecated
	@Nonnull
	public Enumeration<Servlet> getServlets() {
		// Deliberately empty per spec b/c this method is deprecated
		return Collections.emptyEnumeration();
	}

	@Override
	@Deprecated
	@Nonnull
	public Enumeration<String> getServletNames() {
		// Deliberately empty per spec b/c this method is deprecated
		return Collections.emptyEnumeration();
	}

	@Override
	public void log(@Nullable String msg) {
		if (msg == null)
			return;

		try {
			getLogWriter().write(msg);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	@Deprecated
	public void log(@Nullable Exception exception,
									@Nullable String msg) {
		if (exception == null && msg == null)
			return;

		log(msg, exception);
	}

	@Override
	public void log(@Nullable String message,
									@Nullable Throwable throwable) {
		List<String> components = new ArrayList<>(2);

		if (message != null)
			components.add(message);

		if (throwable != null) {
			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			throwable.printStackTrace(printWriter);
			components.add(stringWriter.toString());
		}

		String combinedMessage = components.stream().collect(Collectors.joining("\n"));

		try {
			getLogWriter().write(combinedMessage);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	@Nullable
	public String getRealPath(@Nullable String path) {
		// Soklet has no concept of a physical path on the filesystem for a URL path
		return null;
	}

	@Override
	@Nonnull
	public String getServerInfo() {
		return "Soklet/Undefined";
	}

	@Override
	@Nullable
	public String getInitParameter(String name) {
		// Soklet has no concept of init parameters
		return null;
	}

	@Override
	@Nonnull
	public Enumeration<String> getInitParameterNames() {
		// Soklet has no concept of init parameters
		return Collections.emptyEnumeration();
	}

	@Override
	public boolean setInitParameter(@Nullable String name,
																	@Nullable String value) {
		throw new IllegalStateException(format("Soklet does not support %s init parameters.",
				ServletContext.class.getSimpleName()));
	}

	@Override
	@Nullable
	public Object getAttribute(@Nullable String name) {
		return name == null ? null : getAttributes().get(name);
	}

	@Override
	@Nonnull
	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(getAttributes().keySet());
	}

	@Override
	public void setAttribute(@Nullable String name,
													 @Nullable Object object) {
		if (name == null)
			return;

		if (object == null)
			removeAttribute(name);
		else
			getAttributes().put(name, object);
	}

	@Override
	public void removeAttribute(@Nullable String name) {
		getAttributes().remove(name);
	}

	@Override
	@Nullable
	public String getServletContextName() {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nullable
	public ServletRegistration.Dynamic addServlet(@Nullable String servletName,
																								@Nullable String className) {
		throw new IllegalStateException("Soklet does not support adding Servlets");
	}

	@Override
	@Nullable
	public ServletRegistration.Dynamic addServlet(@Nullable String servletName,
																								@Nullable Servlet servlet) {
		throw new IllegalStateException("Soklet does not support adding Servlets");
	}

	@Override
	@Nullable
	public ServletRegistration.Dynamic addServlet(@Nullable String servletName,
																								@Nullable Class<? extends Servlet> servletClass) {
		throw new IllegalStateException("Soklet does not support adding Servlets");
	}

	@Override
	@Nullable
	public ServletRegistration.Dynamic addJspFile(@Nullable String servletName,
																								@Nullable String jspFile) {
		throw new IllegalStateException("Soklet does not support adding JSP files");
	}

	@Override
	@Nullable
	public <T extends Servlet> T createServlet(@Nullable Class<T> clazz) throws ServletException {
		throw new ServletException("Soklet does not support creating Servlets");
	}

	@Override
	@Nullable
	public ServletRegistration getServletRegistration(@Nullable String servletName) {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nonnull
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		return Map.of();
	}

	@Override
	@Nullable
	public FilterRegistration.Dynamic addFilter(@Nullable String filterName,
																							@Nullable String className) {
		throw new IllegalStateException("Soklet does not support adding Filters");
	}

	@Override
	@Nullable
	public FilterRegistration.Dynamic addFilter(@Nullable String filterName,
																							@Nullable Filter filter) {
		throw new IllegalStateException("Soklet does not support adding Filters");
	}

	@Override
	@Nullable
	public FilterRegistration.Dynamic addFilter(@Nullable String filterName,
																							@Nullable Class<? extends Filter> filterClass) {
		throw new IllegalStateException("Soklet does not support adding Filters");
	}

	@Override
	@Nullable
	public <T extends Filter> T createFilter(@Nullable Class<T> clazz) throws ServletException {
		throw new ServletException("Soklet does not support creating Filters");
	}

	@Override
	@Nullable
	public FilterRegistration getFilterRegistration(@Nullable String filterName) {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nonnull
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		return Map.of();
	}

	@Override
	@Nullable
	public SessionCookieConfig getSessionCookieConfig() {
		// Diverges from spec here; Soklet has no concept of "session cookie"
		throw new IllegalStateException("Soklet does not support session cookies");
	}

	@Override
	public void setSessionTrackingModes(@Nullable Set<SessionTrackingMode> sessionTrackingModes) {
		throw new IllegalStateException("Soklet does not support session tracking");
	}

	@Override
	@Nonnull
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		return Set.of();
	}

	@Override
	@Nonnull
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		return Set.of();
	}

	@Override
	public void addListener(@Nullable String className) {
		throw new IllegalStateException("Soklet does not support listeners");
	}

	@Override
	@Nullable
	public <T extends EventListener> void addListener(@Nullable T t) {
		throw new IllegalStateException("Soklet does not support listeners");
	}

	@Override
	public void addListener(@Nullable Class<? extends EventListener> listenerClass) {
		throw new IllegalStateException("Soklet does not support listeners");
	}

	@Override
	@Nullable
	public <T extends EventListener> T createListener(@Nullable Class<T> clazz) throws ServletException {
		throw new ServletException("Soklet does not support listeners");
	}

	@Override
	@Nullable
	public JspConfigDescriptor getJspConfigDescriptor() {
		// This is legal according to spec
		return null;
	}

	@Override
	@Nonnull
	public ClassLoader getClassLoader() {
		return this.getClass().getClassLoader();
	}

	@Override
	public void declareRoles(@Nullable String... strings) {
		throw new IllegalStateException("Soklet does not support Servlet roles");
	}

	@Override
	@Nonnull
	public String getVirtualServerName() {
		return "soklet";
	}

	@Override
	public int getSessionTimeout() {
		return this.sessionTimeout;
	}

	@Override
	public void setSessionTimeout(int sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}

	@Override
	@Nullable
	public String getRequestCharacterEncoding() {
		return this.requestCharset == null ? null : this.requestCharset.name();
	}

	@Override
	public void setRequestCharacterEncoding(@Nullable String encoding) {
		this.requestCharset = encoding == null ? null : Charset.forName(encoding);
	}

	@Override
	@Nullable
	public String getResponseCharacterEncoding() {
		return this.responseCharset == null ? null : this.responseCharset.name();
	}

	@Override
	public void setResponseCharacterEncoding(@Nullable String encoding) {
		this.responseCharset = encoding == null ? null : Charset.forName(encoding);
	}
}
