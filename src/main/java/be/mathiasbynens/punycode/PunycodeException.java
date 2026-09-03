package be.mathiasbynens.punycode;

/**
 * Thrown when a value falls outside the range Punycode can represent: input
 * that is not valid Punycode, input that carries a non-basic code point where
 * only basic ones are allowed, input that would need wider integers than the
 * Bootstring algorithm supports, or a code point that is not a Unicode scalar
 * value.
 *
 * @see Punycode
 */
public final class PunycodeException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an exception with the given detail message.
	 *
	 * @param message the detail message.
	 */
	public PunycodeException(String message) {
		super(message);
	}
}
