package com.zostio.master;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.io.EOFException;
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

    public MasterServer () {

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
                        if (serverRunnable != null && serverRunnable.serverAnswer != null) {
                            serverRunnable.serverAnswer = ioException.getMessage();
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
        do{
            try {
                showMessage("Received Answer");
                String unformattedAnswer = (String) input.readObject();
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
            }catch (ClassNotFoundException classNotFoundException) {
                showMessage("Server sent unknown object");
            }
        }while (serverConnected);
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

        showMessage("Attempting to send command...");

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

    public void login(String username, String password, final ServerRunnable serverRunnable) {
        sendCommand("login",username+"#loginfo;"+password, serverRunnable);
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
