package dslab.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class AEScrypting {

    private byte[] iv;
    private SecretKey secretKey;
    private SecureRandom random;
    private KeyGenerator keygen;

    public AEScrypting(byte[] iv, SecretKey secretKey) {
        this.iv = iv;
        this.secretKey = secretKey;
    }

    public AEScrypting(){
        this.random = new SecureRandom();
        try {
            this.keygen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        keygen.init(256);

        //constructing a new vector
        iv = new byte[16];
        random.nextBytes(iv);

        //generating a new secretKey
        secretKey = keygen.generateKey();
    }

    public byte[] getIv() {
        return iv;
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }

    public String Encrypt( String message) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        //creating a Cypher for the procedure
        Cipher cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipherAES.init(Cipher.ENCRYPT_MODE,secretKey,ivSpec);

        //converting to base64
        byte[] converted = Base64.getDecoder().decode(message);

        //encrypting
        byte[] encryptedMessage = cipherAES.doFinal(converted);

        //encoding the encrypted base64 into string
        String messageToSend = Base64.getEncoder().encodeToString(encryptedMessage);

        return messageToSend;
    }

    public String Decrypt( String message) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        //creating a Cypher for the procedure
        Cipher cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipherAES.init(Cipher.DECRYPT_MODE,secretKey,ivSpec);

        //decoding string into base64
        byte[] encryptedMessage = Base64.getDecoder().decode(message);

        //decrypting
        byte[] decryptedMessage = cipherAES.doFinal(encryptedMessage);

        //encoding the decrypted base64 into string
        String result = Base64.getEncoder().encodeToString(decryptedMessage);

        return result;
    }
}
