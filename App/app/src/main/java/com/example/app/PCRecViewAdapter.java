package com.example.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.CopyOnWriteArrayList;

public class PCRecViewAdapter extends RecyclerView.Adapter<PCRecViewAdapter.ViewHolder> {

    private final Utils utils;
    private final Context CONTEXT;
    public CopyOnWriteArrayList<PairedPC> connectedPCS;

    public PCRecViewAdapter(Context CONTEXT, CopyOnWriteArrayList<PairedPC> connectedPCS) {
        this.CONTEXT = CONTEXT;
        this.connectedPCS = connectedPCS;
        this.utils = Utils.getInstance(CONTEXT);

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
        holder.pcActiveSwitch.setChecked(connectedPCS.get(position).isActive());

        holder.pcActiveSwitch.setChecked(connectedPCS.get(position).isActive());

        holder.pcActiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            utils.getPairedPCS().get(position).setActive(isChecked);
            utils.savePairedPCSToDevice();
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
