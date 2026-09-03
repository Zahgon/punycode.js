package be.mathiasbynens.punycode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * A robust Punycode converter that fully complies to
 * <a href="https://tools.ietf.org/html/rfc3492">RFC 3492</a> and
 * <a href="https://tools.ietf.org/html/rfc5891">RFC 5891</a>.
 *
 * <p>{@link #decode(String)} and {@link #encode(String)} convert single domain
 * name labels, while {@link #toUnicode(String)} and {@link #toASCII(String)}
 * work on whole domain names and email addresses.
 *
 * <p>All methods are stateless and safe for concurrent use.
 */
public final class Punycode {

	/** A string representing the current Punycode version number. */
	public static final String VERSION = "2.3.1";

	/** Highest positive signed 32-bit integer value. */
	private static final long MAX_INT = 2147483647L; // aka. 0x7FFFFFFF or 2^31-1

	/* Bootstring parameters. */
	private static final long BASE = 36;
	private static final long T_MIN = 1;
	private static final long T_MAX = 26;
	private static final long SKEW = 38;
	private static final long DAMP = 700;
	private static final long INITIAL_BIAS = 72;
	private static final long INITIAL_N = 128; // 0x80
	private static final char DELIMITER = '-'; // '-'

	/** The ASCII Compatible Encoding prefix that marks a Punycoded label. */
	private static final String ACE_PREFIX = "xn--";

	/** Error messages. */
	private static final String ERROR_OVERFLOW = "Overflow: input needs wider integers to process";
	private static final String ERROR_NOT_BASIC = "Illegal input >= 0x80 (not a basic code point)";
	private static final String ERROR_INVALID_INPUT = "Invalid input";

	/** Convenience shortcut. */
	private static final long BASE_MINUS_T_MIN = BASE - T_MIN;

	private Punycode() {
		// Not instantiable.
	}

	/**
	 * Converts a Punycode string of ASCII-only symbols to a string of Unicode
	 * symbols.
	 *
	 * @param input the Punycode string of ASCII-only symbols.
	 * @return the resulting string of Unicode symbols.
	 * @throws PunycodeException if the input is not valid Punycode, or if it
	 * needs wider integers to process.
	 */
	public static String decode(String input) {
		// Don't use UTF-16 code units for the output; these are code points.
		final int inputLength = input.length();
		// Each iteration of the main loop below consumes at least one input code
		// unit and appends exactly one code point, and the basic code points are
		// copied one for one, so the output can never be longer than the input.
		final int[] output = new int[inputLength];
		int outputLength = 0;

		long i = 0;
		long n = INITIAL_N;
		long bias = INITIAL_BIAS;

		// Handle the basic code points: let `basic` be the number of input code
		// points before the last delimiter, or `0` if there is none, then copy
		// the first basic code points to the output.

		int basic = input.lastIndexOf(DELIMITER);
		if (basic < 0) {
			basic = 0;
		}

		for (int j = 0; j < basic; ++j) {
			// if it's not a basic code point
			if (input.charAt(j) >= 0x80) {
				throw new PunycodeException(ERROR_NOT_BASIC);
			}
			output[outputLength++] = input.charAt(j);
		}

		// Main decoding loop: start just after the last delimiter if any basic
		// code points were copied; start at the beginning otherwise.

		for (int index = basic > 0 ? basic + 1 : 0; index < inputLength; /* no final expression */) {

			// `index` is the index of the next character to be consumed.
			// Decode a generalized variable-length integer into `delta`,
			// which gets added to `i`. The overflow checking is easier
			// if we increase `i` as we go, then subtract off its starting
			// value at the end to obtain `delta`.
			final long oldi = i;
			for (long w = 1, k = BASE; /* no condition */; k += BASE) {

				if (index >= inputLength) {
					throw new PunycodeException(ERROR_INVALID_INPUT);
				}

				final long digit = basicToDigit(input.charAt(index++));

				if (digit >= BASE) {
					throw new PunycodeException(ERROR_INVALID_INPUT);
				}
				if (digit > (MAX_INT - i) / w) {
					throw new PunycodeException(ERROR_OVERFLOW);
				}

				i += digit * w;
				final long t = k <= bias ? T_MIN : (k >= bias + T_MAX ? T_MAX : k - bias);

				if (digit < t) {
					break;
				}

				final long baseMinusT = BASE - t;
				// Kept from the reference algorithm in RFC 3492. `adapt` never
				// returns a bias above 198, and reaching this check requires one
				// of at least 235, so no input can trigger it — but the RFC
				// specifies the guard, and dropping it would silently rely on
				// that bound.
				if (w > MAX_INT / baseMinusT) {
					throw new PunycodeException(ERROR_OVERFLOW);
				}

				w *= baseMinusT;

			}

			final long out = outputLength + 1L;
			bias = adapt(i - oldi, out, oldi == 0);

			// `i` was supposed to wrap around from `out` to `0`,
			// incrementing `n` each time, so we'll fix that now:
			if (i / out > MAX_INT - n) {
				throw new PunycodeException(ERROR_OVERFLOW);
			}

			n += i / out;
			i %= out;

			// Insert `n` at position `i` of the output.
			final int insertAt = (int) i;
			System.arraycopy(output, insertAt, output, insertAt + 1, outputLength - insertAt);
			output[insertAt] = (int) n;
			++outputLength;
			++i;

		}

		return codePointsToString(output, outputLength);
	}

	/**
	 * Converts a string of Unicode symbols (e.g. a domain name label) to a
	 * Punycode string of ASCII-only symbols.
	 *
	 * @param input the string of Unicode symbols.
	 * @return the resulting Punycode string of ASCII-only symbols.
	 * @throws PunycodeException if the input needs wider integers to process.
	 */
	public static String encode(String input) {
		final StringBuilder output = new StringBuilder();

		// Convert the UTF-16 input to an array of Unicode code points.
		final int[] codePoints = Ucs2.decode(input);
		final int inputLength = codePoints.length;

		// Initialize the state.
		long n = INITIAL_N;
		long delta = 0;
		long bias = INITIAL_BIAS;

		// Handle the basic code points.
		for (final int currentValue : codePoints) {
			if (currentValue < 0x80) {
				output.append((char) currentValue);
			}
		}

		// `handledCPCount` is the number of code points that have been handled;
		// `basicLength` is the number of basic code points. Every basic code
		// point occupies exactly one UTF-16 code unit, so the builder's length
		// is that count.
		final int basicLength = output.length();
		int handledCPCount = basicLength;

		// Finish the basic string with a delimiter unless it's empty.
		if (basicLength > 0) {
			output.append(DELIMITER);
		}

		// Main encoding loop:
		while (handledCPCount < inputLength) {

			// All non-basic code points < n have been handled already. Find the
			// next larger one:
			long m = MAX_INT;
			for (final int currentValue : codePoints) {
				if (currentValue >= n && currentValue < m) {
					m = currentValue;
				}
			}

			// Increase `delta` enough to advance the decoder's <n,i> state to
			// <m,0>, but guard against overflow.
			final long handledCPCountPlusOne = handledCPCount + 1L;
			if (m - n > (MAX_INT - delta) / handledCPCountPlusOne) {
				throw new PunycodeException(ERROR_OVERFLOW);
			}

			delta += (m - n) * handledCPCountPlusOne;
			n = m;

			for (final int currentValue : codePoints) {
				if (currentValue < n && ++delta > MAX_INT) {
					throw new PunycodeException(ERROR_OVERFLOW);
				}
				if (currentValue == n) {
					// Represent delta as a generalized variable-length integer.
					long q = delta;
					for (long k = BASE; /* no condition */; k += BASE) {
						final long t = k <= bias ? T_MIN : (k >= bias + T_MAX ? T_MAX : k - bias);
						if (q < t) {
							break;
						}
						final long qMinusT = q - t;
						final long baseMinusT = BASE - t;
						output.append(digitToBasic(t + qMinusT % baseMinusT));
						q = qMinusT / baseMinusT;
					}

					output.append(digitToBasic(q));
					bias = adapt(delta, handledCPCountPlusOne, handledCPCount == basicLength);
					delta = 0;
					++handledCPCount;
				}
			}

			++delta;
			++n;

		}
		return output.toString();
	}

	/**
	 * Converts a Punycode string representing a domain name or an email address
	 * to Unicode. Only the Punycoded parts of the input will be converted, i.e.
	 * it doesn't matter if you call it on a string that has already been
	 * converted to Unicode.
	 *
	 * @param input the Punycoded domain name or email address to convert to
	 * Unicode.
	 * @return the Unicode representation of the given Punycode string.
	 * @throws PunycodeException if a Punycoded label is not valid Punycode.
	 */
	public static String toUnicode(String input) {
		return mapDomain(input, label -> label.startsWith(ACE_PREFIX)
			? decode(label.substring(ACE_PREFIX.length()).toLowerCase(Locale.ROOT))
			: label);
	}

	/**
	 * Converts a string representing a domain name or an email address to
	 * Punycode. Only the non-ASCII parts of the domain name will be converted,
	 * i.e. it doesn't matter if you call it with a domain that's already in
	 * ASCII.
	 *
	 * @param input the domain name or email address to convert, as a Unicode
	 * string.
	 * @return the Punycode representation of the given domain name or email
	 * address.
	 * @throws PunycodeException if a label needs wider integers to process.
	 */
	public static String toASCII(String input) {
		return mapDomain(input, label -> containsNonASCII(label)
			? ACE_PREFIX + encode(label)
			: label);
	}

	/**
	 * Methods to convert between UTF-16, the representation Java strings use
	 * internally, and Unicode code points.
	 *
	 * @see <a href="https://mathiasbynens.be/notes/javascript-encoding">Unicode
	 * in string representations</a>
	 */
	public static final class Ucs2 {

		private Ucs2() {
			// Not instantiable.
		}

		/**
		 * Creates an array containing the numeric code points of each Unicode
		 * character in the string. A pair of surrogate halves — which UTF-16
		 * stores as two separate {@code char}s — is converted into the single
		 * code point it represents. Unmatched surrogates are passed through as
		 * their own value.
		 *
		 * @param string the Unicode input string.
		 * @return the new array of code points.
		 * @see #encode(int[])
		 */
		public static int[] decode(String string) {
			final int length = string.length();
			final int[] output = new int[length];
			int outputLength = 0;
			int counter = 0;
			while (counter < length) {
				final char value = string.charAt(counter++);
				if (value >= 0xD800 && value <= 0xDBFF && counter < length) {
					// It's a high surrogate, and there is a next character.
					final char extra = string.charAt(counter++);
					if ((extra & 0xFC00) == 0xDC00) { // Low surrogate.
						output[outputLength++] = ((value & 0x3FF) << 10) + (extra & 0x3FF) + 0x10000;
					} else {
						// It's an unmatched surrogate; only append this code unit, in
						// case the next code unit is the high surrogate of a surrogate
						// pair.
						output[outputLength++] = value;
						counter--;
					}
				} else {
					output[outputLength++] = value;
				}
			}
			return Arrays.copyOf(output, outputLength);
		}

		/**
		 * Creates a string based on an array of numeric code points. The given
		 * array is not modified.
		 *
		 * @param codePoints the array of numeric code points.
		 * @return the new Unicode string.
		 * @throws PunycodeException if a value is not a Unicode code point.
		 * @see #decode(String)
		 */
		public static String encode(int[] codePoints) {
			return codePointsToString(codePoints, codePoints.length);
		}
	}

	/**
	 * Builds a string from the first {@code length} entries of a code point
	 * array. Unpaired surrogate values are permitted, matching what
	 * {@link Ucs2#decode(String)} produces for unpaired surrogates.
	 */
	private static String codePointsToString(int[] codePoints, int length) {
		final StringBuilder output = new StringBuilder(length);
		for (int i = 0; i < length; ++i) {
			final int codePoint = codePoints[i];
			if (!Character.isValidCodePoint(codePoint)) {
				throw new PunycodeException("Invalid code point " + codePoint);
			}
			output.appendCodePoint(codePoint);
		}
		return output.toString();
	}

	/**
	 * Applies {@code callback} to every label of a domain name or email address.
	 * In email addresses, only the domain name is passed to the callback; the
	 * local part (i.e. everything up to {@code @}) is left intact.
	 */
	private static String mapDomain(String domain, UnaryOperator<String> callback) {
		String localPart = "";
		final int at = domain.indexOf('@');
		if (at >= 0) {
			localPart = domain.substring(0, at + 1);
			// Anything past a second `@` is not part of the domain name.
			final int nextAt = domain.indexOf('@', at + 1);
			domain = nextAt < 0 ? domain.substring(at + 1) : domain.substring(at + 1, nextAt);
		}

		final String[] labels = splitLabels(domain);
		// Walk the labels back to front so that when more than one of them is
		// invalid, the last one is the one that reports the failure.
		for (int i = labels.length - 1; i >= 0; --i) {
			labels[i] = callback.apply(labels[i]);
		}
		return localPart + String.join(".", labels);
	}

	/**
	 * Splits a domain name on any of the RFC 3490 separators — U+002E FULL
	 * STOP, U+3002 IDEOGRAPHIC FULL STOP, U+FF0E FULLWIDTH FULL STOP and
	 * U+FF61 HALFWIDTH IDEOGRAPHIC FULL STOP. Empty labels are preserved,
	 * including a trailing one, so that {@code "example.com."} keeps its root
	 * label.
	 */
	private static String[] splitLabels(String domain) {
		final List<String> labels = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < domain.length(); ++i) {
			final char c = domain.charAt(i);
			if (c == '\u002E' || c == '\u3002' || c == '\uFF0E' || c == '\uFF61') {
				labels.add(domain.substring(start, i));
				start = i + 1;
			}
		}
		labels.add(domain.substring(start));
		return labels.toArray(new String[0]);
	}

	/** Reports whether the string holds a code unit outside the ASCII range. */
	private static boolean containsNonASCII(String string) {
		for (int i = 0; i < string.length(); ++i) {
			// Note: U+007F DEL is excluded too.
			if (string.charAt(i) > 0x7F) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Converts a basic code point into a digit/integer.
	 *
	 * @param codePoint the basic numeric code point value.
	 * @return the numeric value of a basic code point (for use in representing
	 * integers) in the range {@code 0} to {@code BASE - 1}, or {@code BASE} if
	 * the code point does not represent a value.
	 * @see #digitToBasic(long)
	 */
	private static long basicToDigit(char codePoint) {
		if (codePoint >= 0x30 && codePoint < 0x3A) {
			return 26 + (codePoint - 0x30);
		}
		if (codePoint >= 0x41 && codePoint < 0x5B) {
			return codePoint - 0x41;
		}
		if (codePoint >= 0x61 && codePoint < 0x7B) {
			return codePoint - 0x61;
		}
		return BASE;
	}

	/**
	 * Converts a digit/integer into a basic code point. Only the lowercase form
	 * is produced: RFC 3492's optional mixed-case annotation is not supported.
	 *
	 * @param digit the numeric value of a basic code point, in the range
	 * {@code 0} to {@code BASE - 1}.
	 * @return the basic code point whose value (when used for representing
	 * integers) is {@code digit}.
	 * @see #basicToDigit(char)
	 */
	private static char digitToBasic(long digit) {
		//  0..25 map to ASCII a..z
		// 26..35 map to ASCII 0..9
		return (char) (digit + 22 + (digit < 26 ? 75 : 0));
	}

	/**
	 * Bias adaptation function as per section 3.4 of RFC 3492.
	 *
	 * @see <a href="https://tools.ietf.org/html/rfc3492#section-3.4">RFC 3492,
	 * section 3.4</a>
	 */
	private static long adapt(long delta, long numPoints, boolean firstTime) {
		long k = 0;
		delta = firstTime ? delta / DAMP : delta >> 1;
		delta += delta / numPoints;
		for (/* no initialization */; delta > BASE_MINUS_T_MIN * T_MAX >> 1; k += BASE) {
			delta /= BASE_MINUS_T_MIN;
		}
		return k + (BASE_MINUS_T_MIN + 1) * delta / (delta + SKEW);
	}
}
