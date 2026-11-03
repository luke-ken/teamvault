package io.github.lukeken.teamvault.common;

/**
 * Masks personal data before it reaches a log line. A log is the leakiest store in the
 * system (shipped, kept, grepped, pasted into tickets), so it gets the id or a masked
 * value, never the natural key.
 */
public final class Redact {

	private Redact() {
	}

	/**
	 * {@code carol@acme.io} becomes {@code c***@acme.io}; anything without an at sign
	 * becomes {@code ***}.
	 */
	public static String email(String email) {
		if (email == null) {
			return "***";
		}
		int at = email.indexOf("@");
		if (at < 1) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

}
