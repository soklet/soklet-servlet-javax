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

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.soklet.servlet.javax.MarshaledResponseTestSupport.bodyBytesOrEmpty;
import static org.junit.jupiter.api.Assertions.*;

public class ResponseCorrectnessTests {
	private static SokletHttpServletResponse response() {
		return SokletHttpServletResponse.fromRawPath("/original/path", SokletServletContext.fromDefaults());
	}

	private static void oldRepresentation(SokletHttpServletResponse response) {
		response.setContentType("text/html; charset=UTF-8");
		response.setHeader("Content-Length", "100");
		response.setHeader("Content-Encoding", "gzip");
		response.setHeader("Content-Range", "bytes 0-99/200");
		response.setHeader("Content-Language", "fr");
		response.setHeader("Content-Location", "/old");
		response.setHeader("Content-Disposition", "attachment; filename=old.bin");
		response.setHeader("Transfer-Encoding", "chunked");
		response.setHeader("Trailer", "Digest");
		response.setHeader("Content-MD5", "old");
		response.setHeader("Content-Digest", "old");
		response.setHeader("Repr-Digest", "old");
		response.setHeader("Digest", "old");
		response.setHeader("ETag", "\"old\"");
		response.setHeader("Last-Modified", "Sun, 06 Nov 1994 08:49:37 GMT");
		response.setHeader("Accept-Ranges", "bytes");
		response.setHeader("WWW-Authenticate", "Basic realm=\"test\"");
		response.setHeader("Access-Control-Allow-Origin", "https://example.test");
		response.setHeader("X-Custom", "keep");
	}

	private static void assertRepresentationRemoved(SokletHttpServletResponse response) {
		for (String name : List.of("Content-Length", "Content-Encoding", "Content-Range",
				"Content-Language", "Content-Location", "Content-Disposition", "Content-MD5", "Content-Digest",
				"Transfer-Encoding", "Trailer",
				"Repr-Digest", "Digest", "ETag", "Last-Modified", "Accept-Ranges"))
			assertNull(response.getHeader(name), name);
		assertEquals("Basic realm=\"test\"", response.getHeader("WWW-Authenticate"));
		assertEquals("https://example.test", response.getHeader("Access-Control-Allow-Origin"));
		assertEquals("keep", response.getHeader("X-Custom"));
	}

	@Test
	public void generatedErrorReplacesUnsafeTypeAndStaleRepresentation() throws Exception {
		var response = response();
		oldRepresentation(response);
		response.getWriter().write("discard");
		String message = "<script>alert('test')</script>";
		response.sendError(400, message);
		assertEquals("text/plain; charset=UTF-8", response.getContentType());
		assertRepresentationRemoved(response);
		assertArrayEquals(message.getBytes(StandardCharsets.UTF_8), bodyBytesOrEmpty(response.toMarshaledResponse()));
		assertTrue(response.isCommitted());
	}

	@Test
	public void generatedDefaultErrorReplacesOldRepresentation() throws Exception {
		var response = response();
		oldRepresentation(response);
		response.sendError(404);
		assertEquals("text/plain; charset=UTF-8", response.getContentType());
		assertRepresentationRemoved(response);
		assertArrayEquals("Not Found".getBytes(StandardCharsets.UTF_8), bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	public void clearingRedirectDiscardsOldRepresentation() throws Exception {
		var response = response();
		oldRepresentation(response);
		response.getWriter().write("discard");
		response.sendRedirect("/new");
		assertNull(response.getContentType());
		assertRepresentationRemoved(response);
		assertEquals("http://localhost/new", response.getHeader("Location"));
		assertArrayEquals(new byte[0], bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	public void resetBufferResetsStatefulEncoderWithoutReplacingWriter() throws Exception {
		assertResetWriterBytes("ISO-2022-JP", "あ", "い");
	}

	@Test
	public void resetBufferRestoresByteOrderMark() throws Exception {
		assertResetWriterBytes("UTF-16", "old", "new");
	}

	@Test
	public void resetBufferDiscardsPendingSurrogate() throws Exception {
		assertResetWriterBytes("UTF-8", "\uD83D", "\uDE00!");
	}

	private static void assertResetWriterBytes(String encoding, String before, String after) throws Exception {
		var response = response();
		response.setCharacterEncoding(encoding);
		response.setContentType("text/plain");
		response.setStatus(202);
		PrintWriter writer = response.getWriter();
		writer.write(before);
		response.resetBuffer();
		assertSame(writer, response.getWriter());
		response.setCharacterEncoding("ISO-8859-1");
		assertEquals(Charset.forName(encoding).name(), response.getCharacterEncoding());
		assertEquals(202, response.getStatus());
		assertEquals("text/plain; charset=" + Charset.forName(encoding).name(), response.getContentType());
		writer.write(after);
		var fresh = response();
		fresh.setCharacterEncoding(encoding);
		fresh.getWriter().write(after);
		assertArrayEquals(bodyBytesOrEmpty(fresh.toMarshaledResponse()), bodyBytesOrEmpty(response.toMarshaledResponse()));
		assertFalse(response.isCommitted());
		assertThrows(IllegalStateException.class, response::getOutputStream);
	}

	@Test
	public void resetBufferRetainsAcquiredOutputStream() throws Exception {
		var response = response();
		var stream = response.getOutputStream();
		stream.write(1);
		response.resetBuffer();
		assertSame(stream, response.getOutputStream());
		stream.write(2);
		assertArrayEquals(new byte[]{2}, bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	public void redirectDotSegmentsPreserveEmptySegments() throws Exception {
		String[][] cases = {
				{"/a//../b", "/a/b"},
				{"/a///../b", "/a//b"},
				{"/a//..", "/a/"},
				{"/a//./b", "/a//b"},
				{"/a//b/../c", "/a//c"},
				{"/a/../../b", "/b"},
				{"/a/%2e%2e/b", "/a/%2e%2e/b"},
				{"/a//../b?q=../x#../y", "/a/b?q=../x#../y"}
		};
		for (String[] testCase : cases) {
			var response = response();
			response.sendRedirect(testCase[0]);
			assertEquals("http://localhost" + testCase[1], response.getHeader("Location"), testCase[0]);
		}
	}
}
