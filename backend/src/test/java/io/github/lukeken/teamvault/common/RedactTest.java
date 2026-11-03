package io.github.lukeken.teamvault.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RedactTest {

	@Test
	void email_keepsFirstLetterAndDomain() {
		assertThat(Redact.email("carol@acme.io")).isEqualTo("c***@acme.io");
	}

	@Test
	void email_oneLetterLocalPart_stillMasked() {
		assertThat(Redact.email("c@acme.io")).isEqualTo("c***@acme.io");
	}

	@Test
	void email_noAtSignOrNothingBeforeIt_fullyMasked() {
		assertThat(Redact.email("not-an-email")).isEqualTo("***");
		assertThat(Redact.email("@acme.io")).isEqualTo("***");
		assertThat(Redact.email(null)).isEqualTo("***");
	}

}
