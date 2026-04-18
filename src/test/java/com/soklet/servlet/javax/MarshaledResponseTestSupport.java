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
import com.soklet.MarshaledResponseBody;
import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Test helpers for Servlet responses converted to Soklet marshaled responses.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class MarshaledResponseTestSupport {
	private MarshaledResponseTestSupport() {
		// Utility class
	}

	@NonNull
	public static byte[] bodyBytesOrEmpty(@NonNull MarshaledResponse marshaledResponse) {
		MarshaledResponseBody body = marshaledResponse.getBody().orElse(null);

		if (body == null)
			return new byte[0];

		if (body instanceof MarshaledResponseBody.Bytes bytes)
			return bytes.getBytes();

		throw new AssertionError("Expected byte-array-backed response body, but got: " + body.getClass().getName());
	}
}
