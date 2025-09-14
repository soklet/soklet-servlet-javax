/*
 * Copyright 2024-2025 Revetware LLC.
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

import com.soklet.core.HttpMethod;
import com.soklet.core.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
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
	public void intAndRfc1123DateHeaders() {
		String rfc1123 = "Sun, 06 Nov 1994 08:49:37 GMT";
		Request request = Request.with(HttpMethod.GET, "/h")
				.headers(Map.of(
						"X-Test-Int", Set.of("123"),
						"X-Test-Date", Set.of(rfc1123)
				))
				.build();

		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request);

		Assertions.assertEquals(123, httpServletRequest.getIntHeader("X-Test-Int"), "Int header parse failed");

		long expectedMillis = ZonedDateTime.parse(rfc1123, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();

		Assertions.assertEquals(expectedMillis, httpServletRequest.getDateHeader("X-Test-Date"), "Date header parse failed");
	}

	@Test
	public void invalidDateHeaderThrows() {
		Request request = Request.with(HttpMethod.GET, "/h")
				.headers(Map.of(
						"X-Test-Date", Set.of("not a date")
				))
				.build();

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request);
			httpServletRequest.getDateHeader("X-Test-Date");
		});
	}
}
