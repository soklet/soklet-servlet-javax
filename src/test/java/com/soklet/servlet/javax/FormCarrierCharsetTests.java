package com.soklet.servlet.javax;

import com.soklet.HttpMethod;
import com.soklet.Request;
import com.soklet.exception.IllegalRequestException;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Form carrier syntax must not be mistaken for bytes inside an encoded character. */
public class FormCarrierCharsetTests {
	private static final List<Function<HttpServletRequest, Object>> PARAMETER_ACCESSORS = List.of(
			request -> request.getParameter("q"), request -> request.getParameterValues("a"),
			HttpServletRequest::getParameterNames, HttpServletRequest::getParameterMap);

	@Test
	public void rawStatefulBytesCannotCreatePhantomParameters() throws Exception {
		for (String name : statefulCharsetNames()) {
			for (int prohibited : List.of(0x1b, 0x0e, 0x0f, 0x80, 0xff)) {
				byte[] body = new byte[]{'a', '=', (byte) prohibited, '&', 's', 'e', 'c', 'r', 'e', 't', '=', 'x'};
				assertRejectedAndCached(body, name);
			}
		}
		for (String name : List.of("ISO-2022-JP", "jis", "csISO2022JP", "cp50220", "cp50221", "windows-iso2022jp")) {
			if (Charset.isSupported(name))
				assertRejectedAndCached("a=Α&secret=x".getBytes(Charset.forName(name)), name);
		}
	}

	@Test
	public void nonAsciiCompatibleComponentsRequireEveryByteToBeEscaped() throws Exception {
		for (String name : List.of("UTF-16", "UTF-16BE", "UTF-16LE", "UnicodeBigUnmarked", "UnicodeLittleUnmarked")) {
			assertRejectedAndCached("a=Α".getBytes(Charset.forName(name)), name);
			// These ASCII carrier bytes otherwise silently decode as unrelated UTF-16 characters.
			assertRejectedAndCached("aa=bb".getBytes(StandardCharsets.US_ASCII), name);
			assertRejectedAndCached("%00%61=+".getBytes(StandardCharsets.US_ASCII), name);
			assertRejectedAndCached("%00%61=%00%62=".getBytes(StandardCharsets.US_ASCII), name);
		}
	}

	@Test
	public void fullyEncodedComponentsPreserveCharsetAndQueryFirstMerge() {
		List<String> names = new ArrayList<>(statefulCharsetNames());
		names.addAll(List.of("UTF-16", "UTF-16BE", "UTF-16LE"));
		for (String name : names) {
			Charset charset = Charset.forName(name);
			if (!charset.canEncode())
				continue;
			String value = List.of("日本語 &+=secret", "한국어 &+=secret", "中文 &+=secret", "Α &+=secret").stream()
					.filter(candidate -> charset.newEncoder().canEncode(candidate)).findFirst().orElseThrow();
			HttpServletRequest request = form(encodedPair("q", value, charset), name);
			assertArrayEquals(new String[]{"é", "second", value}, request.getParameterValues("q"), name);
			assertEquals(1, request.getParameterMap().size(), name);
			String canonical = charset.name().toUpperCase(Locale.ROOT);
			if (canonical.contains("JP") || canonical.startsWith("X-WINDOWS-5022")) {
				String carrier = "a=" + URLEncoder.encode("prefix " + value + " suffix", charset);
				assertEquals("prefix " + value + " suffix",
						form(carrier.getBytes(StandardCharsets.US_ASCII), name).getParameter("a"), name);
			}
		}
		if (Charset.isSupported("ISO-2022-CN") && Charset.isSupported("x-ISO-2022-CN-GB"))
			assertEquals("中文 &+=secret", form(encodedPair("a", "中文 &+=secret",
					Charset.forName("x-ISO-2022-CN-GB")), "ISO-2022-CN").getParameter("a"));
	}

