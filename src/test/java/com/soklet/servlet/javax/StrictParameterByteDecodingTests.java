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
import com.soklet.exception.IllegalRequestException;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Strict byte decoding must reject malformed input without rejecting valid replacement characters. */
public class StrictParameterByteDecodingTests {
	private static final List<Function<HttpServletRequest, Object>> PARAMETER_ACCESSORS = List.of(
			request -> request.getParameter("query"), request -> request.getParameterValues("valid"),
			HttpServletRequest::getParameterNames, HttpServletRequest::getParameterMap);

	@Test
	public void malformedUtf8FailsEveryInitialParameterAccessAndCachesOneFailure() throws Exception {
		for (String malformed : List.of("bad=%C3%28", "bad=%FF", "bad=%C0%AF", "bad=%E2%82",
				"bad=%ED%A0%80", "%FF=bad")) {
			for (Function<HttpServletRequest, Object> firstAccess : PARAMETER_ACCESSORS) {
				HttpServletRequest servlet = utf8Form(("valid=ok&" + malformed).getBytes(StandardCharsets.US_ASCII));
				IllegalRequestException failure = assertThrows(IllegalRequestException.class, () -> firstAccess.apply(servlet));
				for (Function<HttpServletRequest, Object> access : PARAMETER_ACCESSORS)
					assertSame(failure, assertThrows(IllegalRequestException.class, () -> access.apply(servlet)));
				assertSame(failure, assertThrows(IllegalRequestException.class, () -> servlet.getParameter("valid")));
				assertEquals(-1, servlet.getInputStream().read(), "Failed form parsing still consumes the body");
			}
		}
	}

	@Test
	public void malformedLiteralBytesAndUnmappableCharsetBytesAreRejected() {
		HttpServletRequest malformedLiteral = utf8Form(new byte[]{'q', '=', (byte) 0xFF});
		assertThrows(IllegalRequestException.class, malformedLiteral::getParameterMap);
		Request request = Request.withPath(HttpMethod.POST, "/")
				.headers(Map.of("Content-Type", List.of("application/x-www-form-urlencoded; charset=US-ASCII")))
				.body("q=%E9".getBytes(StandardCharsets.US_ASCII)).build();
		HttpServletRequest unmappable = SokletHttpServletRequest.fromRequest(request);
		assertThrows(IllegalRequestException.class, unmappable::getParameterMap);
	}

	@Test
	public void validReplacementCharactersLiteralBytesAndFormSyntaxRemainIntact() throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("q=%EF%BF%BD&q=\uFFFD&q=%ef%bf%bd&name=\u00E9&name=%C3%A9&plus=a%2Bb+c&mixed=%C3"
				.getBytes(StandardCharsets.UTF_8));
		body.write(0xA9); // Literal and escaped bytes jointly form the UTF-8 sequence for é.
		HttpServletRequest servlet = utf8Form(body.toByteArray());
		assertArrayEquals(new String[]{"\uFFFD", "\uFFFD", "\uFFFD"}, servlet.getParameterValues("q"));
		assertArrayEquals(new String[]{"é", "é"}, servlet.getParameterValues("name"));
		assertEquals("a+b c", servlet.getParameter("plus"));
		assertEquals("é", servlet.getParameter("mixed"));
		assertEquals("kept", servlet.getParameter("query"));
	}

	@Test
	public void queryUtf8RemainsIndependentOfLiteralAndEscapedLatin1FormBytes() {
		Request request = Request.withRawUrl(HttpMethod.POST, "/?q=%C3%A9&q=%EF%BF%BD")
				.headers(Map.of("Content-Type", List.of("application/x-www-form-urlencoded; charset=ISO-8859-1")))
				.body("q=%E9&q=é&plus=a%2Bb+c".getBytes(StandardCharsets.ISO_8859_1)).build();
		HttpServletRequest servlet = SokletHttpServletRequest.fromRequest(request);
		assertArrayEquals(new String[]{"é", "\uFFFD", "é", "é"}, servlet.getParameterValues("q"));
		assertEquals("a+b c", servlet.getParameter("plus"));
	}

	@Test
	public void rawQueryRejectsUnpairedSurrogatesAndPreservesSupplementaryUnicode() {
		for (String malformed : List.of("\uD800", "\uDC00", "\uD800x"))
			assertThrows(IllegalRequestException.class,
					() -> Request.withRawUrl(HttpMethod.GET, "/?q=" + malformed).build());
		HttpServletRequest servlet = SokletHttpServletRequest.fromRequest(
				Request.withRawUrl(HttpMethod.GET, "/?q=\uD83D\uDE80&q=%F0%9F%9A%80").build());
		assertArrayEquals(new String[]{"\uD83D\uDE80", "\uD83D\uDE80"}, servlet.getParameterValues("q"));
	}

	private static HttpServletRequest utf8Form(byte[] body) {
		return SokletHttpServletRequest.fromRequest(Request.withRawUrl(HttpMethod.POST, "/?query=kept")
				.headers(Map.of("Content-Type", List.of("application/x-www-form-urlencoded; charset=UTF-8")))
				.body(body).build());
	}
}
