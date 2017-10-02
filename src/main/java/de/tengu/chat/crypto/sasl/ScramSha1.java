package de.tengu.chat.crypto.sasl;

import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;

import java.security.SecureRandom;

import de.tengu.chat.entities.Account;
import de.tengu.chat.xml.TagWriter;

public class ScramSha1 extends ScramMechanism {
	static {
		DIGEST = new SHA1Digest();
		HMAC = new HMac(new SHA1Digest());
	}

	public ScramSha1(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
		super(tagWriter, account, rng);
	}

	@Override
	public int getPriority() {
		return 20;
	}

	@Override
	public String getMechanism() {
		return "SCRAM-SHA-1";
	}
}
