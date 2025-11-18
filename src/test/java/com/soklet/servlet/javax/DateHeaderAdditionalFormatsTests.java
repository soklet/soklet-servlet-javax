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

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/*
 * Extra date header parsing tests for additional HTTP-date formats.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class DateHeaderAdditionalFormatsTests {
	@Test
	public void parsesRfc1036Format() {
		String rfc1036 = "Sun, 06 Nov 94 08:49:37 GMT"; // from RFC 7231 Appendix A
		Request request = Request.with(HttpMethod.GET, "/h")
				.headers(Map.of("X-Test-Date", Set.of(rfc1036)))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();
		long expectedMillis = parseRfc1036Millis(rfc1036);
		Assertions.assertEquals(expectedMillis, http.getDateHeader("X-Test-Date"));
	}

	@Test
	public void parsesAsctimeFormat() {
		String asctime = "Sun Nov  6 08:49:37 1994";
		Request request = Request.with(HttpMethod.GET, "/h")
				.headers(Map.of("X-Test-Date", Set.of(asctime)))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();
		long expectedMillis = parseAsctimeMillis(asctime);
		Assertions.assertEquals(expectedMillis, http.getDateHeader("X-Test-Date"));
	}

	private long parseRfc1036Millis(String s) {
		DateTimeFormatter fmt = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern("EEE, dd MMM ")
				.appendValueReduced(ChronoField.YEAR, 2, 2, 1900)
				.appendPattern(" HH:mm:ss zzz")
				.toFormatter(Locale.US)
				.withZone(ZoneOffset.UTC);
		return Instant.from(fmt.parse(s)).toEpochMilli();
	}

	private long parseAsctimeMillis(String s) {
		DateTimeFormatter fmt = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern("EEE MMM")
				.appendLiteral(' ')
				.optionalStart().appendLiteral(' ').optionalEnd()
				.appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
				.appendPattern(" HH:mm:ss yyyy")
				.toFormatter(Locale.US)
				.withZone(ZoneOffset.UTC);
		return Instant.from(fmt.parse(s)).toEpochMilli();
	}
}
