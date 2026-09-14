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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classpath resource listing is a union, including JARs without directory entries.
 */
@Timeout(10)
public class ContextResourceUnionTests {
	@TempDir
	Path temporaryDirectory;

	@Test
	public void reportsItsDeclaredServletApiVersion() {
		SokletServletContext context = SokletServletContext.fromDefaults();
		assertEquals(4, context.getMajorVersion());
		assertEquals(0, context.getMinorVersion());
		assertEquals(4, context.getEffectiveMajorVersion());
		assertEquals(0, context.getEffectiveMinorVersion());
	}

	@Test
	public void mixedExplicitAndImplicitDirectoriesAreUnionedInEitherClasspathOrder() throws Exception {
		Path explicit = jar("explicit.jar", true, "web/assets/explicit.txt");
		Path implicit = jar("implicit.jar", false, "web/assets/implicit.txt", "web/assets/nested/child.txt");
		for (boolean explicitFirst : new boolean[]{true, false}) {
			URL[] urls = explicitFirst
					? new URL[]{explicit.toUri().toURL(), implicit.toUri().toURL()}
					: new URL[]{implicit.toUri().toURL(), explicit.toUri().toURL()};
			try (URLClassLoader loader = new URLClassLoader(urls, null)) {
				assertListing(loader);
			}
		}
	}

	@Test
	public void implicitResourcesInParentLoaderAreIncluded() throws Exception {
		Path explicit = jar("child.jar", true, "web/assets/explicit.txt");
		Path implicit = jar("parent.jar", false, "web/assets/implicit.txt", "web/assets/nested/child.txt");
		try (URLClassLoader parent = new URLClassLoader(new URL[]{implicit.toUri().toURL()}, null);
				 URLClassLoader child = new URLClassLoader(new URL[]{explicit.toUri().toURL()}, parent)) {
			assertListing(child);
		}
	}

	@Test
	public void unusableClasspathRootDoesNotDiscardValidResources() throws Exception {
		Path valid = jar("valid.jar", true, "web/assets/explicit.txt");
		Path unusable = temporaryDirectory.resolve("not-an-archive.jar");
		Files.writeString(unusable, "not a ZIP archive", StandardCharsets.US_ASCII);
		try (URLClassLoader loader = new URLClassLoader(
				new URL[]{valid.toUri().toURL(), unusable.toUri().toURL()}, null)) {
			ClassLoader original = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(loader);
				SokletServletContext context = SokletServletContext.builder().classpathResourceRoot("web").build();
				assertEquals(Set.of("/assets/explicit.txt"), context.getResourcePaths("/assets/"));
			} finally {
				Thread.currentThread().setContextClassLoader(original);
			}
		}
	}

	@Test
	public void implicitOnlyDirectoryRemainsDiscoverableAndResultsDeduplicate() throws Exception {
		Path first = jar("first.jar", false, "web/assets/shared.txt");
		Path second = jar("second.jar", false, "web/assets/shared.txt");
		try (URLClassLoader loader = new URLClassLoader(new URL[]{first.toUri().toURL(), second.toUri().toURL()}, null)) {
			ClassLoader original = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(loader);
				SokletServletContext context = SokletServletContext.builder().classpathResourceRoot("web").build();
				assertEquals(Set.of("/assets/shared.txt"), context.getResourcePaths("/assets/"));
				assertNull(context.getResourcePaths("/missing/"));
			} finally {
				Thread.currentThread().setContextClassLoader(original);
			}
		}
	}

	private void assertListing(ClassLoader loader) throws IOException {
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(loader);
			SokletServletContext context = SokletServletContext.builder().classpathResourceRoot("web").build();
			assertNotNull(context.getResource("/assets/implicit.txt"));
			assertEquals(Set.of("/assets/explicit.txt", "/assets/implicit.txt", "/assets/nested/"),
					context.getResourcePaths("/assets/"));
			assertEquals(Set.of("/assets/"), context.getResourcePaths("/"));
			assertEquals(Set.of("/assets/nested/child.txt"), context.getResourcePaths("/assets/nested/"));
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private Path jar(String filename, boolean explicitDirectories, String... resources) throws IOException {
		Path path = temporaryDirectory.resolve(filename);
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
			if (explicitDirectories) {
				output.putNextEntry(new JarEntry("web/"));
				output.closeEntry();
				output.putNextEntry(new JarEntry("web/assets/"));
				output.closeEntry();
			}
			for (String resource : resources) {
				output.putNextEntry(new JarEntry(resource));
				output.write("fixture".getBytes(StandardCharsets.US_ASCII));
				output.closeEntry();
			}
		}
		return path;
	}
}
