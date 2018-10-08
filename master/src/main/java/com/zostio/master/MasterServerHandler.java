package com.zostio.master;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
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

    private ArrayList<MasterServer.ServerRunnable> serverRunnables;
    private ArrayList<Command> commands;

    MasterServerHandler() {

    }

    public void connectToServer(String IP, final MasterServer.ServerRunnable serverRunnable) {
        if (!MasterServer.serverConnected) {
            MasterServer.serverIP = IP;
            serverRunnables = new ArrayList<>();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startConnection();
                        setupStream();
                        MasterServer.serverConnected = true;
                        if (serverRunnable != null && serverRunnable.onSuccess != null) {
                            serverRunnable.activity.runOnUiThread(serverRunnable.onSuccess);
                        }
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                commandSender();
                            }
                        });
                        thread.start();

                        whileChatting();
                    } catch (EOFException eofException) {
                        MasterServer.serverConnected = false;
                        showMessage("Error: Terminated connection to server!");
                    } catch (IOException ioException) {
                        MasterServer.serverConnected = false;
                        if (serverRunnable != null) {
                            serverRunnable.serverAnswer = ioException.getLocalizedMessage();
                        }
                        if (serverRunnable != null && serverRunnable.onError != null) {
                            serverRunnable.activity.runOnUiThread(serverRunnable.onError);
                        }
                        ioException.printStackTrace();
                    } finally {
                        MasterServer.serverConnected = false;
                        closeConnection();
                    }
                }
            });
            thread.start();
        }else {
            showMessage("Error: Already connected to a server!");
        }
    }

    public void startConnection() throws IOException{
        showMessage("Attempting to connect...");
        connection = new Socket(InetAddress.getByName(MasterServer.serverIP), 6789);
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
                        MasterServer.ServerRunnable serverRunnable = serverRunnables.get(i);
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
                                showMessage("Request not found: " + req);
                            }
                        }
                    }
                }
            } catch (ClassNotFoundException classNotFoundException) {
                showMessage("Server sent unknown object");
            }
        } while (MasterServer.serverConnected);
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
                MasterServer.serverIP = "";
                MasterServer.serverConnected = false;
            }catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public void sendCommand(String program, String command, final String details, final MasterServer.ServerRunnable serverRunnable) {
        serverRunnables.add(serverRunnable);

        String commandToSend = program + "#divider#" + command;
        if (details != null) {
            commandToSend = commandToSend + "#divider#" + details;
        }
        showMessage("Attempting to send command: " + commandToSend);

        Command commandClass = new Command();
        commandClass.program = program;
        commandClass.command = commandToSend;
        commandClass.serverRunnable = serverRunnable;
        commands.add(commandClass);
    }

    private void commandSender() {
        for (int i = 0; i < commands.size(); i++) {
            Command currentCommand = commands.get(i);
            Boolean serverNotConnectedWarning = false;
            while(!MasterServer.serverConnected) {
                if (!serverNotConnectedWarning) {
                    showMessage("Server not connected!");
                    serverNotConnectedWarning = true;
                }
            }
            try {
                output.writeObject(currentCommand.serverRunnable.REQUEST_CODE + "#divider#" + currentCommand.command);
                output.flush();
                showMessage("Command sent!");
            }catch (IOException ioException) {
                showMessage("Error sending command: " + currentCommand.command);
                ioException.printStackTrace();
            }
            commands.remove(currentCommand);
        }
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                commandSender();
            }
        }, 1000);
    }

    public void uploadFile(final String startPath, final String endPath, final MasterServer.ServerRunnable serverRunnable) {
        MasterServer.ServerRunnable runnable = new MasterServer.ServerRunnable(serverRunnable.activity, "SENDFILE");
        runnable.onSuccess = new Runnable() {
            @Override
            public void run() {
                showMessage("Attempting to upload file: " + startPath);
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Boolean serverNotConnectedWarning = false;
                        while(!MasterServer.serverConnected) {
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

    public void login(final String username, final String password, final MasterServer.ServerRunnable serverRunnable) {
        final MasterServer.ServerRunnable runnable = new MasterServer.ServerRunnable(serverRunnable.activity, "saltReq");
        runnable.onSuccess = new Runnable() {
            @Override
            public void run() {
                MasterCrypto masterCrypto = new MasterCrypto();
                String hashedPass = masterCrypto.generateHash(password, runnable.serverAnswer);
                sendCommand("usermanager","login",username+"#loginfo;"+hashedPass, serverRunnable);
            }
        };
        runnable.onError = serverRunnable.onError;
        sendCommand("usermanager","getusersalt", username, runnable);
    }

    public void createUser(final String username, final String password, String firstName, String lastName, String userGroup, final MasterServer.ServerRunnable serverRunnable) {
        MasterCrypto masterCrypto = new MasterCrypto();
        String salt = masterCrypto.createSalt();
        String hashedPass = masterCrypto.generateHash(password, salt);
        sendCommand("usermanager","createuser",username+"#loginfo;"+hashedPass+"#loginfo;"+salt+"#loginfo;"+firstName+"#loginfo;"+lastName+"#loginfo;"+userGroup, serverRunnable);
    }

    private class Command {
        String program;
        String command;
        MasterServer.ServerRunnable serverRunnable;
    }
}
