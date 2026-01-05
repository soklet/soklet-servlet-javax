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

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;

/*
 * Verify OPTIONS * request URL/URI semantics.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class OptionsStarRequestTests {
	@Test
	public void optionsStarUsesSplatForRequestUrlAndUri() {
		Request request = Request.withRawUrl(HttpMethod.OPTIONS, "*").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("*", httpServletRequest.getRequestURI());
		Assertions.assertEquals("*", httpServletRequest.getRequestURL().toString());
		Assertions.assertNull(httpServletRequest.getQueryString());
	}
}
