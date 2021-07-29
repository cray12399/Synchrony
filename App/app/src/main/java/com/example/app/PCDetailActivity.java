package com.example.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import org.w3c.dom.Text;

// This class shows all of the PC's details and also contains additional options for the user
// to use to sync with the client PC.
public class PCDetailActivity extends AppCompatActivity {
    public static final String PC_ADDRESS_KEY = "pcAddressKey";
    private PairedPC pairedPC;
    private Button syncButton;
    private Button sendFileButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pc_detail);

        // Get the relevant Paired PC so that the activity can get its
        // current information.
        Intent previousIntent = getIntent();
        Utils utils = Utils.getInstance(this);
        pairedPC = utils.getPairedPC(previousIntent.getStringExtra(PC_ADDRESS_KEY));

        TextView pcNameText = findViewById(R.id.pcNameText);
        TextView pcAddressText = findViewById(R.id.pcAddressText);
        TextView pcTypeText = findViewById(R.id.pcTypeText);
        TextView pcSyncText = findViewById(R.id.pcSyncText);

        // Show the Paired PC's information to the user.
        pcNameText.setText(pairedPC.getName());
        pcAddressText.setText(pairedPC.getAddress());
        pcTypeText.setText(pairedPC.getPCType());

        // Set the back button to go back to the MainActivity.
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            Intent mainActivityIntent = new Intent(this, MainActivity.class);
            // Set flags to create a new MainActivity just to make sure we are not running its
            // pcRecViewUpdateThread more than once.
            mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(mainActivityIntent);
        });

        // Set the pcActiveSwitch to mark the PC as active or inactive from the details activity.
        SwitchCompat pcActiveSwitch = findViewById(R.id.pcActiveSwitch);
        pcActiveSwitch.setChecked(pairedPC.isActive());
        pcActiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pairedPC.setActive(isChecked);
        });

        // This button is used to copy the user's clipboard and send it to the client PC.
        Button sendClipboardButton = findViewById(R.id.sendClipboardButton);
        sendClipboardButton.setOnClickListener(v -> {
            ClipboardManager clipboardManager =
                    (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
            ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip.getItemCount() > 0) {
                ClipData.Item item = primaryClip.getItemAt(0);
                if (item != null) {
                    String clipboard = item.getText().toString();
                    pairedPC.setSendClipboard(clipboard);
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        // In case the user navigated here using the notification, I set onBackPressed so that
        // it will navigate them back to the MainActivity
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(mainActivityIntent);
    }
}