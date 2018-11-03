package com.zostio.master;

import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MasterServerHandler {
    protected Socket connection;
    private InputStream input;
    private OutputStream output;

    protected ArrayList<MasterServer.ServerRunnable> serverRunnables;
    private ArrayList<Command> commands;

    MasterServerHandler() {

    }

    public void connectToServer(String IP, final MasterServer.ServerRunnable serverRunnable) {
        if (!MasterServer.serverConnected) {
            MasterServer.serverIP = IP;
            serverRunnables = new ArrayList<>();

            commands = new ArrayList<>();
            commandLooper();

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startConnection();
                        setupStream();
                        MasterServer.serverConnected = true;

                        if (isDeviceRegistered()) {
                            MasterServer.ServerRunnable serverRunnable1 = new MasterServer.ServerRunnable(serverRunnable.activity, "connectDevice");
                            serverRunnable1.onSuccess = new Runnable() {
                                @Override
                                public void run() {
                                    if (serverRunnable.onSuccess != null) {
                                        serverRunnable.activity.runOnUiThread(serverRunnable.onSuccess);
                                    }
                                }
                            };
                            serverRunnable1.onError = new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(serverRunnable.activity.getApplicationContext(), "Device not verified!", Toast.LENGTH_SHORT).show();
                                }
                            };
                            String ID = Settings.Secure.getString(serverRunnable.activity.getApplicationContext().getContentResolver(),
                                    Settings.Secure.ANDROID_ID);
                            sendCommand("serverhandler", "verifydevice", ID, serverRunnable1);
                        }else {}

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

    private boolean isDeviceRegistered() {
        return true;
    }

    public void startConnection() throws IOException{
        showMessage("Attempting to connect...");
        connection = new Socket(InetAddress.getByName(MasterServer.serverIP), 6789);
        showMessage("Connected to: " + connection.getInetAddress().getHostName());
    }

    public void setupStream() throws IOException {
        output = connection.getOutputStream();
        output.flush();
        input = connection.getInputStream();
        showMessage("Streams have been setup");
    }

    public void whileChatting() throws IOException {
        do {
            byte[] dataInfo = new byte[2];
            input.read(dataInfo);
            String dataType = new String(dataInfo, StandardCharsets.UTF_8);

            if (dataType.equals("10")) {
                showMessage("Receiving String");

                byte[] byteLength = new byte[10];
                input.read(byteLength);
                String byteLengthString = new String(byteLength, StandardCharsets.UTF_8);
                showMessage("ByteLengthString: " + byteLengthString);

                byte[] stringRead = new byte[Integer.parseInt(byteLengthString)];
                input.read(stringRead);
                String message = new String(stringRead, StandardCharsets.UTF_8);

                String[] splittedAnswer = message.split("#divider#");
                String req = splittedAnswer[0];
                String answer = splittedAnswer[1];

                showMessage("Message received: " + answer);

                for (int i = 0; i < serverRunnables.size(); i++) {
                    MasterServer.ServerRunnable serverRunnable = serverRunnables.get(i);
                    if (serverRunnable.REQUEST_CODE.equalsIgnoreCase(req)) {
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
                                showMessage("Received success, but serverAnswer.onSuccess is null: " + serverRunnable.REQUEST_CODE);
                            }
                        }


                        if (!serverRunnable.doNotRemove) {
                            serverRunnables.remove(i);
                            Master.log("Removed from runnableList: " + serverRunnable.REQUEST_CODE);
                        }else {
                            Master.log("Did not remove: " + serverRunnable.REQUEST_CODE);
                        }
                    }else {
                        if (i == serverRunnables.size()-1) {
                            showMessage("Request not found: " + req);
                        }
                        Master.log(serverRunnable.REQUEST_CODE + " IS NOT " + req);
                    }
                }
            }else if (dataType.equals("11")) {
                showMessage("Receiving file");
                byte[] reqByte = new byte[13];
                input.read(reqByte);
                String req = new String(reqByte, StandardCharsets.UTF_8);

                for (int i = 0; i < serverRunnables.size(); i++) {
                    showMessage(req + ";" + serverRunnables.get(i).REQUEST_CODE);
                    MasterServer.ServerRunnable serverRunnable = serverRunnables.get(i);
                    if (serverRunnable.REQUEST_CODE.equalsIgnoreCase(req)) {
                        byte[] fileSizeByte = new byte[13];
                        input.read(fileSizeByte);
                        String fileSize = new String(fileSizeByte, StandardCharsets.UTF_8);

                        Master.log("FileSize: " + fileSize);

                        serverRunnable.serverAnswer = downloadFile(serverRunnable, Integer.parseInt(fileSize), input);

                        if (serverRunnable.serverAnswer.startsWith("Error: ")) {
                            if (serverRunnable.onError != null) {
                                serverRunnable.activity.runOnUiThread(serverRunnable.onError);
                            }else {
                                showMessage("Received error, but serverAnswer.onError is null: " + serverRunnable.REQUEST_CODE);
                            }
                        }else {
                            if (serverRunnable.onSuccess != null) {
                                serverRunnable.activity.runOnUiThread(serverRunnable.onSuccess);
                            }else {
                                showMessage("Received success, but serverAnswer.onSuccess is null: " + serverRunnable.REQUEST_CODE);
                            }
                        }


                        if (!serverRunnable.doNotRemove) {
                            serverRunnables.remove(i);
                            Master.log("Removed from runnableList: " + serverRunnable.REQUEST_CODE);
                        }else {
                            Master.log("Did not remove: " + serverRunnable.REQUEST_CODE);
                        }
                    }else {
                        if (i == serverRunnables.size()-1) {
                            showMessage("Request not found: " + req);
                        }
                        Master.log(serverRunnable.REQUEST_CODE + " IS NOT " + req);
                    }
                }
            }
        } while (MasterServer.serverConnected);
    }

    private String downloadFile(MasterServer.ServerRunnable serverRunnable, int fileSize, InputStream input) {
        File file = new File(Environment.getExternalStorageDirectory(), serverRunnable.details);
        if(!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }catch (Exception e) {}
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file.getAbsolutePath());
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            return file.getAbsolutePath();
        }
        byte[] buffer = new byte[4096];

        // Send file size in separate msg
        int read;
        int totalRead = 0;
        int remaining = fileSize;
        try {
            while((read = input.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                totalRead += read;
                remaining -= read;
                System.out.println("read " + totalRead + " bytes.");
                fos.write(buffer, 0, read);

                serverRunnable.progressPercentage = (int)(totalRead * 100.0 / fileSize + 0.5);
                serverRunnable.activity.runOnUiThread(serverRunnable.onProgress);
            }
            showMessage("Done reading!!!");
            fos.close();
            showMessage("Written to storage!");
            return file.getAbsolutePath();
        } catch (IOException e) {
            showMessage("Could not write to storage!");
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    };

    private int counter = 0;

    public void sendFile(final String startPath, final String endPath, final MasterServer.ServerRunnable serverRunnable) {
        counter = 0;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(startPath);
                try {
                    FileInputStream fis = new FileInputStream(file.getAbsolutePath());
                    byte[] buffer = new byte[4096];

                    String startInfo;

                    String bytes = String.valueOf(file.length());
                    showMessage(bytes);
                    int byteLength = bytes.length();
                    String zeros = "";

                    for (int i = byteLength; i < 13; i++) {
                        zeros = zeros + "0";
                    }

                    bytes = zeros + bytes;

                    byte[] endPathInBytes = endPath.getBytes("UTF-8");
                    String pathLength = String.valueOf(endPathInBytes.length);
                    zeros = "";

                    for (int i = pathLength.length(); i < 4; i++) {
                        zeros = zeros + "0";
                    }

                    pathLength = zeros + pathLength;

                    startInfo = "11" + pathLength + endPath + bytes;
                    byte[] byteInfo = startInfo.getBytes(StandardCharsets.UTF_8);
                    byte[] combined = new byte[buffer.length + byteInfo.length];

                    while (fis.read(buffer) > 0) {
                        if (counter == 0) {
                            System.arraycopy(byteInfo,0,combined,0,byteInfo.length);
                            System.arraycopy(buffer,0,combined,byteInfo.length,buffer.length);
                            output.write(combined);
                        }else {
                            output.write(buffer);
                        }
                        long doneSending = counter*4096;
                        serverRunnable.progressPercentage = (int)(doneSending * 100.0 / file.length() + 0.5);
                        serverRunnable.activity.runOnUiThread(serverRunnable.onProgress);
                        counter = counter + 1;
                    }
                    output.flush();

                    fis.close();
                    //output.close();
                    showMessage("File sent!");
                    serverRunnable.activity.runOnUiThread(serverRunnable.onSuccess);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void closeConnection() {
        showMessage("Closing connection...");
        if (output != null) {
            try {
                output.close();
                if (input != null) {
                    input.close();
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

    private boolean allSent = true;

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
                String message = currentCommand.serverRunnable.REQUEST_CODE + "#divider#" + currentCommand.command;
                byte[] messageInBytes = message.getBytes("UTF-8");
                String bytes = String.valueOf(messageInBytes.length);
                int byteLength = bytes.length();
                String zeros = "";
                for (int b = byteLength; b < 10; b++) {
                    zeros = zeros + "0";
                }

                message = "10"+zeros+bytes+message;
                byte[] b = message.getBytes(StandardCharsets.UTF_8);

                output.write(b);
                output.flush();
                showMessage("Command sent: " + currentCommand.command);
            }catch (IOException ioException) {
                showMessage("Error sending command: " + currentCommand.command);
                ioException.printStackTrace();
            }
            commands.remove(currentCommand);
        }
        allSent = true;
    }

    private Handler commandHandler;
    private Runnable commandRunnable;

    private void commandLooper() {
        if (commandHandler == null) {
            commandHandler = new Handler();
            commandRunnable = new Runnable() {
                @Override
                public void run() {
                    commandLooper();
                }
            };
        }

        if (allSent && MasterServer.serverConnected) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    allSent = false;
                    commandSender();
                }
            });
            thread.start();
        }

        commandHandler.postDelayed(commandRunnable, 50);
    }

    public void showMessage(String message) {
        Master.log("MasterSever: " + message);
    }

    public void login(final String username, final String password, final MasterServer.ServerRunnable serverRunnable) {
        final MasterServer.ServerRunnable runnable = new MasterServer.ServerRunnable(serverRunnable.activity, "saltReq");
        runnable.onSuccess = new Runnable() {
            @Override
            public void run() {
                Master.log("Received salt");
                MasterCrypto masterCrypto = new MasterCrypto();
                String hashedPass = masterCrypto.generateHash(password, runnable.serverAnswer);
                sendCommand("usermanager","login",username+"#loginfo;"+hashedPass, serverRunnable);
                Master.log("Sending hashed password");
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
