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
import com.soklet.Request;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.soklet.servlet.javax.MarshaledResponseTestSupport.bodyBytesOrEmpty;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for the final release interoperability corrections. */
public class ReleaseCorrectnessTests {
	@Test
	void adapterBuildersAreFinal() {
		for (Class<?> builder : List.of(SokletHttpServletRequest.Builder.class,
				SokletServletContext.Builder.class, SokletServletOutputStream.Builder.class,
				SokletServletPrintWriter.Builder.class))
			assertTrue(java.lang.reflect.Modifier.isFinal(builder.getModifiers()), builder.getName());
	}

	@Test
	void internalMutationHooksArePackagePrivate() throws Exception {
		for (var method : List.of(
				SokletHttpSession.class.getDeclaredMethod("setSessionId", java.util.UUID.class),
				SokletHttpServletResponse.class.getDeclaredMethod("setPrintWriter", SokletServletPrintWriter.class))) {
			int access = method.getModifiers() & (java.lang.reflect.Modifier.PUBLIC
					| java.lang.reflect.Modifier.PROTECTED | java.lang.reflect.Modifier.PRIVATE);
			assertEquals(0, access, method.toString());
			assertThrows(NoSuchMethodException.class, () -> method.getDeclaringClass()
					.getMethod(method.getName(), method.getParameterTypes()));
		}
	}

	@Test
	void servlet4ReservedCookieNamesDoNotDiscardSessionCookie() {
		var request = Request.withPath(HttpMethod.GET, "/")
				.headers(Map.of("Cookie", Set.of("JSESSIONID=abc; path=/legacy; after=kept"))).build();
		var servlet = SokletHttpServletRequest.fromRequest(request);
		assertEquals(List.of("JSESSIONID", "after"),
				Arrays.stream(servlet.getCookies()).map(Cookie::getName).toList());
		assertEquals("abc", servlet.getRequestedSessionId());
	}

	@Test
	void malformedCookiesDoNotDiscardValidNeighbors() {
		Request request = Request.withPath(HttpMethod.GET, "/")
				.headers(Map.of("Cookie", Set.of("before=1; foo@bar=2; prefs[theme]=dark; =empty; pairless; JSESSIONID=abc; after=3")))
				.build();
		var servlet = SokletHttpServletRequest.fromRequest(request);
		Cookie[] cookies = servlet.getCookies();
		assertNotNull(cookies);
		assertEquals(List.of("before", "JSESSIONID", "after"),
				Arrays.stream(cookies).map(Cookie::getName).toList());
		assertEquals("abc", servlet.getRequestedSessionId());
	}

	@Test
	void allRejectedCookiesBehaveLikeNoCookies() {
		var request = Request.withPath(HttpMethod.GET, "/")
				.headers(Map.of("Cookie", Set.of("foo@bar=1; prefs[theme]=dark"))).build();
		assertNull(SokletHttpServletRequest.fromRequest(request).getCookies());
	}

	@Test
	void printlnPrintfAndFormatDoNotCommitAnyOverload() throws Exception {
		List<Consumer<PrintWriter>> operations = List.of(
				PrintWriter::println, writer -> writer.println(true), writer -> writer.println('x'),
				writer -> writer.println(1), writer -> writer.println(1L),
				writer -> writer.println(1F), writer -> writer.println(1D),
				writer -> writer.println(new char[]{'x'}), writer -> writer.println("x"),
				writer -> writer.println((Object) "x"),
				writer -> writer.printf("%s", "x"), writer -> writer.printf(Locale.ROOT, "%s", "x"),
				writer -> writer.format("%s", "x"), writer -> writer.format(Locale.ROOT, "%s", "x"));
		for (Consumer<PrintWriter> operation : operations) {
			var response = newResponse();
			response.setBufferSize(4096);
			operation.accept(response.getWriter());
			assertFalse(response.isCommitted());
			response.setStatus(201);
			response.setHeader("X-Late", "kept");
			response.addCookie(new Cookie("late", "kept"));
			var marshaled = response.toMarshaledResponse();
			assertEquals(201, marshaled.getStatusCode());
			assertEquals(Set.of("kept"), marshaled.getHeaders().get("X-Late"));
			assertEquals(1, marshaled.getCookies().size());
			assertTrue(marshaled.getBodyLength() > 0);
			response.reset();
			assertEquals(200, response.getStatus());
			assertNull(response.getHeader("X-Late"));
			assertTrue(response.toMarshaledResponse().getCookies().isEmpty());
			assertEquals(0L, response.toMarshaledResponse().getBodyLength());
		}
	}

	@Test
	void explicitFlushCloseAndBufferOverflowStillCommit() throws Exception {
		for (boolean close : new boolean[]{false, true}) {
			var response = newResponse();
			var writer = response.getWriter();
			writer.println("x");
			assertFalse(response.isCommitted());
			if (close) writer.close(); else writer.flush();
			assertTrue(response.isCommitted());
			assertThrows(IllegalStateException.class, response::reset);
			assertThrows(IllegalStateException.class, () -> response.sendError(500));
			assertThrows(IllegalStateException.class, () -> response.sendRedirect("/target"));
		}
		var response = newResponse();
		response.setBufferSize(4);
		response.getWriter().printf("%s", "12345");
		assertTrue(response.isCommitted());
	}

