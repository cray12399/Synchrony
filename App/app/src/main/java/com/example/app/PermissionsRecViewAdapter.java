package com.example.app;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PermissionsRecViewAdapter extends RecyclerView.Adapter<PermissionsRecViewAdapter.ViewHolder>{

    private Context context;
    private Activity activity;
    private HashMap<String, String> permissionsNotGranted;
    private HashMap<String, Integer> requestCodes = new HashMap<>();

    private TextView permissionNameTxt;
    private SwitchCompat permissionGrantedSwitch;

    public PermissionsRecViewAdapter(Context context,
                                     Activity activity,
                                     HashMap<String, String> permissionsNotGranted) {
        this.context = context;
        this.permissionsNotGranted = permissionsNotGranted;
        this.activity = activity;

        int code = 1;
        for (Map.Entry<String, String> entry: permissionsNotGranted.entrySet()) {
            requestCodes.put(entry.getValue(), code);
            code += 1;
        }
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
        ArrayList<String> indexes = new ArrayList<>(permissionsNotGranted.keySet());
        for (Map.Entry<String, String> entry: permissionsNotGranted.entrySet()) {
            if (indexes.indexOf(entry.getKey()) == position) {
                permissionNameTxt.setText(entry.getKey());
            }
        }
    }

    @Override
    public int getItemCount() {
        return permissionsNotGranted.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            permissionNameTxt = itemView.findViewById(R.id.permissionNameText);
            permissionGrantedSwitch = itemView.findViewById(R.id.permissionGrantedSwitch);

        }
    }
}
