package com.example.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class PCRecViewAdapter extends RecyclerView.Adapter<PCRecViewAdapter.ViewHolder> {

    private final Context CONTEXT;
    public ArrayList<ConnectedPC> connectedPCS;

    public PCRecViewAdapter(Context CONTEXT, ArrayList<ConnectedPC> connectedPCS) {
        this.CONTEXT = CONTEXT;
        this.connectedPCS = connectedPCS;
    }

    @NonNull
    @Override
    public PCRecViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                          int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.pc_card_view,
                parent, false);
        return new PCRecViewAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PCRecViewAdapter.ViewHolder holder, int position) {
        holder.pcNameText.setText(connectedPCS.get(position).getName());

        holder.pcActiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !connectedPCS.get(position).isActivePC()) {
                holder.pcActiveSwitch.setChecked(false);

                AlertDialog.Builder builder = new AlertDialog.Builder(CONTEXT);
                builder.setTitle("Set Active PC?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    connectedPCS.get(position).setActivePC(true);
                    holder.pcActiveSwitch.setChecked(true);
                });
                builder.setNegativeButton("No", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            } else if (!isChecked && connectedPCS.get(position).isActivePC()) {
                holder.pcActiveSwitch.setChecked(true);

                AlertDialog.Builder builder = new AlertDialog.Builder(CONTEXT);
                builder.setTitle("Disable Active PC?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    connectedPCS.get(position).setActivePC(false);
                    holder.pcActiveSwitch.setChecked(false);
                });
                builder.setNegativeButton("No", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return connectedPCS.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView pcNameText;
        SwitchCompat pcActiveSwitch;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            pcNameText = itemView.findViewById(R.id.pcNameText);
            pcActiveSwitch = itemView.findViewById(R.id.pcActiveSwitch);
        }
    }
}
