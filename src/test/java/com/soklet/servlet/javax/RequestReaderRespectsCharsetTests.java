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

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

/*
 * Verify that HttpServletRequest.getReader() decodes the body using charset from Content-Type.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RequestReaderRespectsCharsetTests {
	@Test
	public void readerUsesCharsetFromContentType() throws Exception {
		String text = "Olá, Soklet!";
		Charset charset = Charset.forName("UTF-8");

		Request req = Request.withPath(HttpMethod.POST, "/echo")
				.headers(Map.of("Content-Type", Set.of("text/plain; charset=UTF-8")))
				.body(text.getBytes(charset))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		String read = http.getReader().readLine();
		Assertions.assertEquals(text, read);
	}
}
