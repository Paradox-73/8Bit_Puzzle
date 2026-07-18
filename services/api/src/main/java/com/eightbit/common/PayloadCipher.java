package com.eightbit.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Light, symmetric obfuscation for the day's solution shipped to the browser (so guesses/hints can be
 * checked client-side with zero latency, like NYT Wordle). XOR-with-a-shared-key + base64 — this is
 * NOT real secrecy (the key is also in the client bundle), just enough that the answer isn't sitting in
 * plaintext in the Network tab. Acceptable because the run has no rewards and is server-recorded anyway.
 * The client mirror is apps/web/src/cipher.js — the key MUST match.
 */
@Component
public class PayloadCipher {

    private final byte[] key;

    public PayloadCipher(@Value("${app.client.cipher-key:8bit-iiitb-daily-2026-xor-key}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    /** XOR the UTF-8 bytes with the repeating key, then base64. Reversed by cipher.js decode(). */
    public String encode(String plain) {
        byte[] in = plain.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ key[i % key.length]);
        }
        return Base64.getEncoder().encodeToString(out);
    }
}
