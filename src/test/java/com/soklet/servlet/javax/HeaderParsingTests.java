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
import com.soklet.Utilities;
import com.soklet.exception.IllegalRequestHeaderException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Collections;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class HeaderParsingTests {
	@Test
	public void wholeAndEmptyPhysicalFieldsRemainVisibleThroughServletHeaderApis() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/h")
				.headers(Utilities.extractHeadersFromRawHeaderLines(List.of(
						"Accept-Encoding: gzip, deflate, br", "Accept-Language: en-US,en;q=0.9",
						"accept-language: fr,de;q=0.5", "X-Blank:", "x-blank:\t "))).build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("gzip, deflate, br", http.getHeader("accept-encoding"));
		Assertions.assertEquals(List.of("gzip, deflate, br"), Collections.list(http.getHeaders("Accept-Encoding")));
		Assertions.assertEquals("en-US,en;q=0.9", http.getHeader("Accept-Language"));
		Assertions.assertEquals(List.of("en-US,en;q=0.9", "fr,de;q=0.5"), Collections.list(http.getHeaders("ACCEPT-LANGUAGE")));
		Assertions.assertEquals("", http.getHeader("X-Blank"));
		Assertions.assertEquals(List.of("", ""), Collections.list(http.getHeaders("x-blank")));
		Assertions.assertNull(http.getHeader("absent"));
		Assertions.assertTrue(Collections.list(http.getHeaders("absent")).isEmpty());
		Assertions.assertEquals(request.getLocales().get(0), http.getLocale());
		Assertions.assertEquals(request.getLocales(), Collections.list(http.getLocales()));
		// Servlet getHeader returns the first occurrence; core's singular accessor rejects repeats.
		Assertions.assertThrows(IllegalRequestHeaderException.class, () -> request.getHeader("Accept-Language"));
		Assertions.assertThrows(IllegalRequestHeaderException.class, () -> request.getHeader("X-Blank"));
	}

	@Test
	public void mapAndPhysicalHeaderConstructionHaveTheSameServletValues() {
		Map<String, List<String>> supplied = Map.of("Accept-Encoding", List.of(" \tgzip, deflate, br \t"), "X-Empty", List.of("\t "));
		Request mapped = Request.withPath(HttpMethod.GET, "/h").headers(supplied).build();
		Request physical = Request.withRawUrl(HttpMethod.GET, "/h")
				.headers(Utilities.extractHeadersFromRawHeaderLines(List.of("Accept-Encoding: \tgzip, deflate, br \t", "X-Empty:\t "))).build();
		for (String name : supplied.keySet()) {
			HttpServletRequest first = SokletHttpServletRequest.withRequest(mapped).build();
			HttpServletRequest second = SokletHttpServletRequest.withRequest(physical).build();
			Assertions.assertEquals(first.getHeader(name), second.getHeader(name));
			Assertions.assertEquals(Collections.list(first.getHeaders(name)), Collections.list(second.getHeaders(name)));
		}
	}

	@Test
	public void intAndRfc1123DateHeaders() {
		String rfc1123 = "Sun, 06 Nov 1994 08:49:37 GMT";
		Request request = Request.withPath(HttpMethod.GET, "/h")
				.headers(Map.of(
						"X-Test-Int", List.of("123"),
						"X-Test-Date", List.of(rfc1123)
				))
				.build();

		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals(123, httpServletRequest.getIntHeader("X-Test-Int"), "Int header parse failed");

		long expectedMillis = ZonedDateTime.parse(rfc1123, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();

		Assertions.assertEquals(expectedMillis, httpServletRequest.getDateHeader("X-Test-Date"), "Date header parse failed");
	}

	@Test
	public void invalidDateHeaderThrows() {
		Request request = Request.withPath(HttpMethod.GET, "/h")
				.headers(Map.of(
						"X-Test-Date", List.of("not a date")
				))
				.build();

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
			httpServletRequest.getDateHeader("X-Test-Date");
		});
	}
}
