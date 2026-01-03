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

import com.soklet.MarshaledResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/*
 * Verify sendError emits a default body.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class SendErrorTests {
	@Test
	public void sendErrorUsesReasonPhraseWhenMessageMissing() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.sendError(404);
		MarshaledResponse mr = resp.toMarshaledResponse();

		Assertions.assertArrayEquals("Not Found".getBytes(StandardCharsets.ISO_8859_1),
				mr.getBody().orElse(new byte[]{}));

		String contentType = mr.getHeaders().get("Content-Type").iterator().next();
		Assertions.assertTrue(contentType.toLowerCase(Locale.ROOT).contains("text/plain"));
		Assertions.assertTrue(contentType.toLowerCase(Locale.ROOT).contains("charset=iso-8859-1"));
	}

	@Test
	public void sendErrorUsesProvidedMessage() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.sendError(400, "Bad Request");
		MarshaledResponse mr = resp.toMarshaledResponse();

		Assertions.assertArrayEquals("Bad Request".getBytes(StandardCharsets.ISO_8859_1),
				mr.getBody().orElse(new byte[]{}));

		String contentType = mr.getHeaders().get("Content-Type").iterator().next();
		Assertions.assertTrue(contentType.toLowerCase(Locale.ROOT).contains("text/plain"));
		Assertions.assertTrue(contentType.toLowerCase(Locale.ROOT).contains("charset=iso-8859-1"));
	}
}
