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

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * The set of all possible event payloads emittable by {@link SokletServletPrintWriter}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
public sealed interface SokletServletPrintWriterEvent permits
		SokletServletPrintWriterEvent.CharsWritten,
		SokletServletPrintWriterEvent.CharWritten,
		SokletServletPrintWriterEvent.StringWritten,
		SokletServletPrintWriterEvent.ValuePrinted,
		SokletServletPrintWriterEvent.ValueWithNewlinePrinted,
		SokletServletPrintWriterEvent.NewlinePrinted,
		SokletServletPrintWriterEvent.PrintfPerformed,
		SokletServletPrintWriterEvent.FormatPerformed,
		SokletServletPrintWriterEvent.CharSequenceAppended,
		SokletServletPrintWriterEvent.CharAppended {

	/**
	 * Event emitted when a {@code char[]} is written to the {@link SokletServletPrintWriter}.
	 */
	record CharsWritten(
			@NonNull char[] chars,
			int offset,
			int length
	) implements SokletServletPrintWriterEvent {
		public CharsWritten {
			requireNonNull(chars);
		}
	}

	/**
	 * Event emitted when an {@code int} (character) is written to the {@link SokletServletPrintWriter}.
	 */
	record CharWritten(
			int ch
	) implements SokletServletPrintWriterEvent {}

	/**
	 * Event emitted when a {@link String} is written to the {@link SokletServletPrintWriter}.
	 */
	record StringWritten(
			@NonNull String string,
			int offset,
			int length
	) implements SokletServletPrintWriterEvent {
		public StringWritten {
			requireNonNull(string);
		}
	}

	/**
	 * Event emitted when an {@link Object} value is written to the {@link SokletServletPrintWriter}.
	 */
	record ValuePrinted(
			@Nullable Object value
	) implements SokletServletPrintWriterEvent {}

	/**
	 * Event emitted when an {@link Object} value with a newline is written to the {@link SokletServletPrintWriter}.
	 */
	record ValueWithNewlinePrinted(
			@Nullable Object value
	) implements SokletServletPrintWriterEvent {}

	/**
	 * Event emitted when a newline is written to the {@link SokletServletPrintWriter}.
	 */
	record NewlinePrinted() implements SokletServletPrintWriterEvent {}

	/**
	 * Event emitted when the result of a "printf" operation is written to the {@link SokletServletPrintWriter}.
	 */
	record PrintfPerformed(
			@Nullable Locale locale,
			@NonNull String format,
			@Nullable Object @NonNull [] args
	) implements SokletServletPrintWriterEvent {
		public PrintfPerformed {
			requireNonNull(format);
			requireNonNull(args);
		}
	}

	/**
	 * Event emitted when the result of a "format" operation is written to the {@link SokletServletPrintWriter}.
	 */
	record FormatPerformed(
			@Nullable Locale locale,
			@NonNull String format,
			@Nullable Object @NonNull [] args
	) implements SokletServletPrintWriterEvent {
		public FormatPerformed {
			requireNonNull(format);
			requireNonNull(args);
		}
	}

	/**
	 * Event emitted when a {@link CharSequence} is appended to the {@link SokletServletPrintWriter}.
	 */
	record CharSequenceAppended(
			@NonNull CharSequence charSequence,
			int start,
			int end
	) implements SokletServletPrintWriterEvent {
		public CharSequenceAppended {
			requireNonNull(charSequence);
		}
	}

	/**
	 * Event emitted when a {@code char} is appended to the {@link SokletServletPrintWriter}.
	 */
	record CharAppended(
			char ch
	) implements SokletServletPrintWriterEvent {
		public CharAppended {}
	}
}
