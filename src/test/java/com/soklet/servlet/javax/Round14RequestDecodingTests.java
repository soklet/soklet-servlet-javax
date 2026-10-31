package com.soklet.servlet.javax;

import com.soklet.HttpMethod;
import com.soklet.Request;
import com.soklet.exception.IllegalRequestException;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class Round14RequestDecodingTests {
	@Test void unsupportedDeclarationsRemainVisibleAndDoNotConsumeReaderState() throws Exception {
		for (String[] declaration : List.of(
				new String[]{"text/plain; charset=not-a-real-charset", "not-a-real-charset"},
				new String[]{"text/plain; note=\"ignore;charset=UTF-16\"; charset=\"not\\-a-real-charset\"", "not-a-real-charset"},
				new String[]{"text/plain; charset='not-a-real-charset'", "not-a-real-charset"},
				new String[]{"text/plain; charset=\"bad charset\"", "bad charset"},
				new String[]{"text/plain; charset=bad\\; note=retained", "bad\\"},
				new String[]{"text/plain; charset=\"\"", ""})) {
			byte[] body = "é".getBytes(StandardCharsets.UTF_8);
			var request = servletRequest(declaration[0], body, SokletServletContext.builder().build());
			assertEquals(declaration[1], request.getCharacterEncoding(), declaration[0]);
			for (int attempt = 0; attempt < 2; attempt++) {
				var failure = assertThrows(UnsupportedEncodingException.class, request::getReader, declaration[0]);
				assertEquals(declaration[1], failure.getMessage());
				assertInstanceOf(UnsupportedCharsetException.class, failure.getCause());
			}
			assertArrayEquals(body, request.getInputStream().readAllBytes(), declaration[0]);
		}
	}

	@Test void explicitEncodingCanReplaceOrClearAnUnsupportedDeclaration() throws Exception {
		var context = SokletServletContext.builder().build();
		context.setRequestCharacterEncoding("UTF-16LE");
		byte[] body = "é".getBytes(StandardCharsets.UTF_8);
		var request = servletRequest("text/plain; charset=not-a-real-charset", body, context);
		assertThrows(UnsupportedEncodingException.class, request::getReader);
		assertThrows(UnsupportedEncodingException.class, () -> request.setCharacterEncoding("another-unsupported-name"));
		assertEquals("not-a-real-charset", request.getCharacterEncoding());
		request.setCharacterEncoding("UTF-8");
		assertEquals("é", request.getReader().readLine());
		assertEquals("UTF-8", request.getCharacterEncoding());

		context.setRequestCharacterEncoding("UTF-8");
		var cleared = servletRequest("text/plain; charset=not-a-real-charset", body, context);
		cleared.setCharacterEncoding(null);
		assertEquals("é", cleared.getReader().readLine());
		assertEquals("UTF-8", cleared.getCharacterEncoding());

		var defaulted = servletRequest("text/plain", "é".getBytes(StandardCharsets.ISO_8859_1),
				SokletServletContext.builder().build());
		assertNull(defaulted.getCharacterEncoding());
		assertEquals("é", defaulted.getReader().readLine());
		assertEquals("ISO-8859-1", defaulted.getCharacterEncoding());
	}

	@Test void consumedReaderAndFormEncodingStayConsistentWithTheirReportedEncoding() throws Exception {
		var context = SokletServletContext.builder().build();
		context.setRequestCharacterEncoding("UTF-8");
		var request = servletRequest("text/plain", "é".getBytes(StandardCharsets.UTF_8), context);
		var reader = request.getReader();
		context.setRequestCharacterEncoding("UTF-16LE");
		assertEquals("UTF-8", request.getCharacterEncoding());
		assertEquals("é", reader.readLine());

		context.setRequestCharacterEncoding("UTF-8");
		var form = servletRequest("application/x-www-form-urlencoded", "q=%C3%A9".getBytes(StandardCharsets.US_ASCII), context);
		assertEquals("é", form.getParameter("q"));
		context.setRequestCharacterEncoding("UTF-16LE");
		assertEquals("UTF-8", form.getCharacterEncoding());
		assertEquals(-1, form.getInputStream().read());
	}

	@Test void unsupportedFormEncodingFailsOnceWhileQueryDecodingRemainsIndependent() throws Exception {
		var request = Request.withRawUrl(HttpMethod.GET, "/?q=%C3%A9")
				.headers(Map.of("Content-Type", List.of("text/plain; charset=not-a-real-charset"))).build();
		assertEquals("é", SokletHttpServletRequest.fromRequest(request).getParameter("q"));
		var form = servletRequest("application/x-www-form-urlencoded; charset=not-a-real-charset",
				"q=%C3%A9".getBytes(StandardCharsets.US_ASCII), SokletServletContext.builder().build());
		IllegalRequestException failure = assertThrows(IllegalRequestException.class, form::getParameterMap);
		assertSame(failure, assertThrows(IllegalRequestException.class, () -> form.getParameter("q")));
		assertEquals(-1, form.getInputStream().read());
	}

	@Test void supportedEscapedCharsetsAndDuplicateRejectionRemainIntact() throws Exception {
		var request = servletRequest("text/plain; note=\"a;charset=bogus\"; charset=\"UTF\\-8\"",
				"é".getBytes(StandardCharsets.UTF_8), SokletServletContext.builder().build());
		assertEquals("UTF-8", request.getCharacterEncoding());
		assertEquals("é", request.getReader().readLine());
		assertThrows(IllegalRequestException.class, () -> servletRequest(
				"text/plain; charset=not-a-real-charset; charset=UTF-8", new byte[0], SokletServletContext.builder().build()));
	}

	@Test void dateHeadersRejectImpossibleDatesAndPreserveValidFormatsAndEpochFallback() {
		for (String invalid : List.of("Sat, 31 Feb 2026 12:34:56 GMT", "Sat Feb 31 12:34:56 2026",
				"Mon, 06 Nov 1994 08:49:37 GMT", "Mon, 06 Nov 1994 24:00:00 GMT"))
			assertThrows(IllegalArgumentException.class, () -> dateRequest(invalid).getDateHeader("If-Modified-Since"), invalid);
		for (String valid : List.of("Sun, 06 Nov 1994 08:49:37 GMT", "Sunday, 06-Nov-94 08:49:37 GMT", "Sun Nov  6 08:49:37 1994"))
			assertEquals(Instant.parse("1994-11-06T08:49:37Z").toEpochMilli(), dateRequest(valid).getDateHeader("If-Modified-Since"), valid);
		assertEquals(0, dateRequest("0").getDateHeader("If-Modified-Since"));
	}

	@Test void dateHeaderParsingRemainsStrictWithHostLocaleData() throws Exception {
		String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Djava.locale.providers=HOST", "-cp", classpath, HostDateParsingProbe.class.getName())
				.redirectErrorStream(true).start();
		try {
			assertTrue(process.waitFor(10, TimeUnit.SECONDS), "HTTP-date parser probe did not finish");
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(0, process.exitValue(), output);
			assertEquals(List.of("PARSED: 1793277296000", "PARSED: 1793277296000", "PARSED: 1793277296000", "INVALID: rejected"),
					output.lines().filter(line -> line.startsWith("PARSED: ") || line.startsWith("INVALID: ")).toList(), output);
		} finally {
			if (process.isAlive()) {
				process.destroyForcibly();
				assertTrue(process.waitFor(10, TimeUnit.SECONDS), "HTTP-date parser probe did not stop");
			}
		}
	}

	public static final class HostDateParsingProbe {
		public static void main(String[] args) {
			for (String date : List.of("Thu, 29 Oct 2026 12:34:56 GMT", "Thursday, 29-Oct-26 12:34:56 GMT", "Thu Oct 29 12:34:56 2026"))
				System.out.println("PARSED: " + dateRequest(date).getDateHeader("If-Modified-Since"));
			try {
				dateRequest("Sat Feb 31 12:34:56 2026").getDateHeader("If-Modified-Since");
				throw new AssertionError("An impossible date was accepted");
			} catch (IllegalArgumentException expected) {
				System.out.println("INVALID: rejected");
			}
		}
	}

	private static SokletHttpServletRequest servletRequest(String contentType, byte[] body, SokletServletContext context) {
		return SokletHttpServletRequest.withRequest(Request.withPath(HttpMethod.POST, "/")
				.headers(Map.of("Content-Type", List.of(contentType))).body(body).build()).servletContext(context).build();
	}

	private static SokletHttpServletRequest dateRequest(String date) {
		return SokletHttpServletRequest.fromRequest(Request.withPath(HttpMethod.GET, "/")
				.headers(Map.of("If-Modified-Since", List.of(date))).build());
	}
}
