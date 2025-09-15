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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/*
 * Additional tests for date header parsing.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class DateHeaderParsingValidTests {
	@Test
	public void parsesRfc1123() {
		String stamp = DateTimeFormatter.RFC_1123_DATE_TIME
				.withZone(ZoneId.of("GMT"))
				.format(Instant.ofEpochMilli(1_725_000_000_000L));

		Request request = Request.with(HttpMethod.GET, "/h")
				.headers(Map.of("X-Test-Date", Set.of(stamp)))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request);
		long millis = http.getDateHeader("X-Test-Date");
		Assertions.assertEquals(1_725_000_000_000L, millis);
	}

	@Test
	public void parsesEpochMillisAsFallback() {
		Request request = Request.with(HttpMethod.GET, "/h")
				.headers(Map.of("X-Test-Date", Set.of("1725000000000")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request);
		long millis = http.getDateHeader("X-Test-Date");
		Assertions.assertEquals(1_725_000_000_000L, millis);
	}
}
