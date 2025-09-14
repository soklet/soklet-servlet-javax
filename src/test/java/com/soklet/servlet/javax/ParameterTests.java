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
import javax.servlet.http.HttpServletRequest;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ParameterTests {
	@Test
	public void parameterValuesOnlyForRequestedName() {
		Request request = Request.with(HttpMethod.GET, "/p?one=a&one=b&two=c").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request);

		String[] oneValues = httpServletRequest.getParameterValues("one");
		Assertions.assertArrayEquals(new String[]{"a", "b"}, oneValues);

		String[] twoValues = httpServletRequest.getParameterValues("two");
		Assertions.assertArrayEquals(new String[]{"c"}, twoValues);

		String[] missing = httpServletRequest.getParameterValues("none");
		Assertions.assertNull(missing);
	}
}
