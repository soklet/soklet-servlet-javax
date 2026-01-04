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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link ServletOutputStream}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class SokletServletOutputStream extends ServletOutputStream {
	@NonNull
	private final OutputStream outputStream;
	@NonNull
	private final BiConsumer<SokletServletOutputStream, Integer> onWriteOccurred;
	@NonNull
	private final Consumer<SokletServletOutputStream> onWriteFinalized;
	@NonNull
	private Boolean writeFinalized;

	@NonNull
	public static Builder withOutputStream(@NonNull OutputStream outputStream) {
		return new Builder(outputStream);
	}

	private SokletServletOutputStream(@NonNull Builder builder) {
		requireNonNull(builder);
		requireNonNull(builder.outputStream);

		this.outputStream = builder.outputStream;
		this.onWriteOccurred = builder.onWriteOccurred != null ? builder.onWriteOccurred : (ignored1, ignored2) -> {};
		this.onWriteFinalized = builder.onWriteFinalized != null ? builder.onWriteFinalized : (ignored) -> {};
		this.writeFinalized = false;
	}

	/**
	 * Builder used to construct instances of {@link SokletServletOutputStream}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://www.revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@NonNull
		private OutputStream outputStream;
		@Nullable
		private BiConsumer<SokletServletOutputStream, Integer> onWriteOccurred;
		@Nullable
		private Consumer<SokletServletOutputStream> onWriteFinalized;

		@NonNull
		private Builder(@NonNull OutputStream outputStream) {
			requireNonNull(outputStream);
			this.outputStream = outputStream;
		}

		@NonNull
		public Builder outputStream(@NonNull OutputStream outputStream) {
			requireNonNull(outputStream);
			this.outputStream = outputStream;
			return this;
		}

		@NonNull
		public Builder onWriteOccurred(@Nullable BiConsumer<SokletServletOutputStream, Integer> onWriteOccurred) {
			this.onWriteOccurred = onWriteOccurred;
			return this;
		}

		@NonNull
		public Builder onWriteFinalized(@Nullable Consumer<SokletServletOutputStream> onWriteFinalized) {
			this.onWriteFinalized = onWriteFinalized;
			return this;
		}

		@NonNull
		public SokletServletOutputStream build() {
			return new SokletServletOutputStream(this);
		}
	}

	@NonNull
	private OutputStream getOutputStream() {
		return this.outputStream;
	}

	@NonNull
	private BiConsumer<SokletServletOutputStream, Integer> getOnWriteOccurred() {
		return this.onWriteOccurred;
	}

	@NonNull
	private Consumer<SokletServletOutputStream> getOnWriteFinalized() {
		return this.onWriteFinalized;
	}

	@NonNull
	private Boolean getWriteFinalized() {
		return this.writeFinalized;
	}

	private void setWriteFinalized(@NonNull Boolean writeFinalized) {
		requireNonNull(writeFinalized);
		this.writeFinalized = writeFinalized;
	}

// Implementation of ServletOutputStream methods below:

	@Override
	public void write(int b) throws IOException {
		getOutputStream().write(b);
		getOnWriteOccurred().accept(this, b);
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void flush() throws IOException {
		super.flush();
		getOutputStream().flush();

		if (!getWriteFinalized()) {
			setWriteFinalized(true);
			getOnWriteFinalized().accept(this);
		}
	}

	@Override
	public void close() throws IOException {
		super.close();
		getOutputStream().close();

		if (!getWriteFinalized()) {
			setWriteFinalized(true);
			getOnWriteFinalized().accept(this);
		}
	}

	@Override
	public void setWriteListener(@NonNull WriteListener writeListener) {
		requireNonNull(writeListener);
		throw new IllegalStateException(format("%s functionality is not supported", WriteListener.class.getSimpleName()));
	}
}
