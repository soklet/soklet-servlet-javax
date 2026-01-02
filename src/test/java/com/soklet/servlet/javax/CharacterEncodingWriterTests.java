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

import com.soklet.MarshaledResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.charset.Charset;
import java.util.Locale;

/*
 * Verify that response writer uses the set character encoding and that changing the encoding
 * after obtaining the writer has no effect (Servlet spec behavior).
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CharacterEncodingWriterTests {
	@Test
	public void writerUsesExplicitEncoding() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write("é"); // non-ASCII
		MarshaledResponse mr = resp.toMarshaledResponse();
		byte[] body = mr.getBody().orElse(new byte[]{});
		Assertions.assertArrayEquals("é".getBytes(Charset.forName("UTF-8")), body);
	}

	@Test
	public void changingEncodingAfterGetWriterHasNoEffect() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		var writer = resp.getWriter();
		String encoding = resp.getCharacterEncoding();
		resp.setCharacterEncoding("UTF-16"); // should be ignored per spec
		writer.write("ok");
		MarshaledResponse mr = resp.toMarshaledResponse();
		byte[] body = mr.getBody().orElse(new byte[]{});
		Assertions.assertArrayEquals("ok".getBytes(Charset.forName(encoding)), body);
	}

	@Test
	public void contentTypeCharsetAppliedBeforeWriter() throws Exception {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setContentType("text/plain; charset=UTF-16");
		var w = resp.getWriter();
		w.write("ok");
		var mr = resp.toMarshaledResponse();

		// Body should be UTF-16 encoded
		Assertions.assertArrayEquals("ok".getBytes(Charset.forName("UTF-16")),
				mr.getBody().orElse(new byte[]{}), "Body is not encoded with UTF-16");

		// Header should include the same charset
		var ct = mr.getHeaders().get("Content-Type").iterator().next();
		Assertions.assertTrue(ct.toLowerCase(Locale.ROOT).contains("charset=utf-16"), "Content-Type header does not signal UTF-16");
	}

	@Test
	public void headerContentTypeCharsetAppliedBeforeWriter() throws Exception {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setHeader("Content-Type", "text/plain; charset=UTF-16");
		var w = resp.getWriter();
		w.write("ok");
		var mr = resp.toMarshaledResponse();

		Assertions.assertArrayEquals("ok".getBytes(Charset.forName("UTF-16")),
				mr.getBody().orElse(new byte[]{}), "Body is not encoded with UTF-16");

		var ct = mr.getHeaders().get("Content-Type").iterator().next();
		Assertions.assertTrue(ct.toLowerCase(Locale.ROOT).contains("charset=utf-16"), "Content-Type header does not signal UTF-16");
	}

	@Test
	public void setCharacterEncodingUpdatesHeaderContentType() throws Exception {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setHeader("Content-Type", "text/plain");
		resp.setCharacterEncoding("UTF-8");
		var w = resp.getWriter();
		w.write("ok");
		var mr = resp.toMarshaledResponse();

		var ct = mr.getHeaders().get("Content-Type").iterator().next();
		Assertions.assertTrue(ct.toLowerCase(Locale.ROOT).contains("charset=utf-8"), "Content-Type header does not include UTF-8");
	}

	@Test
	public void changingContentTypeAfterWriterDoesNotChangeEncoding() throws Exception {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		// Do NOT set any charset; getWriter() will lock the default encoding
		var w = resp.getWriter();
		String encoding = resp.getCharacterEncoding();
		// Now try to change to UTF-8—should not affect actual encoding used by writer
		resp.setContentType("text/plain; charset=UTF-8");
		w.write("ok");
		var mr = resp.toMarshaledResponse();

		// Body remains in the encoding locked at getWriter()
		Assertions.assertArrayEquals("ok".getBytes(Charset.forName(encoding)),
				mr.getBody().orElse(new byte[]{}));
	}

	@Test
	public void invalidSetCharacterEncodingIsIgnored() throws Exception {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setCharacterEncoding("no-such-charset");
		resp.getWriter().write("é");
		MarshaledResponse mr = resp.toMarshaledResponse();

		Assertions.assertArrayEquals("é".getBytes(Charset.forName("UTF-8")),
				mr.getBody().orElse(new byte[]{}));
	}

	@Test
	public void invalidCharsetInContentTypeIsIgnored() throws Exception {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setContentType("text/plain; charset=no-such-charset");
		resp.getWriter().write("ok");
		MarshaledResponse mr = resp.toMarshaledResponse();

		String encoding = resp.getCharacterEncoding();
		Assertions.assertArrayEquals("ok".getBytes(Charset.forName(encoding)),
				mr.getBody().orElse(new byte[]{}));

		var ct = mr.getHeaders().get("Content-Type").iterator().next();
		Assertions.assertTrue(ct.toLowerCase(Locale.ROOT).contains("charset=utf-8"), "Content-Type header does not include UTF-8");
	}

	@Test
	public void setLocaleAppliesDefaultEncodingWhenUnset() {
		var resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setContentType("text/plain");
		resp.setLocale(Locale.US);

		String contentType = resp.getContentType();
		Assertions.assertNotNull(contentType);
		Assertions.assertTrue(contentType.toLowerCase(Locale.ROOT).contains("charset=utf-8"));
	}
}
