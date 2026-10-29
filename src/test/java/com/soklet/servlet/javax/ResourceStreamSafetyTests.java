package com.soklet.servlet.javax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourceStreamSafetyTests {
	@TempDir Path directory;

	@Test void filesystemStreamsNeverExposeDirectoriesOrInvalidPaths() throws Exception {
		Files.createDirectory(directory.resolve("dir"));
		Files.writeString(directory.resolve("dir/secret.txt"), "content");
		var context = SokletServletContext.builder().filesystemResourceRoot(directory).build();
		for (String path : new String[] {"/", "/dir", "/dir/", "/a\0b"})
			assertNull(context.getResourceAsStream(path), path);
		assertNull(context.getResourcePaths("/a\0b"));
		assertNull(context.getResource("/a\0b"));
		assertFileContent(context);
	}

	@Test void explodedClasspathStreamsNeverExposeDirectories() throws Exception {
		Files.createDirectories(directory.resolve("web/dir"));
		Files.writeString(directory.resolve("web/dir/secret.txt"), "content");
		assertClasspathResources(directory);
	}

	@Test void jarClasspathStreamsNeverExposeDirectories() throws Exception {
		Path jar = directory.resolve("resources.jar");
		try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
			for (String name : new String[] {"web/", "web/dir/", "web/dir/secret.txt"}) {
				output.putNextEntry(new JarEntry(name));
				if (name.endsWith(".txt")) output.write("content".getBytes(StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}
		assertClasspathResources(jar);
	}

	@Test void rejectedJarDirectoriesCloseAnUncachedOwnedJar() throws Exception {
		try (var jar = trackingJar()) {
			var context = contextFor(jar, "web/dir/", false);
			assertNull(context.getResourceAsStream("/dir"));
			assertEquals(1, jar.closes);
			assertFalse(jar.cacheSettings.isEmpty());
			assertTrue(jar.cacheSettings.stream().noneMatch(Boolean::booleanValue));
		}
	}

	@Test void closingJarResourceStreamClosesItsUncachedOwnedJarEvenWhenEntryCloseFails() throws Exception {
		for (boolean failClose : new boolean[] {false, true}) {
		try (var jar = trackingJar()) {
			var context = contextFor(jar, "web/dir/secret.txt", false);
			InputStream stream = context.getResourceAsStream("/dir/secret.txt");
			assertNotNull(stream);
			assertEquals("content", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			assertEquals(0, jar.closes);
			jar.failStreamClose = failClose;
			if (failClose)
				assertThrows(IOException.class, stream::close);
			else
				stream.close();
			assertTrue(jar.streamClosed);
			assertEquals(1, jar.closes);
			stream.close();
			assertEquals(1, jar.closes, "The returned stream closes its owned jar once");
			assertTrue(jar.cacheSettings.stream().noneMatch(Boolean::booleanValue));
		}
		}
	}

	@Test void jarLookupAndStreamOpenFailuresCloseTheOwnedJar() throws Exception {
		for (boolean lookupFailure : new boolean[] {false, true}) {
			try (var jar = trackingJar()) {
				jar.failStreamOpen = !lookupFailure;
				var context = contextFor(jar, "web/dir/secret.txt", lookupFailure);
				assertNull(context.getResourceAsStream("/dir/secret.txt"));
				assertEquals(1, jar.closes);
				assertTrue(jar.cacheSettings.stream().noneMatch(Boolean::booleanValue));
			}
		}
	}

	private TrackingJarFile trackingJar() throws Exception {
		Path file = Files.createTempFile(directory, "owned-", ".jar");
		try (var output = new JarOutputStream(Files.newOutputStream(file))) {
			output.putNextEntry(new JarEntry("web/dir/"));
			output.closeEntry();
			output.putNextEntry(new JarEntry("web/dir/secret.txt"));
			output.write("content".getBytes(StandardCharsets.UTF_8));
			output.closeEntry();
		}
		return new TrackingJarFile(file);
	}

	private SokletServletContext contextFor(TrackingJarFile jar, String entryName, boolean failLookup) throws Exception {
		URL resource = new URL(null, "jar:" + Path.of(jar.getName()).toUri() + "!/" + entryName,
				new URLStreamHandler() {
					@Override protected URLConnection openConnection(URL url) throws IOException {
						return new JarURLConnection(url) {
							@Override public void connect() {}
							@Override public JarFile getJarFile() {
								jar.cacheSettings.add(getUseCaches());
								return jar;
							}
							@Override public JarEntry getJarEntry() throws IOException {
								JarFile opened = getJarFile();
								if (failLookup) throw new IOException("entry lookup failed");
								return opened.getJarEntry(entryName);
							}
							@Override public InputStream getInputStream() throws IOException {
								return getJarFile().getInputStream(getJarEntry());
							}
						};
					}
				});
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
				@Override public URL getResource(String name) { return resource; }
			});
			return SokletServletContext.builder().classpathResourceRoot("web").build();
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	private static final class TrackingJarFile extends JarFile {
		private final List<Boolean> cacheSettings = new ArrayList<>();
		private int closes;
		private boolean streamClosed;
		private boolean failStreamOpen;
		private boolean failStreamClose;
		private TrackingJarFile(Path path) throws IOException { super(path.toFile()); }
		@Override public InputStream getInputStream(ZipEntry entry) throws IOException {
			if (failStreamOpen) throw new IOException("entry open failed");
			return new FilterInputStream(super.getInputStream(entry)) {
				@Override public void close() throws IOException {
					streamClosed = true;
					super.close();
					if (failStreamClose) throw new IOException("entry close failed");
				}
			};
		}
		@Override public void close() throws IOException { closes++; super.close(); }
	}

	private void assertClasspathResources(Path source) throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (var loader = new URLClassLoader(new URL[] {source.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			var context = SokletServletContext.builder().classpathResourceRoot("web").build();
			for (String path : new String[] {"/", "/dir", "/dir/", "/a\0b"})
				assertNull(context.getResourceAsStream(path), path);
			assertFileContent(context);
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	private void assertFileContent(SokletServletContext context) throws Exception {
		try (var stream = context.getResourceAsStream("/dir/secret.txt")) {
			assertNotNull(stream);
			assertEquals("content", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
