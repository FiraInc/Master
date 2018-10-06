package com.zostio.master;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;

public class MasterServerHandler {
    private Socket connection;
    private ObjectInputStream input;
    private InputStream inputFile;
    private ObjectOutputStream output;

    private ArrayList<ServerRunnable> serverRunnables;

    static String serverIP = "";

    public Boolean serverConnected = false;

    MasterServerHandler() {

    }

    public void connectToServer(String IP, final ServerRunnable serverRunnable) {
        if (serverIP.equals("") && !serverConnected) {
            serverIP = IP;
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

    public void startConnection() throws IOException{
        showMessage("Attempting to connect...");
        connection = new Socket(InetAddress.getByName(serverIP), 6789);
        showMessage("Connected to: " + connection.getInetAddress().getHostName());
    }

    public void setupStream() throws IOException {
        output = new ObjectOutputStream(connection.getOutputStream());
        output.flush();
        input = new ObjectInputStream(connection.getInputStream());
        inputFile = connection.getInputStream();
        showMessage("Streams have been setup");
    }

    public String nextIsFile;

    public void whileChatting() throws IOException {
        do {
            try {
                showMessage("Received Answer");

                showMessage("String");
                showMessage("RUNNING");

                String unformattedAnswer = (String) input.readObject();
                showMessage("Serveranswer: " + unformattedAnswer);
                String[] splittedAnswer = unformattedAnswer.split("#divider#");

                String req = splittedAnswer[0];
                String answer = splittedAnswer[1];

                if (answer.equals("nextisfile")) {
                    nextIsFile = req;
                }else {
                    if (unformattedAnswer.toLowerCase().equals("server - close")) {
                        closeConnection();
                        return;
                    }

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
                }
            } catch (ClassNotFoundException classNotFoundException) {
                showMessage("Server sent unknown object");
            }
        } while (serverConnected);
    }

    public void closeConnection() {
        showMessage("Closing connection...");
        if (output != null) {
            try {
                output.close();
                if (input != null) {
                    input.close();
                }
                if (inputFile != null) {
                    inputFile.close();
                }
                connection.close();
                serverIP = "";
                serverConnected = false;
            }catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public void sendCommand(String program, String command, final String details, final ServerRunnable serverRunnable) {
        serverRunnables.add(serverRunnable);

        String commandToSend = program + "#divider#" + command;

        if (details != null) {
            commandToSend = commandToSend + "#divider#" + details;
        }

        showMessage("Attempting to send command: " + commandToSend);

        final String finalCommandToSend = commandToSend;
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
                    output.writeObject(serverRunnable.REQUEST_CODE + "#divider#" + finalCommandToSend);
                    output.flush();
                    showMessage("Command sent!");
                }catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void uploadFile(final String startPath, final String endPath, final ServerRunnable serverRunnable) {
        ServerRunnable runnable = new ServerRunnable(serverRunnable.activity, "SENDFILE");
        runnable.onSuccess = new Runnable() {
            @Override
            public void run() {
                showMessage("Attempting to upload file: " + startPath);
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
                            File file = new File(Environment.getExternalStorageDirectory() + "/ZOSTIO/" + startPath);
                            if (!file.exists()) {
                                file.mkdirs();
                                showMessage("File created");
                            }
                            FileInputStream fileInputStream = new FileInputStream(file);
                            byte[] b = new byte[2000];
                            fileInputStream.read(b,0,b.length);
                            OutputStream outputStream = connection.getOutputStream();
                            outputStream.write(b,0,b.length);

                            showMessage("File sent!");
                            serverRunnable.activity.runOnUiThread(serverRunnable.onSuccess);
                        }catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                });
                thread.start();
            }
        };
        runnable.onError = serverRunnable.onError;
        sendCommand("filemanager","sendfileuser", endPath, runnable);
    }

    public void showMessage(String message) {
        Master.log("MasterSever: " + message);
    }

    public void login(final String username, final String password, final ServerRunnable serverRunnable) {
        final ServerRunnable runnable = new ServerRunnable(serverRunnable.activity, "saltReq");
        runnable.onSuccess = new Runnable() {
            @Override
            public void run() {
                MasterCrypto masterCrypto = new MasterCrypto();
                String hashedPass = masterCrypto.generateHash(password, runnable.serverAnswer);
                sendCommand("usermanager","login",username+"#loginfo;"+hashedPass, serverRunnable);
            }
        };
        runnable.onError = new Runnable() {
            @Override
            public void run() {
                serverRunnable.onError.run();
            }
        };
        sendCommand("usermanager","getusersalt", username, runnable);
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
