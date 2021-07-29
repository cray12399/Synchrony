package com.example.app;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.concurrent.CopyOnWriteArrayList;

// This class is the RecViewAdapter that contains the entries for the Paired PC's in MainActivity.
public class PCRecViewAdapter extends RecyclerView.Adapter<PCRecViewAdapter.ViewHolder> {
    private final Utils utils;
    private final Context CONTEXT;
    public CopyOnWriteArrayList<PairedPC> connectedPCS;

    public PCRecViewAdapter(Context context, CopyOnWriteArrayList<PairedPC> connectedPCS) {
        this.CONTEXT = context;
        this.connectedPCS = connectedPCS;
        this.utils = Utils.getInstance(context);
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
        holder.pcAddressText.setText(connectedPCS.get(position).getAddress());
        holder.pcTypeText.setText(connectedPCS.get(position).getPCType());

        // Set the CardView to expand itself and show some details about the PC when the
        // expandPCBtn is clicked.
        holder.expandPCBtn.setOnClickListener(v -> {
            if (holder.expanded) {
                holder.expandedLayout.setVisibility(View.GONE);
                holder.expandPCBtn.setBackgroundResource(R.drawable.ic_expand);
                holder.expanded = false;
            } else {
                holder.expandedLayout.setVisibility(View.VISIBLE);
                holder.expandPCBtn.setBackgroundResource(R.drawable.ic_collapse);
                holder.expanded = true;
            }
        });

        // Set the pcActiveSwitch to toggle the Paired PC's sync activity.
        holder.pcActiveSwitch.setChecked(connectedPCS.get(position).isActive());
        holder.pcActiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            utils.setPCActive(connectedPCS.get(position).getAddress(), isChecked);
        });

        // Set the card view to navigate the user to the PCDetailActivity when clicked.
        holder.parent.setOnClickListener(v -> {
            Intent showPCDetailIntent = new Intent(CONTEXT, PCDetailActivity.class);
            showPCDetailIntent.putExtra(PCDetailActivity.PC_ADDRESS_KEY,
                    connectedPCS.get(position).getAddress());
            CONTEXT.startActivity(showPCDetailIntent);
        });
    }

    @Override
    public int getItemCount() {
        return connectedPCS.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        MaterialCardView parent;
        ImageButton expandPCBtn;
        LinearLayout expandedLayout;
        TextView pcNameText;
        TextView pcAddressText;
        TextView pcTypeText;
        SwitchCompat pcActiveSwitch;
        boolean expanded = false;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            parent = itemView.findViewById(R.id.parent);
            expandPCBtn = itemView.findViewById(R.id.expandPCBtn);
            expandedLayout = itemView.findViewById(R.id.expandedLayout);
            pcNameText = itemView.findViewById(R.id.pcNameText);
            pcAddressText = itemView.findViewById(R.id.pcAddressText);
            pcTypeText = itemView.findViewById(R.id.pcTypeText);
            pcActiveSwitch = itemView.findViewById(R.id.pcActiveSwitch);
        }
    }
}
