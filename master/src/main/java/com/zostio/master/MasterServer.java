package com.zostio.master;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;

public class MasterServer {
    private Socket connection;
    private ObjectInputStream input;
    private ObjectOutputStream output;

    private ArrayList<ServerRunnable> serverRunnables;

    String serverIP = "";

    public Boolean serverConnected = false;

    Context context;

    public MasterServer (Context context) {
        this.context = context;
    }

    public void connectToServer(String serverIP) {
        connectToServer(serverIP, null);
    }

    public void connectToServer(String serverIP, final ServerRunnable serverRunnable) {
        if (this.serverIP.equals("") && !serverConnected) {
            this.serverIP = serverIP;
            serverRunnables = new ArrayList<>();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startConnection();
                        setupStream();
                        serverConnected = true;
                        if (serverRunnable != null && serverRunnable.onSuccess != null) {
                            serverRunnable.onSuccess.run();
                        }
                        whileChatting();
                    } catch (EOFException eofException) {
                        serverConnected = false;
                        showMessage("Terminated connection to server!");
                    } catch (IOException ioException) {
                        serverConnected = false;
                        if (serverRunnable != null) {
                            serverRunnable.serverAnswer = ioException.getLocalizedMessage();
                        }
                        if (serverRunnable != null && serverRunnable.onError != null) {
                            serverRunnable.activity.runOnUiThread(serverRunnable.onError);
                        }
                        ioException.printStackTrace();
                    } finally {
                        serverConnected = false;
                        closeConnection();
                    }
                }
            });
            thread.start();
        }else {
            showMessage("Already connected to a server!");
        }
    }

    private void startConnection() throws IOException{
        showMessage("Attempting to connect...");
        connection = new Socket(InetAddress.getByName(serverIP), 6789);
        showMessage("Connected to: " + connection.getInetAddress().getHostName());
    }

    private void setupStream() throws IOException {
        output = new ObjectOutputStream(connection.getOutputStream());
        output.flush();
        input = new ObjectInputStream(connection.getInputStream());
        showMessage("Streams have been setup");
    }

    private void whileChatting() throws IOException {
        do {
            try {
                showMessage("Received Answer");

                showMessage("String");
                showMessage("RUNNING");

                String unformattedAnswer = (String) input.readObject();

                if (unformattedAnswer.toLowerCase().equals("server - close")) {
                    closeConnection();
                    return;
                }
                String[] splittedAnswer = unformattedAnswer.split("#divider#");

                String req = splittedAnswer[0];
                String answer = splittedAnswer[1];

                showMessage("Serveranswer: " + unformattedAnswer);

                for (int i = 0; i < serverRunnables.size(); i++) {
                    ServerRunnable serverRunnable = serverRunnables.get(i);
                    if (serverRunnable.REQUEST_CODE.equals(req)) {
                        if (answer.toLowerCase().startsWith("error: ")) {
                            serverRunnable.serverAnswer = answer.toLowerCase().split("error: ")[1];
                            if (serverRunnable.onError != null) {
                                serverRunnable.activity.runOnUiThread(serverRunnable.onError);
                            }else {
                                showMessage("Received error, bud didn't know what to do!");
                            }
                        }else {
                            serverRunnable.serverAnswer = answer;
                            if (serverRunnable.onSuccess != null) {
                                serverRunnable.activity.runOnUiThread(serverRunnable.onSuccess);
                            }else {
                                showMessage("Received success, bud didn't know what to do!");
                            }
                        }
                        serverRunnables.remove(i);
                    }else {
                        if (i == serverRunnables.size()-1) {
                            showMessage("Request not found");
                        }
                    }
                }
                /*
                showMessage("Byte");
                byte[] b = new byte[20002];
                FileOutputStream fr = new FileOutputStream(context.getFilesDir() + "testmeg.txt");
                input.read(b, 0, b.length);
                fr.write(b, 0, b.length);
                */
            } catch (ClassNotFoundException classNotFoundException) {
                showMessage("Server sent unknown object");
            }
        } while (serverConnected);
    }

    private void closeConnection() {
        showMessage("Closing connection...");
        if (output != null) {
            try {
                output.close();
                if (input != null) {
                    input.close();
                }
                connection.close();
                serverIP = "";
                serverConnected = false;
            }catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public void sendCommand(String command, final String details, final ServerRunnable serverRunnable) {
        serverRunnables.add(serverRunnable);

        showMessage("Attempting to send command: " + command + "#prog#" + details);

        final String commandToSend = command + "#prog#" +details;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Boolean serverNotConnectedWarning = false;
                while(!serverConnected) {
                    if (!serverNotConnectedWarning) {
                        showMessage("Server not connected!");
                        serverNotConnectedWarning = true;
                    }
                }
                try {
                    output.writeObject(serverRunnable.REQUEST_CODE + "#divider#" + commandToSend);
                    output.flush();
                    showMessage("Command sent!");
                }catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void sendCommand(final String command, final ServerRunnable serverRunnable) {
        sendCommand(command, "", serverRunnable);
    }

    private void showMessage(String message) {
        Master.log("MasterSever: " + message);
    }

    public void login(final String username, final String password, final ServerRunnable serverRunnable) {
        final ServerRunnable runnable = new ServerRunnable(serverRunnable.activity, "saltReq");
        runnable.onSuccess = new Runnable() {
            @Override
            public void run() {
                MasterCrypto masterCrypto = new MasterCrypto();
                String hashedPass = masterCrypto.generateHash(password, runnable.serverAnswer);
                sendCommand("login",username+"#loginfo;"+hashedPass, serverRunnable);
            }
        };
        runnable.onError = new Runnable() {
            @Override
            public void run() {
                serverRunnable.onError.run();
            }
        };
        sendCommand("getusersalt",username, runnable);
    }

    public static class ServerRunnable {

        public String serverAnswer;
        public String REQUEST_CODE;
        public Runnable onSuccess;
        public Runnable onError;
        Activity activity;

        public ServerRunnable(Activity activity, String request) {
            this.activity = activity;
            Random random = new Random();
            REQUEST_CODE = request + String.valueOf(random.nextInt(100000) + random.nextInt(100000));
        }

        public ServerRunnable(Activity activity, String REQUEST_CODE, Runnable onSuccess, Runnable onError) {
            this.activity = activity;
            this.REQUEST_CODE = REQUEST_CODE;
            this.onSuccess = onSuccess;
            this.onError = onError;
        }

        public void addOnSuccess(Runnable onSuccess) {
            this.onSuccess = onSuccess;
        }

        public void addOnError(Runnable onError) {
            this.onError = onError;
        }

    }
}
