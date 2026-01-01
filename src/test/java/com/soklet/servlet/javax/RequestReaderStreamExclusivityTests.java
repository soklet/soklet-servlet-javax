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
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;

/*
 * Verify request body accessors follow servlet exclusivity rules.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RequestReaderStreamExclusivityTests {
	@Test
	public void inputStreamThenReaderThrows() throws Exception {
		Request req = Request.withPath(HttpMethod.POST, "/x")
				.body("test".getBytes())
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		http.getInputStream();
		Assertions.assertThrows(IllegalStateException.class, http::getReader);
	}

	@Test
	public void readerThenInputStreamThrows() throws Exception {
		Request req = Request.withPath(HttpMethod.POST, "/x")
				.body("test".getBytes())
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		http.getReader();
		Assertions.assertThrows(IllegalStateException.class, http::getInputStream);
	}

	@Test
	public void repeatedGetInputStreamReturnsSameInstance() throws Exception {
		Request req = Request.withPath(HttpMethod.POST, "/x")
				.body("test".getBytes())
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		ServletInputStream first = http.getInputStream();
		ServletInputStream second = http.getInputStream();
		Assertions.assertSame(first, second);
	}

	@Test
	public void repeatedGetReaderReturnsSameInstance() throws Exception {
		Request req = Request.withPath(HttpMethod.POST, "/x")
				.body("test".getBytes())
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		BufferedReader first = http.getReader();
		BufferedReader second = http.getReader();
		Assertions.assertSame(first, second);
	}
}
