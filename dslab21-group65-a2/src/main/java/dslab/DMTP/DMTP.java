package dslab.DMTP;

import dslab.Message.Message;

import java.util.Locale;

public class DMTP {

    private static final int WAITING = 0;
    private static final int WORKING = 1;

    private int state = WAITING;

    private dmtpThread thread;

    private boolean to = false;
    private boolean from = false;
    private boolean subject = false;
    private boolean data = false;

    private Message newMessage;

    public DMTP(dmtpThread thread){
        this.thread = thread;
    }

    public String processInput(String input) {
        String output = "";

        switch(state) {
            case WAITING:
                if(input == null)
                    output = "ok DMTP2.0";
                else {
                    String newInput = input;
                    if(input.split("\\s").length > 1)
                        newInput = input.split("\\s")[0];
                    switch(newInput.toLowerCase(Locale.ROOT)){
                        case "begin":
                            output = "ok";
                            newMessage = new Message();
                            from = false;
                            to = false;
                            data = false;
                            subject = false;
                            state = WORKING;
                            break;
                        case "quit":
                            output = "ok bye";
                            break;
                        case "send":
                        case "from":
                        case "to":
                        case "data":
                        case "subject":
                            output = "error no message started - use the 'begin' command";
                            break;
                        default:
                            output = "error protocol error";
                            break;
                    }
                }
                break;
            case WORKING:
                if(input != null){

                    String newInput = input;
                    String[] parts = input.split("\\s");
                    if(parts.length > 1)
                        newInput = parts[0];

                    switch(newInput.toLowerCase(Locale.ROOT)){
                        case "begin":
                            output = "ok started a new message";
                            newMessage = new Message();
                            from = false;
                            to = false;
                            data = false;
                            subject = false;
                            break;
                        case "quit":
                            output = "ok bye";
                            break;
                        case "subject":
                            if (parts.length < 2){
                                output = "error no subject given";
                                break;
                            }
                            output = "ok";
                            newMessage.setSubject(input.substring(8));
                            subject = true;
                            break;
                        case "data":
                            if (parts.length < 2){
                                output = "error no data given";
                                break;
                            }
                            output = "ok";
                            data = true;
                            newMessage.setData(input.substring(5));
                            break;
                        case "from":
                            if (parts.length < 2){
                                output = "error no sender given";
                                break;
                            }
                            output = "ok";
                            from = true;
                            newMessage.setSender(input.substring(5));
                            break;
                        case "hash":
                            if (parts.length < 2){
                                output = "error no hash given";
                                break;
                            }
                            output = "ok";
                            newMessage.setHash(input.substring(5));
                            break;
                        case "to":
                            if (parts.length < 2){
                                output = "error no recipients given";
                                break;
                            }
                            if(parts.length > 2){ //error if there are more whitespaces
                                output = "error wrong format - no whitespace between recipients allowed";
                            }
                            else {
                                String[] recipients = parts[1].split(","); //split up recipients
                                int rec = recipients.length;

                                boolean error = false;


                                for (String recip: recipients) {
                                    if(recip.isEmpty()){ //empty substrings if there were double commas
                                        output = "error wrong format - no double commas allowed";
                                        newMessage.resetRecipients();
                                        to = false;
                                        error = true;
                                        break;
                                    }
                                    if(!recip.contains("@")){ //check validity of address
                                        output = "error address " + recip + " is not a valid email address";
                                        newMessage.resetRecipients();
                                        to = false;
                                        error = true;
                                        break;
                                    }
                                    if(thread.checkDomain(recip.split("@")[1])) { //is domain valid?
                                        if (!thread.checkRecipient(recip)) { //check whether recipient is valid
                                            output = "error unknown recipient "+ recip.split("@")[0]; //if not error
                                            newMessage.resetRecipients();
                                            to = false;
                                            error = true;
                                            break;
                                        }
                                    } else { //if domain invalid, don't count the recipient but add to message
                                        rec--;
                                    }
                                    newMessage.addRecipients(recip);
                                }

                                if(!error){ //if no errors, give ok
                                    output = "ok " + rec;
                                    to = true;
                                }
                            }
                            break;
                        case "send":
                            if(!(to&&from&&subject&&data)){
                                output = "error ";
                                if(!to)
                                    output += "-no recipients specified ";
                                if(!from)
                                    output += "-no sender specified ";
                                if(!subject)
                                    output += "-no subject specified ";
                                if(!data)
                                    output += "-no data specified";
                            } else {
                                output = "ok";
                                this.thread.save(newMessage);
                                state = WAITING;
                            }
                            break;
                        default:
                            output = "error protocol error";
                            break;
                    }

                }
                break;
        }


        return output;
    }

}
