/*
 * Copyright 2024 Revetware LLC.
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
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
class SokletServletOutputStream extends ServletOutputStream {
	@Nonnull
	private final OutputStream outputStream;
	@Nonnull
	private final Consumer<SokletServletOutputStream> writeOccurredCallback;
	@Nonnull
	private final Consumer<SokletServletOutputStream> writeFinalizedCallback;
	@Nonnull
	private Boolean writeFinalized = false;

	public SokletServletOutputStream(@Nonnull OutputStream outputStream) {
		this(requireNonNull(outputStream), null, null);
	}

	public SokletServletOutputStream(@Nonnull OutputStream outputStream,
																	 @Nullable Consumer<SokletServletOutputStream> writeOccurredCallback,
																	 @Nullable Consumer<SokletServletOutputStream> writeFinalizedCallback) {
		super();
		requireNonNull(outputStream);

		if (writeOccurredCallback == null)
			writeOccurredCallback = (ignored) -> {};

		if (writeFinalizedCallback == null)
			writeFinalizedCallback = (ignored) -> {};

		this.outputStream = outputStream;
		this.writeOccurredCallback = writeOccurredCallback;
		this.writeFinalizedCallback = writeFinalizedCallback;
	}

	@Nonnull
	protected OutputStream getOutputStream() {
		return this.outputStream;
	}

	@Nonnull
	protected Consumer<SokletServletOutputStream> getWriteOccurredCallback() {
		return this.writeOccurredCallback;
	}

	@Nonnull
	protected Consumer<SokletServletOutputStream> getWriteFinalizedCallback() {
		return this.writeFinalizedCallback;
	}

	@Nonnull
	protected Boolean getWriteFinalized() {
		return this.writeFinalized;
	}

	protected void setWriteFinalized(@Nonnull Boolean writeFinalized) {
		requireNonNull(writeFinalized);
		this.writeFinalized = writeFinalized;
	}

// Implementation of ServletOutputStream methods below:

	@Override
	public void write(int b) throws IOException {
		getOutputStream().write(b);
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public boolean isReady() {
		return !getWriteFinalized();
	}

	@Override
	public void flush() throws IOException {
		super.flush();
		getOutputStream().flush();

		if (!getWriteFinalized()) {
			setWriteFinalized(true);
			getWriteFinalizedCallback().accept(this);
		}
	}

	@Override
	public void close() throws IOException {
		super.close();
		getOutputStream().close();

		if (!getWriteFinalized()) {
			setWriteFinalized(true);
			getWriteFinalizedCallback().accept(this);
		}
	}

	@Override
	public void setWriteListener(@Nonnull WriteListener writeListener) {
		requireNonNull(writeListener);
		throw new IllegalStateException(format("%s functionality is not supported", WriteListener.class.getSimpleName()));
	}
}