package be.mathiasbynens.punycode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PunycodeTest {

	/** A Unicode string and the Punycode string it corresponds to. */
	record StringCase(String description, String decoded, String encoded) {
		StringCase(String decoded, String encoded) {
			this(null, decoded, encoded);
		}
	}

	/** A UTF-16 string and the code points it decodes to. */
	record Ucs2Case(String description, int[] decoded, String encoded) {
	}

	/** A domain name or email address, in Unicode and in Punycode. */
	record DomainCase(String description, String decoded, String encoded) {
		DomainCase(String decoded, String encoded) {
			this(null, decoded, encoded);
		}
	}

	private static final List<StringCase> STRINGS = List.of(
		new StringCase(
			"a single basic code point",
			"Bach",
			"Bach-"),
		new StringCase(
			"a single non-ASCII character",
			"ü",
			"tda"),
		new StringCase(
			"multiple non-ASCII characters",
			"üëäö♥",
			"4can8av2009b"),
		new StringCase(
			"mix of ASCII and non-ASCII characters",
			"bücher",
			"bcher-kva"),
		new StringCase(
			"long string with both ASCII and non-ASCII characters",
			"Willst du die Blüthe des frühen, die Früchte des späteren Jahres",
			"Willst du die Blthe des frhen, die Frchte des spteren Jahres-x9e96lkal"),
		// https://tools.ietf.org/html/rfc3492#section-7.1
		new StringCase(
			"Arabic (Egyptian)",
			"ليهمابتكلموشعربي؟",
			"egbpdaj6bu4bxfgehfvwxn"),
		new StringCase(
			"Chinese (simplified)",
			"他们为什么不说中文",
			"ihqwcrb4cv8a8dqg056pqjye"),
		new StringCase(
			"Chinese (traditional)",
			"他們爲什麽不說中文",
			"ihqwctvzc91f659drss3x8bo0yb"),
		new StringCase(
			"Czech",
			"Pročprostěnemluvíčesky",
			"Proprostnemluvesky-uyb24dma41a"),
		new StringCase(
			"Hebrew",
			"למההםפשוטלאמדבריםעברית",
			"4dbcagdahymbxekheh6e0a7fei0b"),
		new StringCase(
			"Hindi (Devanagari)",
			"यहलोगहिन्दीक्योंनहींबोलसकतेहैं",
			"i1baa7eci9glrd9b2ae1bj0hfcgg6iyaf8o0a1dig0cd"),
		new StringCase(
			"Japanese (kanji and hiragana)",
			"なぜみんな日本語を話してくれないのか",
			"n8jok5ay5dzabd5bym9f0cm5685rrjetr6pdxa"),
		new StringCase(
			"Korean (Hangul syllables)",
			"세계의모든사람들이한국어를이해한다면얼마나좋을까",
			"989aomsvi5e83db1d2a355cv1e0vak1dwrv93d5xbh15a0dt30a5jpsd879ccm6fea98c"),
		/*
		 * As there is no way to do it, Punycode does not support mixed-case
		 * annotation (which is entirely optional as per the RFC). So, while the
		 * RFC sample string encodes to:
		 * `b1abfaaepdrnnbgefbaDotcwatmq2g4l`
		 * Without mixed-case annotation it has to encode to:
		 * `b1abfaaepdrnnbgefbadotcwatmq2g4l`
		 * https://github.com/mathiasbynens/punycode.js/issues/3
		 */
		new StringCase(
			"Russian (Cyrillic)",
			"почемужеонинеговорятпорусски",
			"b1abfaaepdrnnbgefbadotcwatmq2g4l"),
		new StringCase(
			"Spanish",
			"PorquénopuedensimplementehablarenEspañol",
			"PorqunopuedensimplementehablarenEspaol-fmd56a"),
		new StringCase(
			"Vietnamese",
			"TạisaohọkhôngthểchỉnóitiếngViệt",
			"TisaohkhngthchnitingVit-kjcr8268qyxafd2f1b9g"),
		new StringCase(
			"3年B組金八先生",
			"3B-ww4c5e180e575a65lsy2b"),
		new StringCase(
			"安室奈美恵-with-SUPER-MONKEYS",
			"-with-SUPER-MONKEYS-pc58ag80a8qai00g7n9n"),
		new StringCase(
			"Hello-Another-Way-それぞれの場所",
			"Hello-Another-Way--fc4qua05auwb3674vfr0b"),
		new StringCase(
			"ひとつ屋根の下2",
			"2-u9tlzr9756bt3uc0v"),
		new StringCase(
			"MajiでKoiする5秒前",
			"MajiKoi5-783gue6qz075azm5e"),
		new StringCase(
			"パフィーdeルンバ",
			"de-jg4avhby1noc0d"),
		new StringCase(
			"そのスピードで",
			"d9juau41awczczp"),
		/*
		 * This example is an ASCII string that breaks the existing rules for
		 * host name labels. (It's not a realistic example for IDNA, because
		 * IDNA never encodes pure ASCII labels.)
		 */
		new StringCase(
			"ASCII string that breaks the existing rules for host-name labels",
			"-> $1.00 <-",
			"-> $1.00 <--")
	);

	// Every Unicode symbol is tested separately by the round-trip cases above.
	// These are just the extra tests for symbol combinations:
	private static final List<Ucs2Case> UCS2 = List.of(
		new Ucs2Case(
			"Consecutive astral symbols",
			new int[] { 127829, 119808, 119558, 119638 },
			"🍕𝐀𝌆𝍖"),
		new Ucs2Case(
			"U+D800 (high surrogate) followed by non-surrogates",
			new int[] { 55296, 97, 98 },
			"\uD800ab"),
		new Ucs2Case(
			"U+DC00 (low surrogate) followed by non-surrogates",
			new int[] { 56320, 97, 98 },
			"\uDC00ab"),
		new Ucs2Case(
			"High surrogate followed by another high surrogate",
			new int[] { 0xD800, 0xD800 },
			"\uD800\uD800"),
		new Ucs2Case(
			"Unmatched high surrogate, followed by a surrogate pair, followed by an unmatched high surrogate",
			new int[] { 0xD800, 0x1D306, 0xD800 },
			"\uD800𝌆\uD800"),
		new Ucs2Case(
			"Low surrogate followed by another low surrogate",
			new int[] { 0xDC00, 0xDC00 },
			"\uDC00\uDC00"),
		new Ucs2Case(
			"Unmatched low surrogate, followed by a surrogate pair, followed by an unmatched low surrogate",
			new int[] { 0xDC00, 0x1D306, 0xDC00 },
			"\uDC00𝌆\uDC00")
	);

	private static final List<DomainCase> DOMAINS = List.of(
		new DomainCase(
			"mañana.com",
			"xn--maana-pta.com"),
		// https://github.com/mathiasbynens/punycode.js/issues/17
		new DomainCase(
			"example.com.",
			"example.com."),
		new DomainCase(
			"bücher.com",
			"xn--bcher-kva.com"),
		new DomainCase(
			"café.com",
			"xn--caf-dma.com"),
		new DomainCase(
			"☃-⌘.com",
			"xn----dqo34k.com"),
		new DomainCase(
			"퐀☃-⌘.com",
			"xn----dqo34kn65z.com"),
		new DomainCase(
			"Emoji",
			"💩.la",
			"xn--ls8h.la"),
		new DomainCase(
			"Non-printable ASCII",
			"\u0000\u0001\u0002foo.bar",
			"\u0000\u0001\u0002foo.bar"),
		new DomainCase(
			"Email address",
			"джумла@джpумлатест.bрфa",
			"джумла@xn--p-8sbkgc5ag7bhce.xn--ba-lmcq"),
		// https://github.com/mathiasbynens/punycode.js/pull/115
		new DomainCase(
			"foo\u007F.example",
			"foo\u007F.example")
	);

	private static final List<DomainCase> SEPARATORS = List.of(
		new DomainCase(
			"Using U+002E as separator",
			"mañana.com",
			"xn--maana-pta.com"),
		new DomainCase(
			"Using U+3002 as separator",
			"mañana。com",
			"xn--maana-pta.com"),
		new DomainCase(
			"Using U+FF0E as separator",
			"mañana．com",
			"xn--maana-pta.com"),
		new DomainCase(
			"Using U+FF61 as separator",
			"mañana｡com",
			"xn--maana-pta.com")
	);

	/** Cases named by their description, as the UCS-2 suites are. */
	private static Stream<Arguments> ucs2() {
		return UCS2.stream().map(object -> Arguments.of(object.description(), object));
	}

	/** Cases named by their description, falling back to the Punycode string. */
	private static Stream<Arguments> stringsNamedByEncoded() {
		return STRINGS.stream().map(object ->
			Arguments.of(object.description() != null ? object.description() : object.encoded(), object));
	}

	/** Cases named by their description, falling back to the Unicode string. */
	private static Stream<Arguments> stringsNamedByDecoded() {
		return STRINGS.stream().map(object ->
			Arguments.of(object.description() != null ? object.description() : object.decoded(), object));
	}

	private static Stream<StringCase> strings() {
		return STRINGS.stream();
	}

	private static Stream<Arguments> domainsNamedByEncoded() {
		return DOMAINS.stream().map(object ->
			Arguments.of(object.description() != null ? object.description() : object.encoded(), object));
	}

	private static Stream<Arguments> domainsNamedByDecoded() {
		return DOMAINS.stream().map(object ->
			Arguments.of(object.description() != null ? object.description() : object.decoded(), object));
	}

	private static Stream<DomainCase> separators() {
		return SEPARATORS.stream();
	}

	@Nested
	@DisplayName("Punycode.Ucs2.decode")
	class Ucs2Decode {

		@ParameterizedTest(name = "{0}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#ucs2")
		@DisplayName("decodes")
		void decodesSymbolCombinations(String name, Ucs2Case object) {
			assertArrayEquals(
				object.decoded(),
				Punycode.Ucs2.decode(object.encoded()),
				object.description()
			);
		}

		@Test
		@DisplayName("throws PunycodeException: Illegal input >= 0x80 (not a basic code point)")
		void throwsNotBasic() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.decode("\u0081-")
			);
			assertEquals("Illegal input >= 0x80 (not a basic code point)", exception.getMessage());
		}

		@Test
		@DisplayName("throws PunycodeException: Overflow: input needs wider integers to process")
		void throwsOverflow() {
			assertThrows(PunycodeException.class, () -> Punycode.decode("\u0081"));
		}
	}

	@Nested
	@DisplayName("Punycode.Ucs2.encode")
	class Ucs2Encode {

		@ParameterizedTest(name = "{0}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#ucs2")
		@DisplayName("encodes")
		void encodesSymbolCombinations(String name, Ucs2Case object) {
			assertEquals(
				object.encoded(),
				Punycode.Ucs2.encode(object.decoded())
			);
		}

		@Test
		@DisplayName("does not mutate argument array")
		void doesNotMutateArgumentArray() {
			final int[] codePoints = { 0x61, 0x62, 0x63 };
			final String result = Punycode.Ucs2.encode(codePoints);
			assertEquals("abc", result);
			assertArrayEquals(new int[] { 0x61, 0x62, 0x63 }, codePoints);
		}
	}

	@Nested
	@DisplayName("Punycode.decode")
	class Decode {

		@ParameterizedTest(name = "{0}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#stringsNamedByEncoded")
		@DisplayName("decodes")
		void decodesStrings(String name, StringCase object) {
			assertEquals(
				object.decoded(),
				Punycode.decode(object.encoded())
			);
		}

		@Test
		@DisplayName("handles uppercase Z")
		void handlesUppercaseZ() {
			assertEquals("箥", Punycode.decode("ZZZ"));
		}

		@Test
		@DisplayName("throws PunycodeException: Invalid input")
		void throwsInvalidInput() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.decode("ls8h=")
			);
			assertEquals("Invalid input", exception.getMessage());
		}
	}

	@Nested
	@DisplayName("Punycode.encode")
	class Encode {

		@ParameterizedTest(name = "{0}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#stringsNamedByDecoded")
		@DisplayName("encodes")
		void encodesStrings(String name, StringCase object) {
			assertEquals(
				object.encoded(),
				Punycode.encode(object.decoded())
			);
		}
	}

	@Nested
	@DisplayName("Punycode.toUnicode")
	class ToUnicode {

		@ParameterizedTest(name = "{0}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#domainsNamedByEncoded")
		@DisplayName("converts")
		void convertsDomains(String name, DomainCase object) {
			assertEquals(
				object.decoded(),
				Punycode.toUnicode(object.encoded())
			);
		}

		@DisplayName("does not convert names (or other strings) that don't start with `xn--`")
		@ParameterizedTest(name = "{index}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#strings")
		void doesNotConvertNamesWithoutAcePrefix(StringCase object) {
			assertEquals(
				object.encoded(),
				Punycode.toUnicode(object.encoded())
			);
			assertEquals(
				object.decoded(),
				Punycode.toUnicode(object.decoded())
			);
		}
	}

	@Nested
	@DisplayName("Punycode.toASCII")
	class ToAscii {

		@ParameterizedTest(name = "{0}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#domainsNamedByDecoded")
		@DisplayName("converts")
		void convertsDomains(String name, DomainCase object) {
			assertEquals(
				object.encoded(),
				Punycode.toASCII(object.decoded())
			);
		}

		@DisplayName("does not convert domain names (or other strings) that are already in ASCII")
		@ParameterizedTest(name = "{index}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#strings")
		void doesNotConvertAsciiNames(StringCase object) {
			assertEquals(
				object.encoded(),
				Punycode.toASCII(object.encoded())
			);
		}

		@DisplayName("supports IDNA2003 separators for backwards compatibility")
		@ParameterizedTest(name = "{index}")
		@MethodSource("be.mathiasbynens.punycode.PunycodeTest#separators")
		void supportsIdna2003Separators(DomainCase object) {
			assertEquals(
				object.encoded(),
				Punycode.toASCII(object.decoded())
			);
		}
	}

	/*
	 * The suites below cover behaviour that the original delegated to its
	 * runtime — splitting on `@` and on the label separators, detecting
	 * non-ASCII code units, case-folding the ACE label, and validating code
	 * points — plus the Bootstring overflow guards, which the port now
	 * evaluates in explicit 64-bit arithmetic. Every expectation here was
	 * captured from the reference implementation.
	 */

	@Nested
	@DisplayName("Punycode overflow and invalid input")
	class Overflow {

		@Test
		@DisplayName("throws when a variable-length integer runs past the end of the input")
		void throwsWhenIntegerIsTruncated() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.decode("a-z")
			);
			assertEquals("Invalid input", exception.getMessage());
		}

		@Test
		@DisplayName("throws when a digit would push the index past the integer range")
		void throwsWhenDigitOverflowsIndex() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.decode("9999999999")
			);
			assertEquals("Overflow: input needs wider integers to process", exception.getMessage());
		}

		@Test
		@DisplayName("throws when the code point would be pushed past the integer range")
		void throwsWhenCodePointOverflows() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.decode("n880016ocbk8o")
			);
			assertEquals("Overflow: input needs wider integers to process", exception.getMessage());
		}

		@Test
		@DisplayName("throws when advancing the encoder state would overflow")
		void throwsWhenDeltaAdvanceOverflows() {
			final String input = "a".repeat(2000) + Character.toString(0x10FFFF);
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.encode(input)
			);
			assertEquals("Overflow: input needs wider integers to process", exception.getMessage());
		}

		@Test
		@DisplayName("throws when incrementing delta would overflow")
		void throwsWhenDeltaIncrementOverflows() {
			final String input = "a".repeat(2000) + Character.toString(0x1060B5);
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.encode(input)
			);
			assertEquals("Overflow: input needs wider integers to process", exception.getMessage());
		}

		@Test
		@DisplayName("encodes the largest input that stays inside the integer range")
		void encodesJustBelowTheOverflowBoundary() {
			final String input = "a".repeat(2000) + Character.toString(0x1060B4);
			assertEquals(2009, Punycode.encode(input).length());
		}

		@Test
		@DisplayName("rejects a code point below the basic digit range")
		void rejectsCodePointBelowDigitRange() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.decode("a!b")
			);
			assertEquals("Invalid input", exception.getMessage());
		}

		@Test
		@DisplayName("rejects a code point above the Unicode range")
		void rejectsCodePointAboveUnicodeRange() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.Ucs2.encode(new int[] { 0x110000 })
			);
			assertEquals("Invalid code point 1114112", exception.getMessage());
		}

		@Test
		@DisplayName("rejects a negative code point")
		void rejectsNegativeCodePoint() {
			final PunycodeException exception = assertThrows(
				PunycodeException.class,
				() -> Punycode.Ucs2.encode(new int[] { -1 })
			);
			assertEquals("Invalid code point -1", exception.getMessage());
		}

		@Test
		@DisplayName("converts empty input to empty output")
		void handlesEmptyInput() {
			assertEquals("", Punycode.decode(""));
			assertEquals("", Punycode.encode(""));
			assertEquals("", Punycode.toASCII(""));
			assertEquals("", Punycode.toUnicode(""));
			assertEquals("", Punycode.Ucs2.encode(new int[0]));
			assertArrayEquals(new int[0], Punycode.Ucs2.decode(""));
		}
	}

	@Nested
	@DisplayName("Punycode domain parsing")
	class DomainParsing {

		@Test
		@DisplayName("only converts the domain part of an email address")
		void convertsOnlyTheDomainPart() {
			assertEquals("a@mañana.com", Punycode.toUnicode("a@xn--maana-pta.com"));
		}

		@Test
		@DisplayName("ignores everything past a second `@`")
		void ignoresEverythingPastASecondAt() {
			assertEquals("a@b", Punycode.toASCII("a@b@mañana.com"));
		}

		@Test
		@DisplayName("treats a trailing `@` as ending the local part")
		void treatsTrailingAtAsEndOfLocalPart() {
			assertEquals("mañana.com@", Punycode.toASCII("mañana.com@"));
			assertEquals("a@", Punycode.toASCII("a@"));
			assertEquals("xn--maana-pta@x", Punycode.toUnicode("xn--maana-pta@x"));
		}

		@Test
		@DisplayName("preserves empty labels, including a trailing root label")
		void preservesEmptyLabels() {
			assertEquals("a..b", Punycode.toASCII("a..b"));
			assertEquals("example.com.", Punycode.toASCII("example.com."));
			assertEquals("example.com.", Punycode.toUnicode("example.com."));
		}

		@Test
		@DisplayName("normalises every IDNA2003 separator to U+002E")
		void normalisesEverySeparator() {
			assertEquals("xn--maana-pta.x.y.z", Punycode.toASCII("mañana\u3002x\uFF0Ey\uFF61z"));
		}

		@Test
		@DisplayName("matches the `xn--` prefix case-sensitively")
		void matchesAcePrefixCaseSensitively() {
			assertEquals("XN--maana-pta.com", Punycode.toUnicode("XN--maana-pta.com"));
			assertEquals("mañana.com", Punycode.toUnicode("xn--maana-pta.com"));
		}

		@Test
		@DisplayName("lowercases the Punycode label independently of the default locale")
		void lowercasesIndependentlyOfLocale() {
			final Locale previous = Locale.getDefault();
			try {
				// In Turkish, `I` lowercases to a dotless `\u0131`, which is not a
				// basic code point and would make the label undecodable.
				Locale.setDefault(new Locale("tr", "TR"));
				assertEquals(
					"他们为什么不说中文",
					Punycode.toUnicode("xn--Ihqwcrb4cv8a8dqg056pqjye")
				);
			} finally {
				Locale.setDefault(previous);
			}
		}

		@Test
		@DisplayName("treats U+007F as ASCII but U+0080 as non-ASCII")
		void treatsDelAsAscii() {
			assertEquals("\u007F", Punycode.toASCII("\u007F"));
			assertEquals("xn--a", Punycode.toASCII("\u0080"));
		}

		@Test
		@DisplayName("exposes the version number")
		void exposesVersion() {
			assertEquals("2.3.1", Punycode.VERSION);
		}
	}
}
