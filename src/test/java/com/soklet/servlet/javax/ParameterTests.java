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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ParameterTests {
	@Test
	public void decodedParameterNamesAndEmptyValuesPreserveEveryOccurrence() {
		Request request = Request.withRawUrl(HttpMethod.POST,
				"/p?key%26name=one%3Dtwo&%6bey%26name=one%3Dtwo&empty&empty=&semi=a;b")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("key%26name=one%3Dtwo&empty=&escaped=%26%3D%2B".getBytes(StandardCharsets.US_ASCII)).build();
		HttpServletRequest http = SokletHttpServletRequest.fromRequest(request);
		Assertions.assertArrayEquals(new String[]{"one=two", "one=two", "one=two"}, http.getParameterValues("key&name"));
		Assertions.assertArrayEquals(new String[]{"", "", ""}, http.getParameterMap().get("empty"));
		Assertions.assertEquals("a;b", http.getParameter("semi"));
		Assertions.assertEquals("&=+", http.getParameter("escaped"));
		Assertions.assertEquals(List.of("key&name", "empty", "semi", "escaped"), Collections.list(http.getParameterNames()));
	}

	@Test
	public void malformedFormPercentEscapesNeverExposeAPartialParameterSnapshot() throws Exception {
		for (String malformed : new String[]{"%", "%1", "%GG", "%+1", "%-1", "%١F"}) {
			Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
					.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
					.body(("valid=1&invalid=" + malformed).getBytes(StandardCharsets.UTF_8)).build();
			HttpServletRequest http = SokletHttpServletRequest.fromRequest(request);
			Assertions.assertThrows(com.soklet.exception.IllegalRequestException.class, () -> http.getParameter("query"));
			Assertions.assertEquals(-1, http.getInputStream().read());
			Assertions.assertThrows(com.soklet.exception.IllegalRequestException.class, http::getParameterMap);
			Assertions.assertThrows(com.soklet.exception.IllegalRequestException.class, http::getParameterNames);
			Assertions.assertThrows(com.soklet.exception.IllegalRequestException.class, () -> http.getParameterValues("valid"));
		}
	}

	@Test
	public void parameterValuesOnlyForRequestedName() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/p?one=a&one=b&two=c").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		String[] oneValues = httpServletRequest.getParameterValues("one");
		Assertions.assertArrayEquals(new String[]{"a", "b"}, oneValues);

		String[] twoValues = httpServletRequest.getParameterValues("two");
		Assertions.assertArrayEquals(new String[]{"c"}, twoValues);

		String[] missing = httpServletRequest.getParameterValues("none");
		Assertions.assertNull(missing);
	}

	@Test
	public void parameterNamesPreserveQueryThenFormOrder() {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?b=2&a=1&b=2")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("c=3&a=4&b=5".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		List<String> names = Collections.list(httpServletRequest.getParameterNames());
		Assertions.assertEquals(List.of("b", "a", "c"), names);
	}

	@Test
	public void parameterMapPreservesValueOrder() {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?one=a&one=b&two=z&one=a")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("one=c&one=d&two=y&one=b".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Map<String, String[]> parameterMap = httpServletRequest.getParameterMap();

		Assertions.assertArrayEquals(new String[]{"a", "b", "a", "c", "d", "b"}, parameterMap.get("one"));
		Assertions.assertArrayEquals(new String[]{"z", "y"}, parameterMap.get("two"));
	}

	@Test
	public void formParametersIgnoredAfterInputStream() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		httpServletRequest.getInputStream();

		Assertions.assertEquals("1", httpServletRequest.getParameter("query"));
		Assertions.assertNull(httpServletRequest.getParameter("form"));
		Assertions.assertArrayEquals(new String[]{"1"}, httpServletRequest.getParameterValues("query"));
		Assertions.assertNull(httpServletRequest.getParameterValues("form"));
	}

	@Test
	public void formParametersIgnoredAfterReader() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		httpServletRequest.getReader();

		Assertions.assertNull(httpServletRequest.getParameter("form"));
		Assertions.assertNull(httpServletRequest.getParameterValues("form"));
	}

	@Test
	public void queryParametersDoNotConsumeBodyForInputStream() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
				.headers(Map.of("Content-Type", Set.of("text/plain")))
				.body("body".getBytes(StandardCharsets.UTF_8))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("1", httpServletRequest.getParameter("query"));

		String body = new String(httpServletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		Assertions.assertEquals("body", body);
	}

	@Test
	public void queryParametersDoNotConsumeBodyForReader() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
				.headers(Map.of("Content-Type", Set.of("text/plain")))
				.body("body".getBytes(StandardCharsets.UTF_8))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("1", httpServletRequest.getParameter("query"));

		Assertions.assertEquals('b', httpServletRequest.getReader().read());
	}

	@Test
	public void inputStreamEmptyAfterParameterAccess() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("2", httpServletRequest.getParameter("form"));

		Assertions.assertEquals(-1, httpServletRequest.getInputStream().read());
	}

	@Test
	public void readerEmptyAfterParameterAccess() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("2", httpServletRequest.getParameter("form"));

		Assertions.assertEquals(-1, httpServletRequest.getReader().read());
	}
	@Test
	public void everyParameterApiPreservesDuplicateQueryAndFormOccurrences() {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?one=a&one=a&one=b")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("one=a&one=b".getBytes(StandardCharsets.US_ASCII)).build();
		HttpServletRequest http = SokletHttpServletRequest.fromRequest(request);
		String[] expected = {"a", "a", "b", "a", "b"};
		Assertions.assertEquals("a", http.getParameter("one"));
		Assertions.assertArrayEquals(expected, http.getParameterValues("one"));
		Assertions.assertArrayEquals(expected, http.getParameterMap().get("one"));
		Assertions.assertEquals(List.of("one"), Collections.list(http.getParameterNames()));

		String[] values = http.getParameterValues("one");
		values[0] = "changed";
		http.getParameterMap().get("one")[1] = "changed";
		Assertions.assertArrayEquals(expected, http.getParameterValues("one"));
		Assertions.assertThrows(UnsupportedOperationException.class, () -> http.getParameterMap().clear());
	}

	@Test
	public void everyInitialParameterAccessPopulatesFormBeforeStreamOrReaderAccess() throws Exception {
		for (int firstAccess = 0; firstAccess < 4; firstAccess++) {
			for (boolean reader : new boolean[]{false, true}) {
				Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
						.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
						.body("form=2&form=2".getBytes(StandardCharsets.US_ASCII)).build();
				HttpServletRequest http = SokletHttpServletRequest.fromRequest(request);
				switch (firstAccess) {
					case 0 -> Assertions.assertEquals("1", http.getParameter("query"));
					case 1 -> Assertions.assertArrayEquals(new String[]{"1"}, http.getParameterValues("query"));
					case 2 -> Assertions.assertEquals(List.of("query", "form"), Collections.list(http.getParameterNames()));
					case 3 -> Assertions.assertArrayEquals(new String[]{"1"}, http.getParameterMap().get("query"));
				}
				Assertions.assertEquals(-1, reader ? http.getReader().read() : http.getInputStream().read());
				Assertions.assertArrayEquals(new String[]{"2", "2"}, http.getParameterValues("form"));
			}
		}
	}

	@Test
	public void nonPostFormBodiesRemainReadableAfterParameterAccess() throws Exception {
		for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
			for (boolean reader : new boolean[]{false, true}) {
				Request request = Request.withRawUrl(method, "/p?query=1")
						.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
						.body("form=2".getBytes(StandardCharsets.US_ASCII)).build();
				HttpServletRequest http = SokletHttpServletRequest.fromRequest(request);
				Assertions.assertEquals("1", http.getParameter("query"));
				Assertions.assertNull(http.getParameter("form"));
				Assertions.assertEquals(List.of("query"), Collections.list(http.getParameterNames()));
				Assertions.assertEquals("form=2", reader ? http.getReader().readLine()
						: new String(http.getInputStream().readAllBytes(), StandardCharsets.US_ASCII));
			}
		}
	}

	@Test
	public void parameterDecodingPreservesServletFormSyntaxAndEncoding() throws Exception {
		for (java.nio.charset.Charset charset : new java.nio.charset.Charset[]{StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1}) {
			String encoded = charset.equals(StandardCharsets.UTF_8) ? "%C3%A9" : "%E9";
			Request request = Request.withRawUrl(HttpMethod.POST,
					"/p?name=caf%C3%A9&name=caf%C3%A9&plus=a%2Bb+c&flag&empty=&eq=a=b&escaped=%252B")
					.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
					.body(("name=caf" + encoded + "&name=caf" + encoded).getBytes(StandardCharsets.US_ASCII)).build();
			HttpServletRequest http = SokletHttpServletRequest.fromRequest(request);
			http.setCharacterEncoding(charset.name());
			Assertions.assertArrayEquals(new String[]{"café", "café", "café", "café"}, http.getParameterValues("name"));
			Assertions.assertEquals("a+b c", http.getParameter("plus"));
			Assertions.assertEquals("", http.getParameter("flag"));
			Assertions.assertEquals("", http.getParameter("empty"));
			Assertions.assertEquals("a=b", http.getParameter("eq"));
			Assertions.assertEquals("%2B", http.getParameter("escaped"));
			http.setCharacterEncoding(charset.equals(StandardCharsets.UTF_8) ? "ISO-8859-1" : "UTF-8");
			Assertions.assertArrayEquals(new String[]{"café", "café", "café", "café"}, http.getParameterMap().get("name"));
		}
	}
}
