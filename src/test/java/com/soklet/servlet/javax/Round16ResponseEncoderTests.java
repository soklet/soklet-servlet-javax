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

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.soklet.servlet.javax.MarshaledResponseTestSupport.bodyBytesOrEmpty;
import static org.junit.jupiter.api.Assertions.*;

public class Round16ResponseEncoderTests {
	private static final String DECODER_ONLY_CHARSET = "ISO-2022-CN";

	@Test
	public void decoderOnlyCharsetFailuresPreserveResponseAndRemainChecked() {
		assertFalse(Charset.forName(DECODER_ONLY_CHARSET).canEncode());
		for (String source : List.of("explicit", "content-type", "context")) {
			var context = SokletServletContext.fromDefaults();
			if (source.equals("context"))
				context.setResponseCharacterEncoding(DECODER_ONLY_CHARSET);
			var response = SokletHttpServletResponse.fromRawPath("/x", context);
			response.setStatus(202);
			response.setHeader("X-Preserved", "original");
			response.setContentType(source.equals("content-type")
					? "text/plain; charset=" + DECODER_ONLY_CHARSET : "text/plain");
			if (source.equals("explicit"))
				response.setCharacterEncoding(DECODER_ONLY_CHARSET);
			String contentType = response.getContentType();

			for (int attempt = 0; attempt < 2; attempt++) {
				UnsupportedEncodingException failure = assertThrows(UnsupportedEncodingException.class,
						response::getWriter, source);
				assertEquals(DECODER_ONLY_CHARSET, failure.getMessage());
				assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
				assertEquals(DECODER_ONLY_CHARSET, response.getCharacterEncoding());
				assertEquals(contentType, response.getContentType());
				assertEquals("original", response.getHeader("X-Preserved"));
				assertEquals(202, response.getStatus());
				assertFalse(response.isCommitted());
				assertArrayEquals(new byte[0], bodyBytesOrEmpty(response.toMarshaledResponse()));
			}
			response.setBufferSize(7);
			assertEquals(7, response.getBufferSize());
		}
	}

	@Test
	public void supportedOverrideAfterFailureAcquiresOneWriterAndKeepsNormalLocks() throws Exception {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.setContentType("text/plain; charset=" + DECODER_ONLY_CHARSET);
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		response.setCharacterEncoding("UTF-8");
		var writer = response.getWriter();
		response.setCharacterEncoding("UTF-16");
		assertEquals("UTF-8", response.getCharacterEncoding());
		assertEquals("text/plain; charset=UTF-8", response.getContentType());
		assertSame(writer, response.getWriter());
		assertThrows(IllegalStateException.class, response::getOutputStream);
		writer.write("café");
		assertArrayEquals("café".getBytes(StandardCharsets.UTF_8), bodyBytesOrEmpty(response.toMarshaledResponse()));
		assertFalse(response.isCommitted());
		response.setStatus(201);
		writer.flush();
		assertTrue(response.isCommitted());
		response.setStatus(500);
		assertEquals(201, response.getStatus());
	}

	@Test
	public void rawOutputRemainsAvailableAfterRepeatedWriterFailures() throws Exception {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		String contentType = "application/octet-stream; charset=" + DECODER_ONLY_CHARSET;
		response.setContentType(contentType);
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		var output = response.getOutputStream();
		byte[] bytes = new byte[]{0, 42, (byte) 0xFF};
		output.write(bytes);
		assertSame(output, response.getOutputStream());
		assertThrows(IllegalStateException.class, response::getWriter);
		assertEquals(contentType, response.getContentType());
		assertArrayEquals(bytes, bodyBytesOrEmpty(response.toMarshaledResponse()));
		output.flush();
		assertTrue(response.isCommitted());
	}

	@Test
	public void failedAcquisitionDoesNotFreezeContextDefault() throws Exception {
		var context = SokletServletContext.fromDefaults();
		context.setResponseCharacterEncoding(DECODER_ONLY_CHARSET);
		var response = SokletHttpServletResponse.fromRawPath("/x", context);
		response.setContentType("text/plain");
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		context.setResponseCharacterEncoding("UTF-8");
		response.getWriter().write("é");
		assertEquals("UTF-8", response.getCharacterEncoding());
		assertEquals("text/plain; charset=UTF-8", response.getContentType());
		assertArrayEquals("é".getBytes(StandardCharsets.UTF_8), bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	public void failedWriterDoesNotUndoPriorCommitment() throws Exception {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.setCharacterEncoding(DECODER_ONLY_CHARSET);
		response.flushBuffer();
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		assertTrue(response.isCommitted());
		response.setCharacterEncoding("UTF-8");
		assertEquals(DECODER_ONLY_CHARSET, response.getCharacterEncoding());
		response.getOutputStream().write(42);
		assertArrayEquals(new byte[]{42}, bodyBytesOrEmpty(response.toMarshaledResponse()));
		assertThrows(IllegalStateException.class, response::reset);
	}
}
