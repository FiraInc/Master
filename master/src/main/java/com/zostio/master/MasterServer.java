package com.zostio.master;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.io.File;
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

    public static void sendCommand(String program, String command, String details, ServerRunnable serverRunnable) {
        masterServerHandler.sendCommand(program, command, details, serverRunnable);
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

        public static void createUser(String username, String firstName, String lastName, String password, String userGroup, ServerRunnable serverRunnable) {
            masterServerHandler.createUser(username, password, firstName, lastName, userGroup, serverRunnable);
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
        //public static void checkUpdate();
        public static void closeServer(ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "stopserver", "", serverRunnable);
        }

        public static void checkUpdate (ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "checkupdate", "", serverRunnable);
        }

        public static void updateServer (ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "updateserver", "", serverRunnable);
        }

        public static void getBuildNumber (ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "getbuild", "", serverRunnable);
        }

        public static void getVersion (ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "getversion", "", serverRunnable);
        }

        public static void restartServer (ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("serverhandler", "restartserver", "", serverRunnable);
        }
    }

    public static class DatabaseManager {
        String databasePath;

        public DatabaseManager(String databasePath, String databaseName) {
            this.databasePath = fixedPath(databasePath, databaseName);
        }

        public void createDatabase(String databasePath, String databaseName, ServerRunnable serverRunnable) {
            //todo what about deletedatabase or deletefile in general?
            this.databasePath = fixedPath(databasePath, databaseName);
            masterServerHandler.sendCommand("databasemanager", "createdatabase", this.databasePath, serverRunnable);
        }

        public DatabaseReference getReference(String key) {
            return new DatabaseReference(databasePath, key);
        }

        private String fixedPath(String path, String databaseName) {
            String realPath = path;

            if (!databaseName.endsWith(".json")) {
                databaseName = databaseName + ".json";
            }

            if (path.endsWith("/")) {
                realPath = realPath + databaseName;
            }else {
                realPath = realPath + "/" + databaseName;
            }

            return realPath;
        }
    }

    public static class DatabaseReference {

        String databasePath;
        String path;

        DatabaseReference(String databasePath, String path) {
            this.databasePath = databasePath;
            this.path = path;
        }
        public DatabaseReference getChild(String key) {
            if (path.isEmpty()) {
                return new DatabaseReference(databasePath, key);
            }else {
                return new DatabaseReference(databasePath, path + "/" + key);
            }

        }

        public void getValue(ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("databasemanager", "getvalue", databasePath + "#newInfo;" + path, serverRunnable);
        }

        public void setRealTimeListener(ServerRunnable serverRunnable) {
            serverRunnable.doNotRemove = true;
            masterServerHandler.sendCommand("databasemanager", "realtimelistener", databasePath + "#newInfo;" + path, serverRunnable);
        }

        public void setValue(String value, ServerRunnable serverRunnable) {
            String details = databasePath + "#newInfo;" + path + "#newInfo;" + value;
            masterServerHandler.sendCommand("databasemanager", "setvalue", details, serverRunnable);
        }

        public void listChilds(ServerRunnable serverRunnable) {
            String details = databasePath + "#newInfo;" + path;
            masterServerHandler.sendCommand("databasemanager", "getallkeys", details, serverRunnable);
        }
    }

    public static class FileManager {
        public static void sendFile(final String phonePath, String path, String fileName, final ServerRunnable serverRunnable) {
            String endPath = fixedPath(path, fileName);
            masterServerHandler.sendFile(phonePath, endPath, serverRunnable);
        }

        public static void requestFile(final String phonePath, String path, String fileName, final ServerRunnable serverRunnable){
            String fixedPath = fixedPath(path, fileName);

            File phoneFile = new File(phonePath);
            if (phoneFile.exists()) {
                phoneFile.delete();
            }
            phoneFile.mkdirs();

            serverRunnable.details = fixedPath;
            serverRunnable.REQUEST_CODE = serverRunnable.REQUEST_CODE.substring(0, 13);

            masterServerHandler.sendCommand("filemanager", "requestfile", fixedPath, serverRunnable);
        }

        private static String fixedPath(String path, String fileName) {
            String realPath = path;
            if (path.endsWith("/")) {
                realPath = realPath + fileName;
            }else {
                realPath = realPath + "/" + fileName;
            }

            return realPath;
        }

        public static void listFiles(String path, ServerRunnable serverRunnable) {
            masterServerHandler.sendCommand("filemanager", "listfiles", path, serverRunnable);
        }
    }


    public static class ServerRunnable {
        Activity activity;

        public String serverAnswer;
        public String REQUEST_CODE;
        public Runnable onSuccess;
        public Runnable onError;

        public int progressPercentage = 0;
        public Runnable onProgress;

        public String details;

        public boolean doNotRemove = false;

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

        public void addOnProgress(Runnable onProgress) {
            this.onProgress = onProgress;
        }

    }
}
