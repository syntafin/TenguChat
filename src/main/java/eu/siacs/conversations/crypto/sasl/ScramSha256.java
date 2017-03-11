package de.tengu.chat.crypto.sasl;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;

import java.security.SecureRandom;

import de.tengu.chat.entities.Account;
import de.tengu.chat.xml.TagWriter;

public class ScramSha256 extends ScramMechanism {
	static {
		DIGEST = new SHA256Digest();
		HMAC = new HMac(new SHA256Digest());
	}

	public ScramSha256(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
		super(tagWriter, account, rng);
	}

	@Override
	public int getPriority() {
		return 25;
	}

	@Override
	public String getMechanism() {
		return "SCRAM-SHA-256";
	}
}
