package com.zostio.master;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.SyncFailedException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import static android.content.ContentValues.TAG;

public class Master {

    public static void log(String text) {
        if (text == null || text.isEmpty()) {
            Log.e("Master says", "NULL");
        }else {
            Log.e("Master says", text);
        }
    }

    public static class readFile {
        public static String local(@NonNull Context context, @NonNull String filename) {
            try {
                FileInputStream fis = context.openFileInput(filename);
                InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                Log.e("Read: ", "#Filename: " + filename + " #textRead: " + sb.toString() + "#PLEASEIGNORE;");
                return sb.toString();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return "0";
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return "0";
            } catch (IOException e) {
                e.printStackTrace();
                return "0";
            }
        }

        public static String external (@NonNull String directory, @NonNull String filename) {
            File directories = new File(Environment.getExternalStorageDirectory(), directory);
            if (!directories.mkdirs()) {
                Master.log("Failed to create directory");
            }
            File file = new File(Environment.getExternalStorageDirectory(), directory + filename);
            StringBuilder text = new StringBuilder();
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line);
                }
                br.close();
            }
            catch (IOException e) {
                //You'll need to add proper error handling here
            }

            if (text == null || String.valueOf(text).equals("")) {
                return String.valueOf("0");
            }else {
                return String.valueOf(text);
            }
        }

        public static Bitmap internalStorageToBitmap(Context context, String picName){
            Bitmap b = null;
            FileInputStream fis = null;
            try {
                fis = context.openFileInput(picName);
                b = BitmapFactory.decodeStream(fis);
            }
            catch (FileNotFoundException e) {
                Log.d(TAG, "file not found");
                e.printStackTrace();
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return b;
        }

        public static Bitmap externalStorageToBitmap(Context context, String picName){
            File file = new File(Environment.getExternalStorageDirectory(), picName);
            Bitmap b = null;
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                //fis = context.openFileInput(picName);
                b = BitmapFactory.decodeStream(fis);
            }
            catch (FileNotFoundException e) {
                Log.d(TAG, "file not found");
                e.printStackTrace();
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return b;
        }
    }

    public static class writeFile {
        public static void local (@NonNull Context context, @NonNull String filename, @NonNull String value) {
            if (!value.equals("0")) {
                FileOutputStream outputStream;
                try {
                    outputStream = context.openFileOutput(filename , Context.MODE_PRIVATE);
                    outputStream.write(value.getBytes());
                    outputStream.close();
                    Log.e("Written: ", "#Filename: " + filename + " #textToWrite: " + value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else {
                File file = new File(context.getFilesDir(), filename);
                if (file.delete()) {
                    Log.e("Deleted file", "Deleted file: " + filename);
                }else {
                    Log.e("Deleted file", "Failed to deleted file: " + filename);
                }
            }
        }

        public static void external (@NonNull String directory, @NonNull String filename, @NonNull String value) {
            File dir = new File(Environment.getExternalStorageDirectory(), directory);
            File file = new File(Environment.getExternalStorageDirectory(), directory + filename);
            if (!dir.mkdirs()) {
                Log.e("Error", "Error creating directory" + filename);
            }
            FileOutputStream fileOutput = null;
            try {
                fileOutput = new FileOutputStream(file);
                OutputStreamWriter outputStreamWriter=new OutputStreamWriter(fileOutput);
                outputStreamWriter.write(value);
                outputStreamWriter.flush();
                fileOutput.getFD().sync();
                outputStreamWriter.close();
                Log.e("File Saved", "Saved file " + filename);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("Error saving", "Error saving file " + filename);
            }
        }

        public static boolean bitmapToInternalStorage(@NonNull Context context, @NonNull Bitmap b, @NonNull String picName, String quality){
            File dir = context.getFilesDir();
            File file = new File(dir, "picName");
            boolean deleted = file.delete();


            FileOutputStream fos = null;
            try {
                fos = context.openFileOutput(picName, Context.MODE_PRIVATE);
                if (quality == null || quality.isEmpty()) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }else {
                    int qualityInt = 0;
                    try {
                        qualityInt = Integer.parseInt(quality);
                    }catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (qualityInt <= 0 || qualityInt > 100) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }else {
                        b.compress(Bitmap.CompressFormat.PNG, qualityInt, fos);
                    }
                }
                return true;
            }
            catch (FileNotFoundException e) {
                Log.d(TAG, "file not found");
                e.printStackTrace();
                return false;
            }
        }
    }

    public static boolean fileExist(@NonNull  Context context, @NonNull String fileName){
        File file = context.getFileStreamPath(fileName);
        return file.exists();
    }

    public static int dpToPx(@NonNull Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    public static class timeTracker {
        public static class timeTrackerElement {

            long timeInMillis;
            String name;

            public timeTrackerElement (String name) {
                this.name = name;
            }
        }

        static ArrayList<timeTrackerElement> timeElements = new ArrayList<>();

        public static void start (@NonNull String name) {
            if (!name.isEmpty()) {
                timeTrackerElement trackerElement = new timeTrackerElement(name);
                trackerElement.timeInMillis = System.currentTimeMillis();
                timeElements.add(trackerElement);
            }else {
                Master.log("Error: timetracker name can't be empty");
            }
        }

        public static void print(@NonNull String name) {
            for (int i = 0; i < timeElements.size(); i++) {
                if (timeElements.get(i).name.equals(name)) {
                    long millisGone = System.currentTimeMillis() - timeElements.get(i).timeInMillis;
                    Log.e("Timetracker", name + " took: " + millisGone + " milliseconds!");
                }
            }
        }
    }

    public static class graphicsTool {
        @NonNull
        public static Bitmap drawableToBitmap(@NonNull Drawable drawable) {
            final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bmp);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bmp;
        }

        public static BitmapDrawable bitmapToBitmapDrawable (@NonNull Context context, @NonNull Bitmap bitmap) {
            return new BitmapDrawable(context.getResources(), bitmap);
        }

        public static BitmapDrawable downloadBitmapFromURL(@NonNull Context context, @NonNull String url) {
            Bitmap bitmap = null;
            try {
                InputStream inputStream = new URL(url).openStream();   // Download Image from URL
                bitmap = BitmapFactory.decodeStream(inputStream);       // Decode Bitmap
                inputStream.close();
            } catch (Exception e) {
                Log.d(TAG, "Exception 1, Something went wrong!");
                e.printStackTrace();
            }

            if (bitmap == null) {
                return null;
            }else {
                return new BitmapDrawable(context.getResources(), bitmap);
            }
        }

        private static final float BITMAP_SCALE = 0.4f;
        private static final float BLUR_RADIUS = 7.5f;

        public static BitmapDrawable blurBitmapToBitmapDrawable(@NonNull Context context, @NonNull Bitmap image) {
            int width = Math.round(image.getWidth() * BITMAP_SCALE);
            int height = Math.round(image.getHeight() * BITMAP_SCALE);

            Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

            RenderScript rs = RenderScript.create(context);
            ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            theIntrinsic.setRadius(BLUR_RADIUS);
            theIntrinsic.setInput(tmpIn);
            theIntrinsic.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);

            BitmapDrawable d = new BitmapDrawable(context.getResources(), outputBitmap);

            return d;
        }



        static int screenHeight = 0;
        static int screenWidth = 0;

        public static int getScreenWidth(@NonNull Activity activity) {
            if (screenWidth == 0) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                screenHeight = displayMetrics.heightPixels;
                screenWidth = displayMetrics.widthPixels;
            }
            return screenWidth;
        }

        public static int getScreenHeight(@NonNull Activity activity) {
            if (screenHeight == 0) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                screenHeight = displayMetrics.heightPixels;
                screenWidth = displayMetrics.widthPixels;
            }
            return screenHeight;
        }
    }

    public static boolean hasPermissions(Context context, String permission) {
        if (context != null && permission != null) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPackageInstalled(@NonNull Context context, @NonNull String packagename) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static class ServerManager {

        private Socket connection;
        private ObjectInputStream input;
        private ObjectOutputStream output;

        private ArrayList<ServerRunnable> serverRunnables;

        String serverIP = "";

        public Boolean serverConnected = false;

        public ServerManager () {

        }

        public void connectToServer(String serverIP) {
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
                            whileChatting();
                        } catch (EOFException eofException) {
                            serverConnected = false;
                            showMessage("Terminated connection to server!");
                        } catch (IOException ioException) {
                            serverConnected = false;
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
                        if (serverRunnables.get(i).REQUEST_CODE.equals(req)) {
                            serverRunnables.get(i).serverAnswer = answer;
                            serverRunnables.get(i).afterAnswer.run();
                            serverRunnables.remove(i);
                            //TODO HERE WE GO
                        }
                    }

                }catch (ClassNotFoundException classNotFoundException) {
                    showMessage("Server sent unknown object");
                }
            }while (serverConnected);
        }

        public void closeConnection() {
            showMessage("Closing connection...");
            if (output != null) {
                try {
                    output.close();
                    input.close();
                    connection.close();
                    serverIP = "";
                    serverConnected = false;
                }catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }

        public void sendCommand(final String command, final ServerRunnable serverRunnable) {
            while(!serverConnected) {

            }

            serverRunnables.add(serverRunnable);

            showMessage("Attempting to send command...");
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        output.writeObject(serverRunnable.REQUEST_CODE + "#divider#" + command);
                        output.flush();
                        showMessage("Command sent!");
                    }catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            });
            thread.start();
        }

        private void showMessage(String message) {
            Master.log("MasterSever: " + message);
        }

        public static class ServerRunnable {

            public String serverAnswer;
            public String REQUEST_CODE;
            public Runnable afterAnswer;

            public ServerRunnable(String REQUEST_CODE) {
                this.afterAnswer = afterAnswer;
                this.REQUEST_CODE = REQUEST_CODE;
            }

            public void addRunnable(Runnable afterAnswer) {
                this.afterAnswer = afterAnswer;
            }

        }
    }
}
