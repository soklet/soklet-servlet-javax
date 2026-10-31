package com.soklet.servlet.javax;

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DeepReviewRegressionTests {

	@Test void dateHeadersRemainCanonicalWithHostLocaleData() throws Exception {
		String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Djava.locale.providers=HOST", "-cp", classpath, HostDateProbe.class.getName())
				.redirectErrorStream(true).start();
		try {
			assertTrue(process.waitFor(10, TimeUnit.SECONDS), "HTTP-date probe did not finish");
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(0, process.exitValue(), output);
			assertEquals(List.of("DATE: Thu, 29 Oct 2026 12:34:56 GMT", "DATE: Sun, 06 Nov 1994 08:49:37 GMT"),
					output.lines().filter(line -> line.startsWith("DATE: ")).toList(), output);
		} finally {
			if (process.isAlive()) {
				process.destroyForcibly();
				assertTrue(process.waitFor(10, TimeUnit.SECONDS), "HTTP-date probe did not stop");
			}
		}
	}

	public static final class HostDateProbe {
		public static void main(String[] args) {
			var response = SokletHttpServletResponse.fromRawPath("/", SokletServletContext.builder().build());
			response.setDateHeader("Date", Instant.parse("2026-10-29T12:34:56Z").toEpochMilli());
			response.addDateHeader("Date", Instant.parse("1994-11-06T08:49:37Z").toEpochMilli());
			response.getHeaders("Date").forEach(value -> System.out.println("DATE: " + value));
		}
	}

	@Test void malformedQuoteRecoversLaterQuotedCookiesAndSessionId() {
		for (String preference : List.of("\"ok\"", "\"a;b\\\"c\"")) {
			String header = "first=\"before;value\"; bad=\"oops; JSESSIONID=expected; pref=" + preference + "; after=1";
			var request = requestWithCookies(header);
			assertEquals("expected", request.getRequestedSessionId(), header);
			var cookies = cookieValues(request);
			assertEquals("before;value", cookies.get("first"), header);
			assertEquals(preference.equals("\"ok\"") ? "ok" : "a;b\"c", cookies.get("pref"), header);
			assertEquals("1", cookies.get("after"), header);
		}
	}

	@Test void validQuotedCookieDelimitersAndEscapesRemainIntact() {
		var request = requestWithCookies("first=\"a;b\"; JSESSIONID=\"expected\"; pref=\"c\\\"d\\\\e\"");
		assertEquals("expected", request.getRequestedSessionId());
		assertEquals(Map.of("first", "a;b", "JSESSIONID", "expected", "pref", "c\"d\\e"), cookieValues(request));
	}

	private static SokletHttpServletRequest requestWithCookies(String cookieHeader) {
		return SokletHttpServletRequest.withRequest(Request.withPath(HttpMethod.GET, "/")
				.headers(Map.of("Cookie", List.of(cookieHeader))).build()).build();
	}

	private static Map<String, String> cookieValues(SokletHttpServletRequest request) {
		assertNotNull(request.getCookies());
		return Arrays.stream(request.getCookies()).collect(Collectors.toMap(cookie -> cookie.getName(), cookie -> cookie.getValue()));
	}
}
