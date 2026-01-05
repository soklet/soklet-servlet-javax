/*
 * Copyright 2024-2026 Revetware LLC.
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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
@ThreadSafe
public final class SokletServletContext implements ServletContext {
	@NonNull
	private final Writer logWriter;
	@NonNull
	private final Object logLock;
	@NonNull
	private final Map<@NonNull String, @NonNull Object> attributes;
	@Nullable
	private final ResourceRoot resourceRoot;
	private volatile @Nullable Integer sessionTimeout;
	@Nullable
	private volatile Charset requestCharset;
	@Nullable
	private volatile Charset responseCharset;

	@NonNull
	public static SokletServletContext withDefaults() {
		return builder().build();
	}

	@NonNull
	public static Builder builder() {
		return new Builder();
	}

	private SokletServletContext(@Nullable Writer logWriter,
															 @Nullable ResourceRoot resourceRoot,
															 @Nullable Integer sessionTimeout,
															 @Nullable Charset requestCharset,
															 @Nullable Charset responseCharset) {
		this.logWriter = logWriter == null ? new NoOpWriter() : logWriter;
		this.logLock = new Object();
		this.attributes = new ConcurrentHashMap<>();
		this.resourceRoot = resourceRoot;
		this.sessionTimeout = sessionTimeout;
		this.requestCharset = requestCharset;
		this.responseCharset = responseCharset;
	}

	@NonNull
	private Writer getLogWriter() {
		return this.logWriter;
	}

	@NonNull
	private Map<@NonNull String, @NonNull Object> getAttributes() {
		return this.attributes;
	}

	@NonNull
	private Optional<ResourceRoot> getResourceRoot() {
		return Optional.ofNullable(this.resourceRoot);
	}

	/**
	 * Builder used to construct instances of {@link SokletServletContext}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://www.revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@Nullable
		private Writer logWriter;
		@Nullable
		private ResourceRoot resourceRoot;
		@Nullable
		private Integer sessionTimeout;
		@Nullable
		private Charset requestCharset;
		@Nullable
		private Charset responseCharset;

		private Builder() {
			this.sessionTimeout = null;
			this.requestCharset = StandardCharsets.ISO_8859_1;
			this.responseCharset = StandardCharsets.ISO_8859_1;
		}

		@NonNull
		public Builder logWriter(@Nullable Writer logWriter) {
			this.logWriter = logWriter;
			return this;
		}

		@NonNull
		public Builder filesystemResourceRoot(@NonNull Path resourceRoot) {
			requireNonNull(resourceRoot);
			this.resourceRoot = ResourceRoot.forFilesystem(resourceRoot);
			return this;
		}

		@NonNull
		public Builder classpathResourceRoot(@NonNull String resourceRoot) {
			requireNonNull(resourceRoot);
			this.resourceRoot = ResourceRoot.forClasspath(resourceRoot);
			return this;
		}

		@NonNull
		public Builder sessionTimeout(int sessionTimeout) {
			this.sessionTimeout = sessionTimeout;
			return this;
		}

		@NonNull
		public Builder requestCharacterEncoding(@Nullable String encoding) {
			this.requestCharset = parseCharset(encoding, this.requestCharset);
			return this;
		}

		@NonNull
		public Builder responseCharacterEncoding(@Nullable String encoding) {
			this.responseCharset = parseCharset(encoding, this.responseCharset);
			return this;
		}

		@NonNull
		public SokletServletContext build() {
			return new SokletServletContext(
					this.logWriter,
					this.resourceRoot,
					this.sessionTimeout,
					this.requestCharset,
					this.responseCharset);
		}

		@Nullable
		private static Charset parseCharset(@Nullable String encoding,
																				@Nullable Charset fallback) {
			if (encoding == null)
				return null;

			try {
				return Charset.forName(encoding);
			} catch (Exception ignored) {
				return fallback;
			}
		}
	}

	@ThreadSafe
	private interface ResourceRoot {
		@Nullable
		Set<@NonNull String> getResourcePaths(@NonNull String path);

		@Nullable
		URL getResource(@NonNull String path) throws MalformedURLException;

		@Nullable
		InputStream getResourceAsStream(@NonNull String path);

		@NonNull
		static ResourceRoot forFilesystem(@NonNull Path resourceRoot) {
			return new FilesystemResourceRoot(resourceRoot);
		}

		@NonNull
		static ResourceRoot forClasspath(@NonNull String resourceRoot) {
			return new ClasspathResourceRoot(resourceRoot);
		}
	}

	@ThreadSafe
	private static final class FilesystemResourceRoot implements ResourceRoot {
		@NonNull
		private final Path root;

		private FilesystemResourceRoot(@NonNull Path root) {
			requireNonNull(root);
			this.root = root.toAbsolutePath().normalize();
		}

		@NonNull
		private Optional<Path> resolvePath(@NonNull String path) {
			String relative = path.substring(1);
			Path resolved = root.resolve(relative).normalize();
			return resolved.startsWith(root) ? Optional.of(resolved) : Optional.empty();
		}

		@Override
		@Nullable
		public Set<@NonNull String> getResourcePaths(@NonNull String path) {
			requireNonNull(path);
			String normalized = path;

			if (!normalized.endsWith("/"))
				normalized += "/";

			Path dir = resolvePath(normalized).orElse(null);

			if (dir == null || !Files.isDirectory(dir))
				return null;

			try (Stream<Path> stream = Files.list(dir)) {
				Set<@NonNull String> out = new java.util.TreeSet<>();
				String prefix = normalized;

				stream.forEach(child -> {
					String name = child.getFileName().toString();
					boolean isDir = Files.isDirectory(child);
					out.add(prefix + name + (isDir ? "/" : ""));
				});

				return out.isEmpty() ? null : out;
			} catch (IOException ignored) {
				return null;
			}
		}

		@Override
		@Nullable
		public URL getResource(@NonNull String path) throws MalformedURLException {
			requireNonNull(path);
			Path resolved = resolvePath(path).orElse(null);

			if (resolved == null || !Files.exists(resolved))
				return null;

			return resolved.toUri().toURL();
		}

		@Override
		@Nullable
		public InputStream getResourceAsStream(@NonNull String path) {
			try {
				URL url = getResource(path);
				return url == null ? null : url.openStream();
			} catch (IOException ignored) {
				return null;
			}
		}
	}

	@ThreadSafe
	private static final class ClasspathResourceRoot implements ResourceRoot {
		@NonNull
		private final String rootPrefix;
		@NonNull
		private final ClassLoader classLoader;

		private ClasspathResourceRoot(@NonNull String rootPrefix) {
			requireNonNull(rootPrefix);
			this.rootPrefix = normalizePrefix(rootPrefix);
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			this.classLoader = loader == null ? SokletServletContext.class.getClassLoader() : loader;
		}

		@NonNull
		private static String normalizePrefix(@NonNull String prefix) {
			String normalized = prefix.trim();

			if (normalized.startsWith("/"))
				normalized = normalized.substring(1);

			if (!normalized.isEmpty() && !normalized.endsWith("/"))
				normalized += "/";

			if (containsDotDotSegment(normalized))
				throw new IllegalArgumentException("Classpath resource root must not contain '..'");

			return normalized;
		}

		private static boolean containsDotDotSegment(@NonNull String path) {
			for (String segment : path.split("/")) {
				if ("..".equals(segment))
					return true;
			}

			return false;
		}

		@NonNull
		private Optional<String> toClasspathPath(@NonNull String path,
																						 boolean forDirectoryListing) {
			if (containsDotDotSegment(path))
				return Optional.empty();

			String relative = path.substring(1);
			String lookup = rootPrefix + relative;

			if (forDirectoryListing && !lookup.endsWith("/") && !lookup.isEmpty())
				lookup += "/";

			return Optional.of(lookup);
		}

		private void addFilesystemEntries(@NonNull Path dir,
																			@NonNull String prefix,
																			@NonNull Set<@NonNull String> out) throws IOException {
			if (!Files.isDirectory(dir))
				return;

			try (Stream<Path> stream = Files.list(dir)) {
				stream.forEach(child -> {
					String name = child.getFileName().toString();
					boolean isDir = Files.isDirectory(child);
					out.add(prefix + name + (isDir ? "/" : ""));
				});
			}
		}

		private void addJarEntries(@NonNull JarFile jar,
															 @NonNull String jarPrefix,
															 @NonNull String prefix,
															 @NonNull Set<@NonNull String> out) {
			jar.stream()
					.map(JarEntry::getName)
					.filter(name -> name.startsWith(jarPrefix) && !name.equals(jarPrefix))
					.map(name -> {
						String remainder = name.substring(jarPrefix.length());
						int slash = remainder.indexOf('/');
						if (slash == -1)
							return prefix + remainder;

						return prefix + remainder.substring(0, slash + 1);
					})
					.forEach(out::add);
		}

		private void addClasspathRootEntries(@NonNull URL rootUrl,
																				 @NonNull String classpathPath,
																				 @NonNull String prefix,
																				 @NonNull Set<@NonNull String> out) throws Exception {
			String protocol = rootUrl.getProtocol();

			if ("file".equals(protocol)) {
				Path rootPath = Paths.get(rootUrl.toURI());
				if (Files.isDirectory(rootPath)) {
					Path dir = classpathPath.isEmpty() ? rootPath : rootPath.resolve(classpathPath);
					addFilesystemEntries(dir, prefix, out);
				} else if (Files.isRegularFile(rootPath)) {
					try (JarFile jar = new JarFile(rootPath.toFile())) {
						addJarEntries(jar, classpathPath, prefix, out);
					}
				}
			} else if ("jar".equals(protocol)) {
				String spec = rootUrl.getFile();
				int bang = spec.indexOf("!");
				String jarPath = bang >= 0 ? spec.substring(0, bang) : spec;
				URL jarUrl = new URL(jarPath);

				try (JarFile jar = new JarFile(new java.io.File(jarUrl.toURI()))) {
					addJarEntries(jar, classpathPath, prefix, out);
				}
			}
		}

		@Override
		@Nullable
		public Set<@NonNull String> getResourcePaths(@NonNull String path) {
			requireNonNull(path);

			String classpathPath = toClasspathPath(path, true).orElse(null);

			if (classpathPath == null)
				return null;

			try {
				Enumeration<@NonNull URL> roots = classLoader.getResources(classpathPath);
				Set<@NonNull String> out = new java.util.TreeSet<>();
				String prefix = path.endsWith("/") ? path : path + "/";
				boolean sawRoot = false;

				while (roots.hasMoreElements()) {
					sawRoot = true;
					URL url = roots.nextElement();
					String protocol = url.getProtocol();

					if ("file".equals(protocol)) {
						Path rootPath = Paths.get(url.toURI());
						if (Files.isDirectory(rootPath)) {
							addFilesystemEntries(rootPath, prefix, out);
						} else if (Files.isRegularFile(rootPath)) {
							try (JarFile jar = new JarFile(rootPath.toFile())) {
								addJarEntries(jar, classpathPath, prefix, out);
							}
						}
					} else if ("jar".equals(protocol)) {
						String spec = url.getFile();
						int bang = spec.indexOf("!");
						String jarPath = spec.substring(0, bang);
						URL jarUrl = new URL(jarPath);

						try (JarFile jar = new JarFile(new java.io.File(jarUrl.toURI()))) {
							String jarPrefix = classpathPath;
							addJarEntries(jar, jarPrefix, prefix, out);
						}
					}
				}

				if (!sawRoot) {
					Enumeration<@NonNull URL> classpathRoots = classLoader.getResources("");

					while (classpathRoots.hasMoreElements()) {
						URL rootUrl = classpathRoots.nextElement();
						addClasspathRootEntries(rootUrl, classpathPath, prefix, out);
					}
				}

				if (out.isEmpty() && classLoader instanceof URLClassLoader) {
					URL[] urls = ((URLClassLoader) classLoader).getURLs();

					for (URL rootUrl : urls)
						addClasspathRootEntries(rootUrl, classpathPath, prefix, out);
				}

				return out.isEmpty() ? null : out;
			} catch (Exception ignored) {
				return null;
			}
		}

		@Override
		@Nullable
		public URL getResource(@NonNull String path) throws MalformedURLException {
			requireNonNull(path);

			String classpathPath = toClasspathPath(path, false).orElse(null);

			if (classpathPath == null)
				return null;

			URL url = classLoader.getResource(classpathPath);
			return url;
		}

		@Override
		@Nullable
		public InputStream getResourceAsStream(@NonNull String path) {
			String classpathPath = toClasspathPath(path, false).orElse(null);

			if (classpathPath == null)
				return null;

			return classLoader.getResourceAsStream(classpathPath);
		}
	}
	@ThreadSafe
	private static class NoOpWriter extends Writer {
		@Override
		public void write(@NonNull char[] cbuf,
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
	public Set<@NonNull String> getResourcePaths(@Nullable String path) {
		if (path == null || !path.startsWith("/"))
			return null;

		ResourceRoot root = getResourceRoot().orElse(null);
		return root == null ? null : root.getResourcePaths(path);
	}

	@Override
	@Nullable
	public URL getResource(@Nullable String path) throws MalformedURLException {
		if (path == null)
			return null;

		if (!path.startsWith("/"))
			throw new MalformedURLException("ServletContext resource paths must start with '/'");

		ResourceRoot root = getResourceRoot().orElse(null);
		return root == null ? null : root.getResource(path);
	}

	@Override
	@Nullable
	public InputStream getResourceAsStream(@Nullable String path) {
		if (path == null || !path.startsWith("/"))
			return null;

		ResourceRoot root = getResourceRoot().orElse(null);
		return root == null ? null : root.getResourceAsStream(path);
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
	@NonNull
	public Enumeration<@NonNull Servlet> getServlets() {
		// Deliberately empty per spec b/c this method is deprecated
		return Collections.emptyEnumeration();
	}

	@Override
	@Deprecated
	@NonNull
	public Enumeration<@NonNull String> getServletNames() {
		// Deliberately empty per spec b/c this method is deprecated
		return Collections.emptyEnumeration();
	}

	@Override
	public void log(@Nullable String msg) {
		if (msg == null)
			return;

		try {
			synchronized (this.logLock) {
				getLogWriter().write(msg);
			}
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
		List<@NonNull String> components = new ArrayList<>(2);

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
			synchronized (this.logLock) {
				getLogWriter().write(combinedMessage);
			}
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
	@NonNull
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
	@NonNull
	public Enumeration<@NonNull String> getInitParameterNames() {
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
	@NonNull
	public Enumeration<@NonNull String> getAttributeNames() {
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
		if (name == null)
			return;

		getAttributes().remove(name);
	}

	@Override
	@Nullable
	public String getServletContextName() {
		// This is legal according to spec
		return null;
	}

	@Override
	public ServletRegistration.@Nullable Dynamic addServlet(@Nullable String servletName,
																													@Nullable String className) {
		throw new IllegalStateException("Soklet does not support adding Servlets");
	}

	@Override
	public ServletRegistration.@Nullable Dynamic addServlet(@Nullable String servletName,
																													@Nullable Servlet servlet) {
		throw new IllegalStateException("Soklet does not support adding Servlets");
	}

	@Override
	public ServletRegistration.@Nullable Dynamic addServlet(@Nullable String servletName,
																													@Nullable Class<? extends Servlet> servletClass) {
		throw new IllegalStateException("Soklet does not support adding Servlets");
	}

	@Override
	public ServletRegistration.@Nullable Dynamic addJspFile(@Nullable String servletName,
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
	@NonNull
	public Map<@NonNull String, ? extends @NonNull ServletRegistration> getServletRegistrations() {
		return Map.of();
	}

	@Override
	public FilterRegistration.@Nullable Dynamic addFilter(@Nullable String filterName,
																												@Nullable String className) {
		throw new IllegalStateException("Soklet does not support adding Filters");
	}

	@Override
	public FilterRegistration.@Nullable Dynamic addFilter(@Nullable String filterName,
																												@Nullable Filter filter) {
		throw new IllegalStateException("Soklet does not support adding Filters");
	}

	@Override
	public FilterRegistration.@Nullable Dynamic addFilter(@Nullable String filterName,
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
	@NonNull
	public Map<@NonNull String, ? extends @NonNull FilterRegistration> getFilterRegistrations() {
		return Map.of();
	}

	@Override
	@Nullable
	public SessionCookieConfig getSessionCookieConfig() {
		// Diverges from spec here; Soklet has no concept of "session cookie"
		throw new IllegalStateException("Soklet does not support session cookies");
	}

	@Override
	public void setSessionTrackingModes(@Nullable Set<@NonNull SessionTrackingMode> sessionTrackingModes) {
		throw new IllegalStateException("Soklet does not support session tracking");
	}

	@Override
	@NonNull
	public Set<@NonNull SessionTrackingMode> getDefaultSessionTrackingModes() {
		return Set.of();
	}

	@Override
	@NonNull
	public Set<@NonNull SessionTrackingMode> getEffectiveSessionTrackingModes() {
		return Set.of();
	}

	@Override
	public void addListener(@Nullable String className) {
		throw new IllegalStateException("Soklet does not support listeners");
	}

	@Override
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
	@NonNull
	public ClassLoader getClassLoader() {
		return this.getClass().getClassLoader();
	}

	@Override
	public void declareRoles(@Nullable String @Nullable ... strings) {
		throw new IllegalStateException("Soklet does not support Servlet roles");
	}

	@Override
	@NonNull
	public String getVirtualServerName() {
		return "soklet";
	}

	@Override
	public int getSessionTimeout() {
		Integer timeout = this.sessionTimeout;
		return timeout == null ? -1 : timeout;
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
		if (encoding == null) {
			this.requestCharset = null;
			return;
		}

		try {
			this.requestCharset = Charset.forName(encoding);
		} catch (Exception ignored) {
			// Ignore invalid charset tokens.
		}
	}

	@Override
	@Nullable
	public String getResponseCharacterEncoding() {
		return this.responseCharset == null ? null : this.responseCharset.name();
	}

	@Override
	public void setResponseCharacterEncoding(@Nullable String encoding) {
		if (encoding == null) {
			this.responseCharset = null;
			return;
		}

		try {
			this.responseCharset = Charset.forName(encoding);
		} catch (Exception ignored) {
			// Ignore invalid charset tokens.
		}
	}
}
