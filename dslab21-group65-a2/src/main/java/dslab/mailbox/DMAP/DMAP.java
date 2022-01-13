package dslab.mailbox.DMAP;

import dslab.Message.Message;
import dslab.mailbox.tcp.dmapThread;
import dslab.util.AEScrypting;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.*;

public class DMAP {

    private static final int PLAIN = -1;
    private static final int RSAENCRYPTED = -2;
    private static final int CHALLENGERESPONSE = -3;
    private static final int LOGGEDOUT = 0;
    private static final int LOGGEDIN = 1;

    private static final int SHOWING = 2;
    private static final int LISTING = 3;

    private int state = PLAIN;
    private String user;
    private dmapThread thread;

    private HashMap<Integer,Message> list;
    private Message message;
    private String componentID;

    private byte[] iv;
    private SecretKey secretKey;

    public DMAP(dmapThread thread, String componentID){
        this.thread = thread;
        this.componentID = componentID;
    }

    public String AESEncryptStub(String message)
    {
        return message;
    }

    public String AESDecryptStub(String message)
    {
        return message;
    }

    public String RSADecryptStub(String message)
    {
        return message;
    }

    public String DecryptString(String string) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if(state == PLAIN || state == RSAENCRYPTED) {
            return string;
        }
        else{
            AEScrypting decrypter = new AEScrypting(iv, secretKey);
            return decrypter.Decrypt(string);
        }
    }

    public String EncryptString(String string) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if (state != PLAIN && state != RSAENCRYPTED) {
            AEScrypting encrypter = new AEScrypting(iv, secretKey);
            return encrypter.Encrypt(string);
        }
        return string;
    }

    public String processInput(String input) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        String output = "waiting for client's message";

        if(state == PLAIN || state == RSAENCRYPTED) {
        }
        else if (input != null) {
            AEScrypting decrypter = new AEScrypting(iv, secretKey);
            input = decrypter.Decrypt(input);
        }

        System.out.println("the message from the client is: " + input);

        switch(state){
            case PLAIN:
                if(input == null)
                    output = "ok DMAP2.0";

                else {
                    String newInput = input;
                    String[] splitInput = input.split("\\s");

                    if(splitInput.length > 1)
                        newInput = splitInput[0];
                    switch(newInput.toLowerCase(Locale.ROOT)){
                        case "startsecure":
                            output = "ok " + componentID;
                            state = RSAENCRYPTED;
                            break;
                        default:
                            output = "PLAIN: error protocol error";
                            break;
                    }
                }
            break;
            case RSAENCRYPTED:
                //File privKeyFile = new File("dslab21-group65-a2/keys/server/"+componentID+".der");
                File privKeyFile = new File("keys/server/"+componentID+".der");
                PrivateKey privateKey = Keys.readPrivateKey(privKeyFile);
                Cipher RSAcipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                RSAcipher.init(Cipher.DECRYPT_MODE,privateKey);

                //decoding the message to base64
                byte[] b64input = Base64.getDecoder().decode(input);

                //decrypting
                byte[] decryptedInput = RSAcipher.doFinal(b64input);

                //making a string
                String decryptedString = new String(decryptedInput, StandardCharsets.UTF_8);


                String newInput = decryptedString;
                String[] splitInput = decryptedString.split("\\s");

                if(splitInput.length > 1)
                    newInput = splitInput[0];
                switch(newInput.toLowerCase(Locale.ROOT)){
                    case "ok":
                        if(splitInput.length < 4)
                        {
                            output = "RSAENCRYPTED: error not enough data";
                            break;
                        }
                        String clientChallenge = splitInput[1];

                        //converting the keystring into the key
                        String secretKeyString = splitInput[2];
                        byte[] decodedSecretKey = Base64.getDecoder().decode(secretKeyString);
                        secretKey = new SecretKeySpec(decodedSecretKey, 0, decodedSecretKey.length, "AES");
                        System.out.println("SecretKey on server side: " + Arrays.toString(secretKey.getEncoded()));

                        String ivString = splitInput[3];
                        iv = Base64.getDecoder().decode(ivString);
                        System.out.println("iv on server side: " + Arrays.toString(iv));

                        AEScrypting AEScrypter = new AEScrypting(iv,secretKey);

                        output = "ok " + clientChallenge;

                        //output = "ok " + clientChallenge;
                        state = CHALLENGERESPONSE;
                        break;
                    default:
                        output = "RSAENCRYPRTED: error protocol error";
                        break;
                }
                break;
            case CHALLENGERESPONSE:
                System.out.println("DMAP CHALLENGERESPONSE");
                splitInput = input.split("\\s");
                switch(input.toLowerCase(Locale.ROOT)) {
                    case "ok":
                        if (splitInput.length == 1) {
                            System.out.println("DMAP LOGGEDOUT SET");
                            state = LOGGEDOUT;
                            break;
                        }
                    default:
                        output = "CHALLENGERESPONSE: error protocol error";
                        break;
                }
                break;
            case LOGGEDOUT:
                newInput = input;
                splitInput = input.split("\\s");
                System.out.println("DMAP LOGGEDOUT");
                    if(splitInput.length > 1)
                        newInput = splitInput[0];
                    switch(newInput.toLowerCase(Locale.ROOT)){
                        case "login":
                            if(splitInput.length < 3){
                                output = "error not enough data";
                                break;
                            }
                            String name = splitInput[1];
                            String password = splitInput[2];

                            //check user
                            int ans = thread.checkLogin(name,password);

                            switch(ans){
                                case 0:
                                    output = "error unknown user";
                                    break;
                                case 1:
                                    output = "error wrong password";
                                    break;
                                case 2:
                                    output = "ok";
                                    state = LOGGEDIN;
                                    user = name;
                                    break;
                            }
                            break;

                        case "show":
                        case "delete":
                        case "list":
                        case "logout":
                            output = "error not logged in";
                            break;
                        case "quit":
                            output = "ok bye";
                            break;
                        default:
                            output = "LOGGEDOUT: error protocol error";
                            break;
                    }
                    break;
            case LOGGEDIN:
                System.out.println("DMAP LOGGEDIN: input: " + input);
                if(input != null){
                     newInput = input;
                     splitInput = input.split("\\s");

                    if(splitInput.length > 1)
                        newInput = splitInput[0];
                    switch(newInput.toLowerCase(Locale.ROOT)) {
                        case "login":
                            output = "error already logged in";
                            break;
                        case "logout":
                            output = "ok";
                            user = "";
                            state = LOGGEDOUT;
                            break;
                        case "quit":
                            output = "ok bye";
                            break;
                        case "show":
                            if (splitInput.length < 2) {
                                output = "error too little arguments";
                                break;
                            }
                            int id;
                            try {
                                id = Integer.parseInt(splitInput[1]);
                            } catch (NumberFormatException e){
                                output = "error wrong format id";
                                break;
                            }
                            Message message = this.thread.showMessage(id, this.user);
                            if (message == null){
                                output = "error unknown message id";
                                break;
                            }

                            this.message = message;
                            output = "from: " + message.getSender();
                            this.state = SHOWING;
                            break;
                        case "list":
                            this.list = this.thread.getAllMessages(this.user);
                            if(list == null) {
                                output = "no mails available";
                                break;
                            }
                            if(!list.isEmpty()){
                                this.state = LISTING;
                                int curid = list.keySet().iterator().next();
                                Message msg = list.get(curid);
                                output = curid + " " + msg.getSender() + " " + msg.getSubject();
                                list.remove(curid);
                            }
                            break;
                        case "delete":
                            if (splitInput.length < 2) {
                                output = "error too little arguments";
                                break;
                            }
                            try {
                                id = Integer.parseInt(splitInput[1]);
                            } catch (NumberFormatException e){
                                output = "error wrong format id";
                                break;
                            }
                            boolean ans = this.thread.delete(id, user);
                            if(ans)
                                output="ok";
                            else
                                output ="error during deleting";
                            break;
                        default:
                            output = "LOGGEDIN: error protocol error";
                            break;
                    }
                }
                break;
            case SHOWING:
                if(input != null) {
                    switch(input){
                        case "0":
                            output = "to: ";
                            List<String> recips = message.getRecipients();
                            for (String recip: recips) {
                                output += recip + ",";
                            }
                            break;
                        case "1":
                            output = "subject: " + message.getSubject();
                            break;
                        case "2":
                            output = "data: " + message.getData();
                            break;
                        case "3":
                            output = "hash: " + message.getHash();
                            break;
                        case "4":
                            output = "ok";
                            this.message = null;
                            this.state = LOGGEDIN;
                            break;
                    }
                }
                break;
            case LISTING: //State, expects null answers from thread to keep listing mails
                if (input == null){
                    if(!list.isEmpty()){
                       int id = list.keySet().iterator().next();
                       Message msg = list.get(id);
                       output = id + " " + msg.getSender() + " " + msg.getSubject();
                       list.remove(id);
                       break;
                    }
                    output = "ok"; //if all mails listed, signal with ok.
                    this.list = null;
                    state = LOGGEDIN;
                    break;
                }
                break;
        }
        System.out.println(output);
        if (state != PLAIN && state != RSAENCRYPTED) {
            AEScrypting encrypter = new AEScrypting(iv, secretKey);
            output = encrypter.Encrypt(output);
        }
        System.out.println("the message sent to the client is: " + output);
        return output;
    }
}
