package com.zostio.master;

import android.app.Activity;
import android.content.Context;

import java.util.Random;

public class MasterServer {

    public static MasterServerHandler masterServerHandler;
    public static String serverIP = "";

    public static Boolean serverConnected = false;

    public MasterServer() {

    }

    public static void setup(Context context) {
        masterServerHandler = new MasterServerHandler();
    }

    public static void connectToServer(String IP) {
        connectToServer(IP, null);
    }

    public static void connectToServer(String IP, final ServerRunnable serverRunnable) {
        if (masterServerHandler == null) {
            masterServerHandler = new MasterServerHandler();
        }
        if (!serverConnected) {
            masterServerHandler.connectToServer(IP, serverRunnable);
        }else {
            serverRunnable.serverAnswer = "Already connected to a server: ";
            serverRunnable.activity.runOnUiThread(serverRunnable.onError);
        }
    }

    public static class ServerCommands {
        public static void stopServer(ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "stopserver", "", serverRunnable);
        }
    }

    public static class UserData {
        public static void loadString(String path, ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("filemanager", "getstring", path, serverRunnable);
        }

        public static void writeString(String path, String content, ServerRunnable serverRunnable) {
            String details = path + "#newInfo;" + content;
            masterServerHandler.sendCommand("filemanager", "savestring", details, serverRunnable);
        }
    }

    public static class UserManager {
        public static void login(String username, String password, ServerRunnable serverRunnable) {
            masterServerHandler.login(username, password, serverRunnable);
        }

        public static void getAllUserNames(ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("usermanager", "getallusernames", "", serverRunnable);
        }

        public static void getAllUserInfo(String UID, ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("usermanager", "getalluserinfo", UID, serverRunnable);
        }

        public static void deleteUser(String UID, ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("usermanager", "deleteuser", UID, serverRunnable);
        }

        public static void changeUserDetails(String UID, String username, String firstName, String lastName, String userGroup, ServerRunnable serverRunnable) {
            String details = UID + "#newInfo;" + username + "#newInfo;" + firstName + "#newInfo;" + lastName + "#newInfo;" + userGroup;
            masterServerHandler.sendCommand("usermanager", "deleteuser", details, serverRunnable);
        }
    }

    public static class ServerManager {
        public static void closeServer(ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "stopserver", "", serverRunnable);
        }
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
