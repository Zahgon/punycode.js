# Punycode [![Maven Central](https://img.shields.io/maven-central/v/be.mathiasbynens/punycode)](https://central.sonatype.com/artifact/be.mathiasbynens/punycode)

Punycode is a robust Punycode converter that fully complies to [RFC 3492](https://tools.ietf.org/html/rfc3492) and [RFC 5891](https://tools.ietf.org/html/rfc5891).

This Java library is the result of comparing, optimizing and documenting different open-source implementations of the Punycode algorithm:

* [The C example code from RFC 3492](https://tools.ietf.org/html/rfc3492#appendix-C)
* [`punycode.c` by _Markus W. Scherer_ (IBM)](http://opensource.apple.com/source/ICU/ICU-400.42/icuSources/common/punycode.c)
* [`punycode.c` by _Ben Noordhuis_](https://github.com/bnoordhuis/punycode/blob/master/punycode.c)
* [JavaScript implementation by _some_](http://stackoverflow.com/questions/183485/can-anyone-recommend-a-good-free-javascript-for-punycode-to-unicode-conversion/301287#301287)
* [`punycode.js` by _Ben Noordhuis_](https://github.com/joyent/node/blob/426298c8c1c0d5b5224ac3658c41e7c2a3fe9377/lib/punycode.js) (note: [not fully compliant](https://github.com/joyent/node/issues/2072))

## Installation

With [Maven](https://maven.apache.org/), add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>be.mathiasbynens</groupId>
  <artifactId>punycode</artifactId>
  <version>2.3.1</version>
</dependency>
```

With [Gradle](https://gradle.org/), add it to your `build.gradle`:

```groovy
implementation 'be.mathiasbynens:punycode:2.3.1'
```

Then import it:

```java
import be.mathiasbynens.punycode.Punycode;
```

The library requires Java 17 or newer and has no runtime dependencies.

## API

All methods are `static`, and the class is stateless and safe for concurrent use. Input that cannot be represented — invalid Punycode, a code point that is not a Unicode scalar value, or a value that needs wider integers than the algorithm supports — raises a `PunycodeException`, which extends `IllegalArgumentException`.

### `Punycode.decode(String string)`

Converts a Punycode string of ASCII symbols to a string of Unicode symbols.

```java
// decode domain name parts
Punycode.decode("maana-pta"); // "mañana"
Punycode.decode("--dqo34k");  // "☃-⌘"
```

### `Punycode.encode(String string)`

Converts a string of Unicode symbols to a Punycode string of ASCII symbols.

```java
// encode domain name parts
Punycode.encode("mañana"); // "maana-pta"
Punycode.encode("☃-⌘");    // "--dqo34k"
```

### `Punycode.toUnicode(String input)`

Converts a Punycode string representing a domain name or an email address to Unicode. Only the Punycoded parts of the input will be converted, i.e. it doesn’t matter if you call it on a string that has already been converted to Unicode.

```java
// decode domain names
Punycode.toUnicode("xn--maana-pta.com");
// → "mañana.com"
Punycode.toUnicode("xn----dqo34k.com");
// → "☃-⌘.com"

// decode email addresses
Punycode.toUnicode("джумла@xn--p-8sbkgc5ag7bhce.xn--ba-lmcq");
// → "джумла@джpумлатест.bрфa"
```

### `Punycode.toASCII(String input)`

Converts a lowercased Unicode string representing a domain name or an email address to Punycode. Only the non-ASCII parts of the input will be converted, i.e. it doesn’t matter if you call it with a domain that’s already in ASCII.

```java
// encode domain names
Punycode.toASCII("mañana.com");
// → "xn--maana-pta.com"
Punycode.toASCII("☃-⌘.com");
// → "xn----dqo34k.com"

// encode email addresses
Punycode.toASCII("джумла@джpумлатест.bрфa");
// → "джумла@xn--p-8sbkgc5ag7bhce.xn--ba-lmcq"
```

### `Punycode.Ucs2`

#### `Punycode.Ucs2.decode(String string)`

Creates an array containing the numeric code point values of each Unicode symbol in the string. Java strings are [UTF-16](https://mathiasbynens.be/notes/javascript-encoding), so this function converts a pair of surrogate halves — which UTF-16 stores as two separate `char`s — into the single code point it represents. Unmatched surrogates are passed through as their own value.

```java
Punycode.Ucs2.decode("abc");
// → [0x61, 0x62, 0x63]
// surrogate pair for U+1D306 TETRAGRAM FOR CENTRE:
Punycode.Ucs2.decode("𝌆");
// → [0x1D306]
```

#### `Punycode.Ucs2.encode(int[] codePoints)`

Creates a string based on an array of numeric code point values. The given array is not modified.

```java
Punycode.Ucs2.encode(new int[] { 0x61, 0x62, 0x63 });
// → "abc"
Punycode.Ucs2.encode(new int[] { 0x1D306 });
// → "𝌆"
```

### `Punycode.VERSION`

A string representing the current Punycode version number.

## For maintainers

### How to build and test

```sh
mvn verify
```

### How to publish a new release

1. On the `main` branch, bump the version number in `pom.xml`:

    ```sh
    mvn versions:set -DnewVersion=X.Y.Z
    ```

    Then commit and tag the release:

    ```sh
    git commit -am 'Release vX.Y.Z' && git tag vX.Y.Z
    ```

1. Push the release commit and tag:

    ```sh
    git push && git push --tags
    ```

    Our CI then automatically publishes the new release to [Maven Central](https://central.sonatype.com/artifact/be.mathiasbynens/punycode).

## Author

| [![twitter/mathias](https://gravatar.com/avatar/24e08a9ea84deb17ae121074d0f17125?s=70)](https://twitter.com/mathias "Follow @mathias on Twitter") |
|---|
| [Mathias Bynens](https://mathiasbynens.be/) |

## License

Punycode is available under the [MIT](https://mths.be/mit) license.
