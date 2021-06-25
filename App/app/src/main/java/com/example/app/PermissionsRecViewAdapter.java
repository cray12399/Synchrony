package com.example.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PermissionsRecViewAdapter extends RecyclerView.Adapter<PermissionsRecViewAdapter
        .ViewHolder>{

    private final Context CONTEXT;
    public HashMap<String, Permission> permissionsNotGranted;

    public PermissionsRecViewAdapter(Context CONTEXT,
                                     HashMap<String, Permission> PERMISSIONS_NOT_GRANTED) {
        this.CONTEXT = CONTEXT;
        this.permissionsNotGranted = PERMISSIONS_NOT_GRANTED;
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
        // Get the indices of permissions not granted so that they can be accurately displayed
        ArrayList<String> indices = new ArrayList<>(permissionsNotGranted.keySet());
        for (Map.Entry<String, Permission> entry: permissionsNotGranted.entrySet()) {
            if (indices.indexOf(entry.getKey()) == position) {
                holder.permissionNameTxt.setText(entry.getKey());

                //TODO Handle permissions rationale.

                holder.permissionGrantedSwitch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String neededPermission = entry.getValue().getManifestPermission();
                        if (neededPermission != null) {
                            if (ActivityCompat.checkSelfPermission(CONTEXT, neededPermission)
                                    != PackageManager.PERMISSION_GRANTED) {
                                int requestCode = entry.getValue().getRequestCode();
                                ActivityCompat.requestPermissions((Activity) CONTEXT,
                                        new String[] {neededPermission},
                                        requestCode);
                            }
                        }
                    }
                });

                holder.permissionGrantedSwitch.setOnCheckedChangeListener(new CompoundButton
                        .OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        String neededPermission = entry.getValue().getManifestPermission();
                        if (neededPermission != null) {
                            if (ActivityCompat.checkSelfPermission(CONTEXT, neededPermission)
                                    != PackageManager.PERMISSION_GRANTED) {
                                // Switch isn't checked unless the permission is granted
                                holder.permissionGrantedSwitch.setChecked(false);

                                int requestCode = entry.getValue().getRequestCode();
                                ActivityCompat.requestPermissions((Activity) CONTEXT,
                                        new String[] {neededPermission},
                                        requestCode);
                            }
                        }
                    }
                });

                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return permissionsNotGranted.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView permissionNameTxt;
        private final SwitchCompat permissionGrantedSwitch;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            permissionNameTxt = itemView.findViewById(R.id.permissionNameText);
            permissionGrantedSwitch = itemView.findViewById(R.id.permissionGrantedSwitch);

        }
    }
}