	@Test
	void printlnCanBeReplacedWithErrorOrRedirect() throws Exception {
		var error = newResponse();
		error.getWriter().println("discard");
		error.sendError(400, "replacement");
		assertEquals(400, error.getStatus());
		assertEquals("replacement", new String(bodyBytesOrEmpty(error.toMarshaledResponse()), StandardCharsets.ISO_8859_1));
		var redirect = newResponse();
		redirect.getWriter().println("discard");
		redirect.sendRedirect("/target");
		assertEquals(302, redirect.getStatus());
		assertEquals(0L, redirect.toMarshaledResponse().getBodyLength());
	}

	@Test
	void generatedBodylessErrorsNeverCreateRepresentationBodies() throws Exception {
		for (int status : new int[]{100, 199, 204, 304}) {
			for (boolean message : new boolean[]{false, true}) {
				var response = newResponse();
				response.setContentType("text/html");
				response.setContentLength(123);
				response.getWriter().write("discard");
				if (message) response.sendError(status, "must not appear"); else response.sendError(status);
				assertEquals(status, response.getStatus());
				assertTrue(response.isCommitted());
				assertEquals(0L, response.toMarshaledResponse().getBodyLength());
				if (status >= 200) {
					assertTrue(response.toResponse().getBody().isEmpty());
					assertTrue(response.toMarshaledResponse().getBody().isEmpty());
				}
				assertNull(response.getContentType());
				assertNull(response.getHeader("Content-Length"));
			}
		}
	}

	@Test
	void queryEncodingIsIndependentOfBodyAndContextCharsets() throws Exception {
		var context = SokletServletContext.builder().requestCharacterEncoding(StandardCharsets.UTF_16BE).build();
		var request = Request.withRawUrl(HttpMethod.POST, "/?name=%C3%A9&name=%C3%A9")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("name=%E9".getBytes(StandardCharsets.US_ASCII)).build();
		var servlet = SokletHttpServletRequest.withRequest(request).servletContext(context).build();
		servlet.setCharacterEncoding("ISO-8859-1");
		assertArrayEquals(new String[]{"é", "é", "é"}, servlet.getParameterValues("name"));
		assertEquals(Set.of("é"), request.getQueryParameters().get("name"));
	}

	@Test
	void queryUsesUtf8EvenWhenNoBodyEncodingIsSpecified() {
		var servlet = SokletHttpServletRequest.fromRequest(Request.withRawUrl(HttpMethod.GET, "/?q=%C3%A9").build());
		assertNull(servlet.getCharacterEncoding());
		assertEquals("é", servlet.getParameter("q"));
	}

	@Test
	void unspecifiedEncodingAllowsStandardUtf8Guard() throws Exception {
		var context = SokletServletContext.fromDefaults();
		assertNull(context.getRequestCharacterEncoding());
		assertNull(context.getResponseCharacterEncoding());
		var request = Request.withPath(HttpMethod.POST, "/")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("q=%C3%A9".getBytes(StandardCharsets.US_ASCII)).build();
		var servlet = SokletHttpServletRequest.withRequest(request).servletContext(context).build();
		if (servlet.getCharacterEncoding() == null)
			servlet.setCharacterEncoding("UTF-8");
		assertEquals("é", servlet.getParameter("q"));
	}

	@Test
	void unsetBodyEncodingStillFallsBackToLatin1() throws Exception {
		var request = Request.withPath(HttpMethod.POST, "/").body(new byte[]{(byte) 0xE9}).build();
		var servlet = SokletHttpServletRequest.fromRequest(request);
		assertNull(servlet.getCharacterEncoding());
		assertEquals("é", servlet.getReader().readLine());
		var response = newResponse();
		assertEquals("ISO-8859-1", response.getCharacterEncoding());
		response.getWriter().write("é");
		assertArrayEquals(new byte[]{(byte) 0xE9}, bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	void sessionsSnapshotContextTimeoutInSecondsWithoutOverflow() {
		var context = SokletServletContext.builder().sessionTimeout(5).build();
		var original = SokletHttpSession.fromServletContext(context);
		assertEquals(300, original.getMaxInactiveInterval());
		context.setSessionTimeout(2);
		assertEquals(300, original.getMaxInactiveInterval());
		assertEquals(120, SokletHttpSession.fromServletContext(context).getMaxInactiveInterval());
		original.setMaxInactiveInterval(17);
		assertEquals(17, original.getMaxInactiveInterval());
		for (int minutes : new int[]{Integer.MIN_VALUE, -1, 0}) {
			context.setSessionTimeout(minutes);
			assertEquals(0, SokletHttpSession.fromServletContext(context).getMaxInactiveInterval());
		}
		context.setSessionTimeout(Integer.MAX_VALUE);
		assertEquals(Integer.MAX_VALUE, SokletHttpSession.fromServletContext(context).getMaxInactiveInterval());
	}

	private static SokletHttpServletResponse newResponse() {
		return SokletHttpServletResponse.fromRawPath("/", SokletServletContext.fromDefaults());
	}
}
