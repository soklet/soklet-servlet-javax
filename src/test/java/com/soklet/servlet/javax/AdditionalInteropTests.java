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

import com.soklet.HttpMethod;
import com.soklet.MarshaledResponse;
import com.soklet.Request;
import com.soklet.Utilities.EffectiveOriginResolver.TrustPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/*
 * Additional tests to cover servlet interop odds-and-ends semantics that were not exercised elsewhere.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class AdditionalInteropTests {
	@Test
	public void encodeUrlPassThrough() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		String in = "http://example.com/a?b=c";
		Assertions.assertEquals(in, resp.encodeURL(in));
		Assertions.assertEquals(in, resp.encodeRedirectURL(in));
		Assertions.assertNull(resp.encodeURL(null));
		Assertions.assertNull(resp.encodeRedirectURL(null));
	}

	@Test
	public void containsHeaderIsCaseInsensitive() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.addHeader("X-Test", "1");
		Assertions.assertTrue(resp.containsHeader("x-test"));
		Assertions.assertTrue(resp.containsHeader("X-TEST"));
		Assertions.assertFalse(resp.containsHeader("missing"));
		Assertions.assertFalse(resp.containsHeader(null));
	}

	@Test
	public void changeSessionIdBehavior() {
		HttpServletRequest http = SokletHttpServletRequest.withRequest(Request.withPath(HttpMethod.GET, "/x").build()).build();
		// Without a session, changeSessionId should throw
		Assertions.assertThrows(IllegalStateException.class, http::changeSessionId);

		// With a session, id should change and be reflected on the session
		HttpSession session = http.getSession(true);
		String originalId = session.getId();
		String newId = http.changeSessionId();
		Assertions.assertNotEquals(originalId, newId);
		Assertions.assertEquals(newId, session.getId());
	}

	@Test
	public void serverNameAndPortComeFromHostHeader() {
		Request req = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("Host", Set.of("example.com:8443")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertEquals("example.com", http.getServerName());
		Assertions.assertEquals(8443, http.getServerPort());
		Assertions.assertTrue(http.getRequestURL().toString().startsWith("http://example.com:8443/p"));

		Request reqDefault = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("Host", Set.of("example.com")))
				.build();
		HttpServletRequest httpDefault = SokletHttpServletRequest.withRequest(reqDefault).build();
		Assertions.assertEquals("example.com", httpDefault.getServerName());
		Assertions.assertEquals(80, httpDefault.getServerPort());
	}

	@Test
	public void getServerPortDefaultsFromScheme() {
		// https without explicit port -> 443
		Request httpsReq = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("https"), "Host", Set.of("example.com")))
				.build();
		HttpServletRequest https = SokletHttpServletRequest.withRequest(httpsReq)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals(443, https.getServerPort());

		// http without explicit port -> 80
		Request httpReq = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("http"), "Host", Set.of("example.com")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(httpReq)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals(80, http.getServerPort());
	}

	@Test
	public void getServerPortDefaultsWithoutHostHeader() {
		Request httpsReq = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("https")))
				.build();
		HttpServletRequest https = SokletHttpServletRequest.withRequest(httpsReq)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals(443, https.getServerPort());
	}

	@Test
	public void getServerPortUsesLocalPortWhenHostOmitsPort() {
		Request req = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("Host", Set.of("example.com")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.port(8081)
				.build();
		Assertions.assertEquals(8081, http.getServerPort());
	}

	@Test
	public void headerNamesAreUnmodifiable() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.addHeader("A", "1");
		var names = resp.getHeaderNames();
		Assertions.assertThrows(UnsupportedOperationException.class, () -> names.add("B"));
	}

	@Test
	public void flushBufferCommitsResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.flushBuffer();
		Assertions.assertTrue(resp.isCommitted());
		Assertions.assertDoesNotThrow(() -> resp.setHeader("X", "1"));
		Assertions.assertNull(resp.getHeader("X"));
	}

	@Test
	public void outputStreamFlushCommitsResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.getOutputStream().flush();
		Assertions.assertTrue(resp.isCommitted());
		Assertions.assertDoesNotThrow(() -> resp.setHeader("X", "1"));
		Assertions.assertNull(resp.getHeader("X"));
	}

	@Test
	public void writerFlushCommitsResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.getWriter().flush();
		Assertions.assertTrue(resp.isCommitted());
		Assertions.assertDoesNotThrow(() -> resp.setHeader("X", "1"));
		Assertions.assertNull(resp.getHeader("X"));
	}

	@Test
	public void flushBufferAfterCommitIsAllowed() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.flushBuffer();
		Assertions.assertDoesNotThrow(resp::flushBuffer);
	}

	@Test
	public void contentTypeReflectsHeaderValue() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setHeader("Content-Type", "text/plain");
		Assertions.assertEquals("text/plain", resp.getContentType());

		SokletHttpServletResponse respWithAdd = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		respWithAdd.addHeader("Content-Type", "application/json");
		Assertions.assertEquals("application/json", respWithAdd.getContentType());
	}

	@Test
	public void contentLengthHeadersAreSet() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setContentLength(42);
		resp.setContentLengthLong(43L);
		MarshaledResponse mr = resp.toMarshaledResponse();
		// The later call should have replaced the value
		Assertions.assertEquals(Set.of("43"), mr.getHeaders().get("Content-Length"));
	}

	@Test
	public void getResourcePathsReturnsNullForInvalidPath() {
		var ctx = SokletServletContext.withDefaults();
		Assertions.assertNull(ctx.getResourcePaths("relative"));
	}

	@Test
	public void getResourceThrowsOnInvalidPath() {
		var ctx = SokletServletContext.withDefaults();
		Assertions.assertThrows(MalformedURLException.class, () -> ctx.getResource("relative"));
	}

	@Test
	public void getResourcePathsReturnsNullWhenMissing() {
		var ctx = SokletServletContext.withDefaults();
		Assertions.assertNull(ctx.getResourcePaths("/definitely-not-present"));
	}

	@Test
	public void getContextReturnsNullForOtherContext() {
		var ctx = SokletServletContext.withDefaults();
		Assertions.assertNull(ctx.getContext("/other"));
	}

	@Test
	public void requestDispatcherIsNullWhenUnsupported() {
		var ctx = SokletServletContext.withDefaults();
		Assertions.assertNull(ctx.getRequestDispatcher("/x"));
	}

	@Test
	public void requestDispatcherFromRequestIsNullWhenUnsupported() {
		Request req = Request.withPath(HttpMethod.GET, "/p").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertNull(http.getRequestDispatcher("/x"));
	}

	@Test
	public void filesystemResourceRootResolvesResources(@TempDir Path tempDir) throws Exception {
		Path rootFile = tempDir.resolve("root.txt");
		Path dir = Files.createDirectories(tempDir.resolve("dir"));
		Path childFile = dir.resolve("child.txt");

		Files.writeString(rootFile, "root", StandardCharsets.UTF_8);
		Files.writeString(childFile, "child", StandardCharsets.UTF_8);

		SokletServletContext ctx = SokletServletContext.builder()
				.filesystemResourceRoot(tempDir)
				.build();
		Set<String> rootPaths = ctx.getResourcePaths("/");
		Assertions.assertTrue(rootPaths.contains("/root.txt"));
		Assertions.assertTrue(rootPaths.contains("/dir/"));
		Assertions.assertNotNull(ctx.getResource("/root.txt"));
		Assertions.assertNotNull(ctx.getResource("/dir/child.txt"));
		Assertions.assertNull(ctx.getResource("/../root.txt"));

		Set<String> dirPaths = ctx.getResourcePaths("/dir/");
		Assertions.assertTrue(dirPaths.contains("/dir/child.txt"));

		try (InputStream in = ctx.getResourceAsStream("/root.txt")) {
			Assertions.assertNotNull(in);
		}
	}

	@Test
	public void classpathResourceRootResolvesResources() throws Exception {
		SokletServletContext ctx = SokletServletContext.builder()
				.classpathResourceRoot("testdata")
				.build();
		Assertions.assertNotNull(ctx.getResource("/hello.txt"));

		Set<String> rootPaths = ctx.getResourcePaths("/");
		Assertions.assertTrue(rootPaths.contains("/hello.txt"));

		try (InputStream in = ctx.getResourceAsStream("/hello.txt")) {
			Assertions.assertNotNull(in);
		}
	}

	@Test
	public void classpathResourceRootListsJarEntriesWithoutDirectoryEntry(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("test.jar");

		try (OutputStream out = Files.newOutputStream(jarPath);
				 JarOutputStream jar = new JarOutputStream(out)) {
			JarEntry entry = new JarEntry("root/child.txt");
			jar.putNextEntry(entry);
			jar.write("child".getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}

		ClassLoader previous = Thread.currentThread().getContextClassLoader();

		try (URLClassLoader loader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, previous)) {
			Thread.currentThread().setContextClassLoader(loader);

			SokletServletContext ctx = SokletServletContext.builder()
					.classpathResourceRoot("root")
					.build();

			Set<String> rootPaths = ctx.getResourcePaths("/");
			Assertions.assertNotNull(rootPaths);
			Assertions.assertTrue(rootPaths.contains("/child.txt"));
			Assertions.assertNotNull(ctx.getResource("/child.txt"));
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	public void pathTranslatedIsNullWithoutFilesystemMapping() {
		Request req = Request.withPath(HttpMethod.GET, "/p").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertNull(http.getPathTranslated());
	}

	@Test
	public void localPortDefaultsToZeroWhenUnset() {
		Request req = Request.withPath(HttpMethod.GET, "/p").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertEquals(0, http.getLocalPort());
	}

	@Test
	public void forwardedProtoControlsSchemeAndIsSecure() {
		var req = Request.withPath(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("https"), "Host", Set.of("example.com")))
				.build();
		var http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("https", http.getScheme());
		Assertions.assertTrue(http.isSecure());
	}

	@Test
	public void responseLocaleDefaultsToSystemLocale() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		Assertions.assertEquals(Locale.getDefault(), resp.getLocale());
	}

	@Test
	public void responseLocaleSetsContentLanguageHeader() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setLocale(Locale.CANADA_FRENCH);
		MarshaledResponse mr = resp.toMarshaledResponse();
		Assertions.assertEquals(Set.of("fr-CA"), mr.getHeaders().get("Content-Language"));
	}
}
