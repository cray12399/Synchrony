package com.example.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.CopyOnWriteArrayList;

public class PCRecViewAdapter extends RecyclerView.Adapter<PCRecViewAdapter.ViewHolder> {

    private final Context CONTEXT;
    public CopyOnWriteArrayList<ConnectedPC> connectedPCS;
    private LinearLayoutManager layoutManager;

    public PCRecViewAdapter(Context CONTEXT, LinearLayoutManager layoutManager,
                            CopyOnWriteArrayList<ConnectedPC> connectedPCS) {
        this.CONTEXT = CONTEXT;
        this.layoutManager = layoutManager;
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
        holder.pcActiveSwitch.setChecked(connectedPCS.get(position).isActivePC());

        holder.pcActiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            connectedPCS.get(position).setActivePC(isChecked);
            holder.pcActiveSwitch.setChecked(isChecked);

            if (connectedPCS.get(position).isActivePC()) {
                connectedPCS.get(position).initWork(CONTEXT);
            } else {
                connectedPCS.get(position).cancelWork(CONTEXT);
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