	@Test
	public void asciiCompatibleFormsRetainLiteralMixedAndStatefulAsciiText() {
		for (String name : List.of("UTF-8", "ISO-8859-1", "Shift_JIS")) {
			Charset charset = Charset.forName(name);
			String value = name.equals("Shift_JIS") ? "日本語" : "café";
			HttpServletRequest request = form(("a=" + value).getBytes(charset), name);
			assertEquals(value, request.getParameter("a"), name);
			assertArrayEquals(new String[]{"é", "second"}, request.getParameterValues("q"), name);
		}
		for (byte[] body : List.of(new byte[]{'a', '=', '%', 'C', '3', (byte) 0xa9},
				new byte[]{'a', '=', (byte) 0xc3, '%', 'A', '9'}))
			assertEquals("é", form(body, "UTF-8").getParameter("a"));
		for (String name : statefulCharsetNames())
			assertEquals("ASCII text", form("a=ASCII+text".getBytes(StandardCharsets.US_ASCII), name).getParameter("a"), name);
	}

	@Test
	public void validationUsesContextOrOverrideAndOnlyAppliesToEligibleFormBodies() throws Exception {
		byte[] body = "a=Α&secret=x".getBytes(Charset.forName("ISO-2022-JP"));
		var context = SokletServletContext.fromDefaults();
		context.setRequestCharacterEncoding("ISO-2022-JP");
		var contextual = SokletHttpServletRequest.withRequest(rawRequest(HttpMethod.POST, body,
				"application/x-www-form-urlencoded")).servletContext(context).build();
		assertThrows(IllegalRequestException.class, contextual::getParameterMap);
		var overridden = form(body, "ISO-8859-1");
		overridden.setCharacterEncoding("ISO-2022-JP");
		assertThrows(IllegalRequestException.class, overridden::getParameterMap);
		for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT)) {
			var request = SokletHttpServletRequest.fromRequest(rawRequest(method, body,
					"application/x-www-form-urlencoded; charset=ISO-2022-JP"));
			assertArrayEquals(new String[]{"é", "second"}, request.getParameterValues("q"));
			assertArrayEquals(body, request.getInputStream().readAllBytes());
		}
		var alreadyAcquired = form(body, "ISO-2022-JP");
		var input = alreadyAcquired.getInputStream();
		assertArrayEquals(new String[]{"é", "second"}, alreadyAcquired.getParameterValues("q"));
		assertArrayEquals(body, input.readAllBytes());
	}

	private static void assertRejectedAndCached(byte[] body, String charset) throws Exception {
		for (Function<HttpServletRequest, Object> first : PARAMETER_ACCESSORS) {
			HttpServletRequest request = form(body, charset);
			IllegalRequestException failure = assertThrows(IllegalRequestException.class, () -> first.apply(request), charset);
			assertFalse(failure.getMessage().contains("secret"));
			assertNull(failure.getCause());
			for (Function<HttpServletRequest, Object> access : PARAMETER_ACCESSORS)
				assertSame(failure, assertThrows(IllegalRequestException.class, () -> access.apply(request)), charset);
			assertEquals(-1, request.getInputStream().read(), "Failed form parsing consumes the body");
		}
	}

	private static List<String> statefulCharsetNames() {
		return List.of("ISO-2022-JP", "ISO-2022-JP-2", "ISO-2022-KR", "ISO-2022-CN", "x-ISO-2022-CN-CNS",
				"x-ISO-2022-CN-GB", "x-windows-50220", "x-windows-50221", "x-windows-iso2022jp",
				"iso2022jp", "csISO2022KR", "ISO2022CN", "cp50220", "ms50221").stream().filter(Charset::isSupported).toList();
	}

	private static byte[] encodedPair(String name, String value, Charset charset) {
		return (percentEncodeAll(name.getBytes(charset)) + "=" + percentEncodeAll(value.getBytes(charset)))
				.getBytes(StandardCharsets.US_ASCII);
	}

	private static String percentEncodeAll(byte[] bytes) {
		StringBuilder encoded = new StringBuilder();
		for (byte value : bytes)
			encoded.append('%').append(Character.forDigit((value & 0xff) >>> 4, 16)).append(Character.forDigit(value & 0xf, 16));
		return encoded.toString();
	}

	private static HttpServletRequest form(byte[] body, String charset) {
		return SokletHttpServletRequest.fromRequest(rawRequest(HttpMethod.POST, body,
				"application/x-www-form-urlencoded; charset=" + charset));
	}

	private static Request rawRequest(HttpMethod method, byte[] body, String contentType) {
		return Request.withRawUrl(method, "/form?q=%C3%A9&q=second").headers(Map.of("Content-Type", List.of(contentType)))
				.body(body).build();
	}
}
