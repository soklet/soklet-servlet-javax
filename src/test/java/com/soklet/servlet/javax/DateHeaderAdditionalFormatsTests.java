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
	public void rfc850CenturyUsesFullFiftyYearBoundary() {
		Instant now = Instant.parse("2026-09-13T12:34:56Z");
		assertRfc850Date("2076-09-13T12:34:55Z", now);
		assertRfc850Date("2076-09-13T12:34:56Z", now);
		assertRfc850Date("1976-09-13T12:34:57Z", now);
		assertRfc850Date("1977-01-01T00:00:00Z", now);
		assertRfc850Date("2040-01-01T00:00:00Z", now);
		assertRfc850Date("2126-01-01T00:00:00Z", Instant.parse("2077-01-01T00:00:00Z"));
		assertRfc850Date("2000-02-29T00:00:00Z", Instant.parse("2050-02-28T00:00:00Z"));
	}

	@Test
	public void rfc850RejectsInvalidCalendarDatesWeekdaysAndTrailingInput() {
		Instant now = Instant.parse("2026-09-13T12:34:56Z");
		for (String value : new String[]{"Sunday, 31-Nov-94 08:49:37 GMT",
				"Monday, 06-Nov-94 08:49:37 GMT", "Sunday, 06-Nov-94 08:49:37 GMT extra",
				"Sunday, 06-Nov-1994 08:49:37 GMT", "Sunday, 06-Nov-94 25:49:37 GMT"}) {
			Assertions.assertThrows(java.time.DateTimeException.class,
					() -> SokletHttpServletRequest.parseRfc850Date(value, now), value);
		}
	}

	private void assertRfc850Date(String expected, Instant now) {
		Instant instant = Instant.parse(expected);
		String header = DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.US)
				.withZone(ZoneOffset.UTC).format(instant);
		Assertions.assertEquals(instant, SokletHttpServletRequest.parseRfc850Date(header, now), header);
	}

	@Test
	public void parsesRfc850Format() {
		String rfc850 = "Sunday, 06-Nov-94 08:49:37 GMT"; // RFC 9110 section 5.6.7
		Request request = Request.withPath(HttpMethod.GET, "/h")
				.headers(Map.of("X-Test-Date", Set.of(rfc850)))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();
		long expectedMillis = Instant.parse("1994-11-06T08:49:37Z").toEpochMilli();
		Assertions.assertEquals(expectedMillis, http.getDateHeader("X-Test-Date"));
	}

	@Test
	public void parsesAsctimeFormat() {
		String asctime = "Sun Nov  6 08:49:37 1994";
		Request request = Request.withPath(HttpMethod.GET, "/h")
				.headers(Map.of("X-Test-Date", Set.of(asctime)))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();
		long expectedMillis = parseAsctimeMillis(asctime);
		Assertions.assertEquals(expectedMillis, http.getDateHeader("X-Test-Date"));
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
