package com.soklet.servlet.javax;

import com.soklet.HttpMethod;
import com.soklet.Request;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.net.URL;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Round12RegressionTests {
	@TempDir Path directory;

	@Test void emptyHeadCapturePreservesDeclaredLengthInBothConversions() throws Exception {
		var context = SokletServletContext.builder().build();
		var request = Request.fromPath(HttpMethod.HEAD, "/download");
		var response = SokletHttpServletResponse.fromRequest(request, context);
		response.setContentLengthLong(4096);
		assertTrue(response.toResponse().getBody().isEmpty());
		assertTrue(response.toMarshaledResponse().getBody().isEmpty());
		assertEquals(List.of("4096"), response.toMarshaledResponse().getHeaders().get("Content-Length"));
	}

	@Test void defaultServletHeadPreservesCountedGetLength() throws Exception {
		var context = SokletServletContext.builder().build();
		var request = SokletHttpServletRequest.withRequest(Request.fromPath(HttpMethod.HEAD, "/download"))
				.servletContext(context).build();
		var response = SokletHttpServletResponse.fromHttpServletRequest(request);
		new HttpServlet() {
			@Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
				resp.getOutputStream().write(new byte[4096]);
			}
		}.service(request, response);
		assertTrue(response.toResponse().getBody().isEmpty());
		assertEquals(List.of("4096"), response.toMarshaledResponse().getHeaders().get("Content-Length"));
	}

	@Test void malformedCookieDoesNotSwallowLaterSessionCookie() {
		for (String prefix : new String[] {"bad=a\"", "bad=a\\", "bad=\"unterminated"}) {
			var request = SokletHttpServletRequest.withRequest(Request.withPath(HttpMethod.GET, "/")
					.headers(Map.of("Cookie", List.of(prefix + "; JSESSIONID=expected; other=value"))).build()).build();
			assertEquals("expected", request.getRequestedSessionId(), prefix);
			assertEquals(3, request.getCookies().length, prefix);
		}
	}

	@Test void requestAttributeNamesAreASnapshot() {
		var request = SokletHttpServletRequest.withRequest(Request.fromPath(HttpMethod.GET, "/")).build();
		request.setAttribute("first", 1);
		var names = request.getAttributeNames();
		request.setAttribute("second", 2);
		assertEquals(List.of("first"), java.util.Collections.list(names));
	}

	@Test void classpathFileUrlsAllowUnescapedSpacesAndLocalhost() throws Exception {
		Path file = Files.writeString(directory.resolve("a b.txt"), "content");
		for (URL url : List.of(new URL("file:" + file), new URL("file://localhost" + file.toUri().getRawPath()))) {
			ClassLoader previous = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
					@Override public URL getResource(String name) { return url; }
				});
				var context = SokletServletContext.builder().classpathResourceRoot("web").build();
				try (var stream = context.getResourceAsStream("/a b.txt")) {
					assertNotNull(stream, url.toString());
					assertEquals("content", new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
				}
			} finally {
				Thread.currentThread().setContextClassLoader(previous);
			}
		}
	}
}
