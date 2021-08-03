package com.example.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Class used to  display CardViews that allow user to grant permissions on the PermissionsActivity
 */
public class PermissionsRecViewAdapter extends RecyclerView.Adapter<PermissionsRecViewAdapter
        .ViewHolder> {

    // Constructor variables.
    private final Context mContext;
    public HashMap<String, Permission> mPermissionsNotGranted;

    public PermissionsRecViewAdapter(Context CONTEXT,
                                     HashMap<String, Permission> permissionsNotGranted) {
        this.mContext = CONTEXT;
        this.mPermissionsNotGranted = permissionsNotGranted;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.permission_card_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Get the indices of permissions not granted so that they can be accurately displayed.
        ArrayList<String> indices = new ArrayList<>(mPermissionsNotGranted.keySet());
        for (Map.Entry<String, Permission> entry : mPermissionsNotGranted.entrySet()) {
            if (indices.indexOf(entry.getKey()) == position) {
                holder.permissionNameTxt.setText(entry.getKey());

                // Handle switching of permissionGrantedSwitch
                holder.permissionGrantedSwitch
                        .setOnCheckedChangeListener((buttonView, isChecked) -> {
                            String neededPermission = entry.getValue().getManifestPermission();
                            if (neededPermission != null) {
                                if (ActivityCompat.checkSelfPermission(mContext, neededPermission)
                                        != PackageManager.PERMISSION_GRANTED) {
                                    // Switch isn't checked unless the permission is granted
                                    holder.permissionGrantedSwitch.setChecked(false);

                                    getPermission(holder.itemView, entry);

                                }
                            }
                        });

                // Description button so users can understand why they need to give permission
                holder.permissionDescriptionButton.setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle("Why?");
                    builder.setMessage(entry.getValue().getDescription());
                    builder.setPositiveButton("Close",
                            (dialog, which) -> dialog.cancel());
                    AlertDialog dialog = builder.create();
                    dialog.show();
                });
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return mPermissionsNotGranted.size();
    }

    private void getPermission(View itemView, Map.Entry<String, Permission> entry) {
        // Separated entry into separate variables for code readability
        String neededPermission = entry.getValue().getManifestPermission();
        String permissionDescription = entry.getValue().getDescription();
        int requestCode = entry.getValue().getRequestCode();

        // If the app doesn't need to show a rationale, show permission dialog.
        if (!ActivityCompat.shouldShowRequestPermissionRationale((Activity) mContext,
                neededPermission)) {
            ActivityCompat.requestPermissions((Activity) mContext,
                    new String[]{neededPermission},
                    requestCode);
        } else {
            // If not, show SnackBar to show rationale and send user to app settings page.
            Snackbar.make(itemView,
                    permissionDescription,
                    Snackbar.LENGTH_LONG).setAction("Grant...", v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", mContext.getPackageName(),
                                null));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            }).show();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView permissionNameTxt;
        private final SwitchCompat permissionGrantedSwitch;
        private final ImageButton permissionDescriptionButton;
        private final View itemView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            permissionNameTxt = itemView.findViewById(R.id.permissionNameText);
            permissionGrantedSwitch = itemView.findViewById(R.id.permissionGrantedSwitch);
            permissionDescriptionButton = itemView.findViewById(R.id.permissionDescriptionBtn);
            this.itemView = itemView;
        }
    }
}
