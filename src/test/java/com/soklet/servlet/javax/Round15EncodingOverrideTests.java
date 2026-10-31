package com.soklet.servlet.javax;

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Round15EncodingOverrideTests {
	@Test void rawStreamAcquisitionDoesNotLockCharacterEncoding() throws Exception {
		byte[] body = "é".getBytes(StandardCharsets.UTF_8);
		var request = request("text/plain; charset=not-a-real-charset", body);
		var stream = request.getInputStream();
		request.setCharacterEncoding("UTF-8");
		assertEquals("UTF-8", request.getCharacterEncoding());
		assertSame(stream, request.getInputStream());
		assertArrayEquals(body, stream.readAllBytes());
		assertThrows(IllegalStateException.class, request::getReader,
				"Changing metadata must retain reader/stream exclusivity");
	}

	@Test void rawReadsPreserveSupportedOverridesAndValidateLaterEncodingNames() throws Exception {
		var request = request("text/plain", new byte[]{'a', 'b'});
		var stream = request.getInputStream();
		assertEquals('a', stream.read());
		request.setCharacterEncoding("UTF-8");
		assertThrows(UnsupportedEncodingException.class, () -> request.setCharacterEncoding("not-a-real-charset"));
		assertEquals("UTF-8", request.getCharacterEncoding());
		request.setCharacterEncoding((String) null);
		assertNull(request.getCharacterEncoding());
		assertEquals('b', stream.read());
		assertEquals(-1, stream.read());
	}

	@Test void readerAndParameterConsumptionStillLockEncoding() throws Exception {
		var readerRequest = request("text/plain; charset=UTF-8", "é".getBytes(StandardCharsets.UTF_8));
		var reader = readerRequest.getReader();
		readerRequest.setCharacterEncoding("ISO-8859-1");
		assertEquals("UTF-8", readerRequest.getCharacterEncoding());
		assertEquals("é", reader.readLine());

		var form = request("application/x-www-form-urlencoded; charset=UTF-8", "q=%C3%A9".getBytes(StandardCharsets.US_ASCII));
		assertEquals("é", form.getParameter("q"));
		form.getInputStream();
		form.setCharacterEncoding("ISO-8859-1");
		assertEquals("UTF-8", form.getCharacterEncoding());
		assertEquals("é", form.getParameter("q"));
	}

	private static SokletHttpServletRequest request(String contentType, byte[] body) {
		return SokletHttpServletRequest.fromRequest(Request.withPath(HttpMethod.POST, "/")
				.headers(Map.of("Content-Type", List.of(contentType))).body(body).build());
	}
}
