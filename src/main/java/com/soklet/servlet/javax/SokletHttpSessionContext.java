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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Soklet integration implementation of {@link HttpSessionContext}.
 * <p>
 * <strong>Note that {@link HttpSessionContext} is deprecated, so this implementation is as well.</strong>
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@Immutable
@Deprecated
public final class SokletHttpSessionContext implements HttpSessionContext {
	@Nonnull
	public static SokletHttpSessionContext withDefaults() {
		return new SokletHttpSessionContext();
	}

	private SokletHttpSessionContext() {
		// Nothing to do
	}

	// Implementation of HttpSessionContext methods below:

	@Override
	@Nullable
	@Deprecated
	public HttpSession getSession(@Nullable String sessionId) {
		// Per spec
		return null;
	}

	@Override
	@Nonnull
	@Deprecated
	public Enumeration<String> getIds() {
		// Per spec
		return Collections.emptyEnumeration();
	}
}