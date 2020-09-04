package com.metadium.did.wapper;

import java.io.IOException;
import java.math.BigInteger;

/** nonce interface */
public interface Nonce {
    public BigInteger getNonce() throws IOException;
}
