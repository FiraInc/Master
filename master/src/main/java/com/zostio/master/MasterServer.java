package com.zostio.master;

import android.content.Context;

public class MasterServer {

    public static MasterServerHandler masterServerHandler;

    public MasterServer() {

    }

    public static void setup(Context context) {
        masterServerHandler = new MasterServerHandler();
    }

    public static void connectToServer(String IP) {
        connectToServer(IP, null);
    }

    public static void connectToServer(String IP, final MasterServerHandler.ServerRunnable serverRunnable) {
        if (masterServerHandler == null) {
            masterServerHandler = new MasterServerHandler();
        }
        if (!masterServerHandler.serverConnected) {
            masterServerHandler.connectToServer(IP, serverRunnable);
        }else {
            serverRunnable.serverAnswer = "Already connected to a server: ";
            serverRunnable.activity.runOnUiThread(serverRunnable.onError);
        }
    }

    public static class ServerCommands {
        public static void stopServer(MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "stopserver", "", serverRunnable);
        }
    }

    public static class UserData {
        public static void loadString(String path, MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("filemanager", "getstring", path, serverRunnable);
        }

        public static void writeString(String path, String content, MasterServerHandler.ServerRunnable serverRunnable) {
            String details = path + "#newInfo;" + content;
            masterServerHandler.sendCommand("filemanager", "savestring", details, serverRunnable);
        }
    }

    public static class UserManager {
        public static void login(String username, String password, MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.login(username, password, serverRunnable);
        }

        public static void getAllUserNames(MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("usermanager", "getallusernames", "", serverRunnable);
        }

        public static void getAllUserInfo(String UID, MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("usermanager", "getalluserinfo", UID, serverRunnable);
        }

        public static void deleteUser(String UID, MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("usermanager", "deleteuser", UID, serverRunnable);
        }

        public static void changeUserDetails(String UID, String username, String firstName, String lastName, String userGroup, MasterServerHandler.ServerRunnable serverRunnable) {
            String details = UID + "#newInfo;" + username + "#newInfo;" + firstName + "#newInfo;" + lastName + "#newInfo;" + userGroup;
            masterServerHandler.sendCommand("usermanager", "deleteuser", details, serverRunnable);
        }
    }

    public static class ServerManager {
        public static void closeServer(MasterServerHandler.ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "stopserver", "", serverRunnable);
        }
    }
}
