package hku.cs.fyp24057.chinesecheckerrobot;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BoardDetectionClient {
    private static final String TAG = "BoardDetectionClient";
    private static final int DEFAULT_PORT = 5001;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String serverUrl;

    public BoardDetectionClient(String serverIp) {
        this(serverIp, DEFAULT_PORT);
    }

    public BoardDetectionClient(String serverIp, int port) {
        // Remove any whitespace and trailing slashes from the IP
        serverIp = serverIp.trim().replaceAll("/+$", "");

        // Format the base URL
//        this.serverUrl = String.format("http://%s:%d", serverIp, port);
        this.serverUrl = "https://chinesecheckerrobot-detection-6jg9.onrender.com";
        //web service now

        // Initialize OkHttpClient with timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Log.d(TAG, "Initialized with server URL: " + serverUrl);
    }

    public interface DetectionCallback {
        void onSuccess(List<String> boardState);
        void onError(String error);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    public void uploadEmptyBoard(Bitmap boardImage, DetectionCallback callback) {
        new Thread(() -> {
            try {
                String base64Image = bitmapToBase64(boardImage);

                JSONObject json = new JSONObject();
                json.put("image", base64Image);

                Request request = new Request.Builder()
                        .url(serverUrl + "/upload_empty_board")
                        .post(RequestBody.create(json.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response " + response);
                    }

                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);

                    if (jsonResponse.has("error")) {
                        callback.onError(jsonResponse.getString("error"));
                    } else {
                        callback.onSuccess(null); // No board state for empty board upload
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error uploading empty board", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void detectCurrentState(Bitmap boardImage, DetectionCallback callback) {
        new Thread(() -> {
            try {
                String base64Image = bitmapToBase64(boardImage);

                JSONObject json = new JSONObject();
                json.put("image", base64Image);

                Request request = new Request.Builder()
                        .url(serverUrl + "/detect_current_state")
                        .post(RequestBody.create(json.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response " + response);
                    }

                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);

                    if (jsonResponse.has("error")) {
                        callback.onError(jsonResponse.getString("error"));
                    } else {
                        JSONArray boardState = jsonResponse.getJSONArray("board_state");
                        List<String> boardStateList = new ArrayList<>();
                        for (int i = 0; i < boardState.length(); i++) {
                            boardStateList.add(boardState.getString(i));
                        }
                        callback.onSuccess(boardStateList);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error detecting current state", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
}