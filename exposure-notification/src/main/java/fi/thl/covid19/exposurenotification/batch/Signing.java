package fi.thl.covid19.exposurenotification.batch;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Signing {
    private Signing() {}

    private static final String KEY_ALGORITHM = "EC";

    public static PrivateKey privateKey(String privateKeyBase64) {
        return privateKey(Base64.getDecoder().decode(privateKeyBase64));
    }

    public static PrivateKey privateKey(byte[] privateKeyBytes) {
        try {
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Could not generate private key", e);
        }
    }

    public static PublicKey publicKey(String publicKeyBase64) {
        return publicKey(Base64.getDecoder().decode(publicKeyBase64));
    }

    public static PublicKey publicKey(byte[] publicKeyBytes) {
        try {
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Could not generate public key", e);
        }
    }

    public static KeyPair randomKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            g.initialize(ecSpec, new SecureRandom());
            return g.generateKeyPair();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not generate random keypair", e);
        }
    }

    public static byte[] sign(String algorithmName, PrivateKey key, byte[] payload)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        java.security.Signature ecdsaSign = java.security.Signature.getInstance(algorithmName);
        ecdsaSign.initSign(key);
        ecdsaSign.update(payload);
        return ecdsaSign.sign();
    }

    public static boolean singatureMatches(String algorithmName, PublicKey key, byte[] signature, byte[] payload)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        java.security.Signature ecdsaVerify = java.security.Signature.getInstance(algorithmName);
        ecdsaVerify.initVerify(key);
        ecdsaVerify.update(payload);
        return ecdsaVerify.verify(signature);
    }
}
