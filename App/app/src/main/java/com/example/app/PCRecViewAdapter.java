package com.example.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
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

import java.util.ArrayList;
import java.util.Objects;

/**
 * This class is the RecViewAdapter that contains the entries for the Paired PC's
 * in MainActivity.
 */
public class PCRecViewAdapter extends RecyclerView.Adapter<PCRecViewAdapter.ViewHolder> {
    // Constructor variables.
    private final Context mContext;
    public ArrayList<PairedPC> mListedPCS;

    public PCRecViewAdapter(Context context, ArrayList<PairedPC> listedPCS) {
        this.mContext = context;
        this.mListedPCS = listedPCS;
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PairedPC listedPC = mListedPCS.get(position);
        System.out.println(mListedPCS);
        holder.pcNameText.setText(listedPC.getName());
        holder.pcAddressText.setText(listedPC.getAddress());
        holder.pcTypeText.setText(listedPC.getPCType());

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
        holder.pcSyncingSwitch.setChecked(mListedPCS.get(position).isSyncing());
        holder.pcSyncingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.notifySyncChanged(mContext.getApplicationContext(), listedPC.getAddress());

            Objects.requireNonNull(
                    Utils.getPairedPC(listedPC.getAddress())).setSyncing(isChecked);
        });

        // Set the card view to navigate the user to the PCDetailActivity when clicked.
        holder.parent.setOnClickListener(v -> {
            Intent showPCDetailsIntent = new Intent(mContext, PCDetailsActivity.class);
            showPCDetailsIntent.putExtra(PCDetailsActivity.PC_ADDRESS_KEY,
                    mListedPCS.get(position).getAddress());
            mContext.startActivity(showPCDetailsIntent);
        });

        holder.syncReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Utils.SYNC_CHANGED_ACTION)) {
                    if (intent.getStringExtra(Utils.RECIPIENT_ADDRESS_KEY)
                            .equals(listedPC.getAddress())) {
                        holder.pcSyncingSwitch.setChecked(Objects.requireNonNull(
                                Utils.getPairedPC(listedPC.getAddress())).isSyncing());
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.SYNC_CHANGED_ACTION);
        mContext.getApplicationContext().registerReceiver(holder.syncReceiver, filter);
    }

    @Override
    public int getItemCount() {
        return mListedPCS.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        MaterialCardView parent;
        ImageButton expandPCBtn;
        LinearLayout expandedLayout;
        TextView pcNameText;
        TextView pcAddressText;
        TextView pcTypeText;
        SwitchCompat pcSyncingSwitch;
        boolean expanded = false;
        BroadcastReceiver syncReceiver;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            parent = itemView.findViewById(R.id.parent);
            expandPCBtn = itemView.findViewById(R.id.expandPCBtn);
            expandedLayout = itemView.findViewById(R.id.expandedLayout);
            pcNameText = itemView.findViewById(R.id.pcNameText);
            pcAddressText = itemView.findViewById(R.id.pcAddressText);
            pcTypeText = itemView.findViewById(R.id.pcTypeText);
            pcSyncingSwitch = itemView.findViewById(R.id.pcSyncingSwitch);
        }
    }
}
