package dslab.transfer;

import at.ac.tuwien.dsg.orvell.Shell;

import java.util.concurrent.ExecutorService;

public class ShellListenerThread extends Thread{

    private TransferServer transferServer;
    private Shell shell;
    private ExecutorService ThreadPool;

    public ShellListenerThread(Shell shell) {
        this.shell = shell;
    }

    @Override
    public void run() {
        shell.run();
    }


}
