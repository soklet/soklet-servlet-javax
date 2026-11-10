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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** A classpath prefix remains a boundary when filesystem loaders interpret names. */
@Timeout(10)
class ClasspathResourcePrefixSafetyTests {
	@TempDir
	Path directory;

	@Test
	void invalidConfiguredPrefixesFailBeforeClassLoaderAccess() throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (RecordingLoader loader = new RecordingLoader()) {
			Thread.currentThread().setContextClassLoader(loader);
			for (String prefix : List.of("web\\assets", "..\\outside", "\\web", "web\\",
					"\0web", "web\0", "web/\0/nested", "//web", "///web",
					"C:/web", "C:web", "/C:/web", "/c:web", "../web", "web/../outside")) {
				assertThrows(IllegalArgumentException.class,
						() -> SokletServletContext.builder().classpathResourceRoot(prefix), printable(prefix));
			}
			assertEquals(0, loader.lookups, "Invalid configuration must not query the class loader");
			assertEquals(0, loader.roots, "Invalid configuration must not inspect URL roots");
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void unsafeRequestPathsAreRejectedBeforeLookupEnumerationOrFallback() throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (RecordingLoader loader = new RecordingLoader()) {
			Thread.currentThread().setContextClassLoader(loader);
			for (String prefix : List.of("", "web")) {
				SokletServletContext context = SokletServletContext.builder().classpathResourceRoot(prefix).build();
				for (String path : List.of("/..\\private-config.txt", "/nested\\..\\private-config.txt",
						"/nested/..\\private-config.txt", "/\\server\\share", "/\\?\\C:\\private-config.txt",
						"/nested\\file.txt", "/a\0b", "/../private-config.txt", "/nested/../../private-config.txt",
						"//private", "///private", "/C:/private", "/c:private", "//server/share")) {
					assertRejected(context, path);
				}
			}
			assertEquals(0, loader.lookups, "All three resource APIs must reject before consulting the loader");
			assertEquals(0, loader.roots, "Rejected listings must not enter the URL-root fallback");
			SokletServletContext valid = SokletServletContext.builder().classpathResourceRoot("web").build();
			valid.getResource("/safe.txt");
			valid.getResourceAsStream("/safe.txt");
			valid.getResourcePaths("/safe/");
			assertTrue(loader.lookups >= 4, "The recording loader must observe valid lookups");
			assertTrue(loader.roots > 0, "A valid listing must exercise the URL-root fallback");
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void explodedClasspathPreservesValidPrefixVariantsAndNestedResources() throws Exception {
		Path root = createExplodedClasspath();
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			assertNotNull(loader.getResource("private-config.txt"), "The classpath itself is deliberately wider than web/");
			for (String prefix : List.of("web", "/web", "web/", "/web/", " web ")) {
				SokletServletContext context = SokletServletContext.builder().classpathResourceRoot(prefix).build();
				assertContent(context, "/nested/public.txt", "public");
				assertEquals(Set.of("/nested/"), context.getResourcePaths("/"));
				assertEquals(Set.of("/nested/public.txt"), context.getResourcePaths("/nested/"));
				assertRejected(context, "/../private-config.txt");
				assertRejected(context, "/..\\private-config.txt");
				assertRejected(context, "/nested/..\\..\\private-config.txt");
			}
			SokletServletContext nested = SokletServletContext.builder().classpathResourceRoot("/web/nested").build();
			assertContent(nested, "/public.txt", "public");
			assertEquals(Set.of("/public.txt"), nested.getResourcePaths("/"));
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void anEmptyPrefixCannotTurnListingsIntoAbsoluteFilesystemLookups() throws Exception {
		Path root = createExplodedClasspath();
		Path outside = Files.createDirectories(directory.resolve("outside-classpath"));
		Files.writeString(outside.resolve("outside.txt"), "outside", StandardCharsets.UTF_8);
		String absoluteResourcePath = "/" + outside.toAbsolutePath().toString().replace('\\', '/');
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			for (String prefix : List.of("", "/", " ")) {
				SokletServletContext context = SokletServletContext.builder().classpathResourceRoot(prefix).build();
				assertContent(context, "/web/nested/public.txt", "public");
				assertRejected(context, absoluteResourcePath);
				assertRejected(context, "//private-config.txt");
				assertRejected(context, "/C:/private-config.txt");
				assertRejected(context, "/C:private-config.txt");
			}
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void jarPrefixesAndLiteralPercentNamesRemainUsable() throws Exception {
		Path jar = directory.resolve("classpath.jar");
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
			for (String name : List.of("web/", "web/nested/", "web/nested/public.txt",
					"web/nested/literal%2e%2e.txt", "web/nested/literal%5c.txt", "private-config.txt")) {
				output.putNextEntry(new JarEntry(name));
				if (!name.endsWith("/"))
					output.write("jar content".getBytes(StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			SokletServletContext context = SokletServletContext.builder().classpathResourceRoot("/web/nested/").build();
			for (String path : List.of("/public.txt", "/literal%2e%2e.txt", "/literal%5c.txt"))
				assertContent(context, path, "jar content");
			assertEquals(Set.of("/public.txt", "/literal%2e%2e.txt", "/literal%5c.txt"), context.getResourcePaths("/"));
			assertRejected(context, "/..\\..\\private-config.txt");
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void nativeWindowsSeparatorsCannotEscapeTheConfiguredClasspathPrefix() throws Exception {
		Path root = createExplodedClasspath();
		assertEquals(root.resolve("private-config.txt"), root.resolve("web/..\\private-config.txt").normalize(),
				"This case must exercise native Windows path interpretation");
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			assertNotNull(loader.getResource("private-config.txt"));
			SokletServletContext context = SokletServletContext.builder().classpathResourceRoot("web").build();
			assertContent(context, "/nested/public.txt", "public");
			assertRejected(context, "/..\\private-config.txt");
			assertRejected(context, "/nested/..\\..\\private-config.txt");
		}
		finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	private Path createExplodedClasspath() throws IOException {
		Path root = Files.createDirectories(directory.resolve("classpath"));
		Files.createDirectories(root.resolve("web/nested"));
		Files.writeString(root.resolve("web/nested/public.txt"), "public", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("private-config.txt"), "private", StandardCharsets.UTF_8);
		return root;
	}

	private static void assertRejected(SokletServletContext context, String path) throws Exception {
		assertNull(context.getResource(path), printable(path));
		assertNull(context.getResourceAsStream(path), printable(path));
		assertNull(context.getResourcePaths(path), printable(path));
	}

	private static void assertContent(SokletServletContext context, String path, String expected) throws Exception {
		assertNotNull(context.getResource(path), path);
		try (InputStream stream = context.getResourceAsStream(path)) {
			assertNotNull(stream, path);
			assertEquals(expected, new String(stream.readAllBytes(), StandardCharsets.UTF_8), path);
		}
	}

	private static String printable(String text) {
		return text.replace("\0", "\\0");
	}

	private static final class RecordingLoader extends URLClassLoader {
		private int lookups;
		private int roots;
		private RecordingLoader() { super(new URL[0], null); }
		@Override public URL getResource(String name) { lookups++; return null; }
		@Override public Enumeration<URL> getResources(String name) { lookups++; return Collections.emptyEnumeration(); }
		@Override public URL[] getURLs() { roots++; return super.getURLs(); }
	}
}
