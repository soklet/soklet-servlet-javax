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
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write("é"); // non-ASCII
		MarshaledResponse mr = resp.toMarshaledResponse();
		byte[] body = mr.getBody().orElse(new byte[]{});
		Assertions.assertArrayEquals("é".getBytes(Charset.forName("UTF-8")), body);
	}

	@Test
	public void changingEncodingAfterGetWriterHasNoEffect() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		var writer = resp.getWriter();
		resp.setCharacterEncoding("UTF-16"); // should be ignored per spec
		writer.write("ok");
		MarshaledResponse mr = resp.toMarshaledResponse();
		byte[] body = mr.getBody().orElse(new byte[]{});
		// Default per servlet spec is ISO-8859-1
		Assertions.assertArrayEquals("ok".getBytes(Charset.forName("ISO-8859-1")), body);
	}

	@Test
	public void contentTypeCharsetAppliedBeforeWriter() throws Exception {
		var resp = SokletHttpServletResponse.withRequestPath("/x");
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
	public void changingContentTypeAfterWriterDoesNotChangeEncoding() throws Exception {
		var resp = SokletHttpServletResponse.withRequestPath("/x");
		// Do NOT set any charset; getWriter() will lock ISO-8859-1
		var w = resp.getWriter();
		// Now try to change to UTF-8—should not affect actual encoding used by writer
		resp.setContentType("text/plain; charset=UTF-8");
		w.write("ok");
		var mr = resp.toMarshaledResponse();

		// Body remains ISO-8859-1
		Assertions.assertArrayEquals("ok".getBytes(Charset.forName("ISO-8859-1")),
				mr.getBody().orElse(new byte[]{}));
	}
}
