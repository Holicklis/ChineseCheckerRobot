package hku.cs.fyp24057.chinesecheckerrobot;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

public class IpConfigDialog {
    private Context context;
    private String currentIp;
    private OnIpConfirmedListener listener;
    private String title;

    public interface OnIpConfirmedListener {
        void onIpConfirmed(String newIp);
    }

    public IpConfigDialog(Context context, String currentIp, String title, OnIpConfirmedListener listener) {
        this.context = context;
        this.currentIp = currentIp;
        this.listener = listener;
        this.title = title;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_ip_config, null);

        TextView titleText = dialogView.findViewById(R.id.dialogTitle);
        EditText ipInput = dialogView.findViewById(R.id.ipInput);
        Button confirmButton = dialogView.findViewById(R.id.confirmButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        titleText.setText(title);
        ipInput.setText(currentIp);

        AlertDialog dialog = builder.setView(dialogView).create();

        confirmButton.setOnClickListener(v -> {
            String newIp = ipInput.getText().toString().trim();
            if (isValidIp(newIp)) {
                listener.onIpConfirmed(newIp);
                dialog.dismiss();
            } else {
                ipInput.setError("Invalid IP address");
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}