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

import com.soklet.core.HttpMethod;
import com.soklet.core.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

/*
 * Verify async methods are unsupported and auth methods signal unsupported behavior.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class AsyncAndAuthUnsupportedTests {
	@Test
	public void startAsyncThrows() {
		Request req = Request.with(HttpMethod.GET, "/x").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req);
		Assertions.assertThrows(IllegalStateException.class, () -> http.startAsync());
	}

	@Test
	public void authenticateThrowsServletException() {
		Request req = Request.with(HttpMethod.GET, "/x").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req);
		Assertions.assertThrows(ServletException.class, () -> http.authenticate(SokletHttpServletResponse.withRequestPath("/x")));
	}
}
