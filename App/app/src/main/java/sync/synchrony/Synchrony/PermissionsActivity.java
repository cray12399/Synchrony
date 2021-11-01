package sync.synchrony.Synchrony;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Synchrony.R;

import java.io.Serializable;
import java.util.ArrayList;


/**
 * Activity used to request permissions from user on first startup.
 */
public class PermissionsActivity extends AppCompatActivity {

    // Constant used to determine needed permissions. The key-value pair is used within
    // PermissionsActivity to obtain details of a permission. Permissions are defined this
    // way so that they can be coded in dynamically as needed.
    private final ArrayList<Permission> mNeededPermissions = new ArrayList<Permission>() {{
        add(new Permission(
                "Receive SMS",
                Manifest.permission.RECEIVE_SMS,
                "Receive SMS permission is needed to receive SMS from PC.",
                true,
                100));
        add(new Permission(
                "Send SMS",
                Manifest.permission.SEND_SMS,
                "Send SMS permission is needed to send SMS from PC.",
                true,
                101));
        add(new Permission(
                "Read SMS",
                Manifest.permission.READ_SMS,
                "Read SMS permission is needed to read SMS from PC.",
                true,
                102));
        add(new Permission(
                "Access Contacts",
                Manifest.permission.READ_CONTACTS,
                "Contacts permission needed to access contacts from PC.",
                true,
                103));
        add(new Permission(
                "Access Call Logs",
                Manifest.permission.READ_CALL_LOG,
                "Call Logs permission needed to access call history from PC.",
                true,
                104));
        add(new Permission(
                "Handle Calls",
                Manifest.permission.CALL_PHONE,
                "Telephony permission needed to make calls from PC.",
                true,
                105));
        add(new Permission(
                "Notification Access",
                Manifest.permission.ACCESS_NOTIFICATION_POLICY,
                "Notification access needed to send notifications to PC.",
                false,
                106
        ));
    }};

    // UI variables.
    private PermissionsRecViewAdapter mPermissionsRecViewAdapter;
    private LinearLayoutManager mPermissionsRecViewLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        // Used to display permissions in a card view layout with switches the user can press.
        // UI variables.
        RecyclerView permissionsRecView = findViewById(R.id.permissionsRecView);

        // Check which permissions have not been granted
        ArrayList<Permission> permissionsNotGranted = new ArrayList<>();
        for (Permission permission : mNeededPermissions) {
            if (ContextCompat.checkSelfPermission(PermissionsActivity.this,
                    permission.getManifestPermission()) == PermissionChecker.PERMISSION_DENIED) {
                permissionsNotGranted.add(permission);
            }
        }

        // Display permissions in a linear card view layout.
        mPermissionsRecViewAdapter = new PermissionsRecViewAdapter(this,
                permissionsNotGranted);
        mPermissionsRecViewLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);
        permissionsRecView.setLayoutManager(mPermissionsRecViewLayoutManager);
        permissionsRecView.setAdapter(mPermissionsRecViewAdapter);

        // Allows user to skip permissions screen if they choose.
        TextView skipBtn = findViewById(R.id.skipBtn);
        skipBtn.setOnClickListener(v -> {
            // Confirm just in case they accidentally pressed the button.
            AlertDialog.Builder builder = new AlertDialog
                    .Builder(PermissionsActivity.this);
            builder.setTitle("Skip Permissions?");
            builder.setMessage("Are you sure you wish to skip giving permissions? You will " +
                    "need to give permissions later!");
            builder.setNegativeButton("Cancel", null);
            builder.setPositiveButton("Skip", (dialog, which) -> continueToMainActivity());

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // If there are permission request results, handle them.
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                for (Permission permission :
                        mPermissionsRecViewAdapter.getPermissionsNotGranted()) {
                    // Check which request code was provided and match it to proper permission
                    if (permission.getRequestCode() == requestCode) {
                        int position = mPermissionsRecViewAdapter
                                .getPermissionsNotGranted().indexOf(permission);
                        View view = mPermissionsRecViewLayoutManager
                                .findViewByPosition(position);
                        if (view != null) {
                            // Check the switch and remove the CardView
                            SwitchCompat permissionGrantedSwitch = view
                                    .findViewById(R.id.permissionGrantedSwitch);
                            permissionGrantedSwitch.setChecked(true);
                            mPermissionsRecViewAdapter
                                    .getPermissionsNotGranted().remove(permission);
                            mPermissionsRecViewAdapter.notifyItemRemoved(
                                    position);
                        }
                        break;
                    }
                }
            }
        }
        // If all permissions are granted, continue to MainActivity
        if (mPermissionsRecViewAdapter.getItemCount() == 0) {
            continueToMainActivity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Clone of permissionsNotGranted list to avoid exceptions when removing items
        // from within foreach iterator.
        ArrayList<Permission> permissionsToCheck = new ArrayList<>(
                mPermissionsRecViewAdapter.getPermissionsNotGranted());
        // Iterate over permissions upon resuming of PermissionsActivity
        // and update list if any permissions are granted.
        for (Permission permission : permissionsToCheck) {
            if (ContextCompat.checkSelfPermission(this,
                    permission.getManifestPermission()) ==
                    PackageManager.PERMISSION_GRANTED) {
                int position = mPermissionsRecViewAdapter
                        .getPermissionsNotGranted().indexOf(permission);
                mPermissionsRecViewAdapter.getPermissionsNotGranted().remove(position);
                mPermissionsRecViewAdapter.notifyItemRemoved(position);
            } else if (permission.getManifestPermission().equals(
                    Manifest.permission.ACCESS_NOTIFICATION_POLICY)) {
                NotificationManager notificationManager = (NotificationManager)
                        getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    int position = mPermissionsRecViewAdapter
                            .getPermissionsNotGranted().indexOf(permission);
                    mPermissionsRecViewAdapter.getPermissionsNotGranted().remove(position);
                    mPermissionsRecViewAdapter.notifyItemRemoved(position);
                }
            }
        }

        // If all permissions are granted, continue to MainActivity
        if (mPermissionsRecViewAdapter.getItemCount() == 0) {
            continueToMainActivity();
        }
    }

    @Override
    public void onBackPressed() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
    }

    private void continueToMainActivity() {
        Intent mainActivityIntent = new Intent(PermissionsActivity.this,
                MainActivity.class);
        startActivity(mainActivityIntent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /**
     * Class used to contain permission details within PermissionsActivity.
     */
    public static class Permission implements Serializable {
        // Constructor variables.
        private final String mPermissionName;
        private final String mManifestPermission;
        private final String mDescription;
        private final boolean mIsDialogPermission;
        private final int mRequestCode;

        public Permission(String permissionName, String manifestPermission,
                          String description, boolean isDialogPermission,
                          int requestCode) {
            this.mPermissionName = permissionName;
            this.mManifestPermission = manifestPermission;
            this.mDescription = description;
            this.mIsDialogPermission = isDialogPermission;
            this.mRequestCode = requestCode;
        }

        public String getPermissionName() {
            return mPermissionName;
        }

        public String getManifestPermission() {
            return mManifestPermission;
        }

        public String getDescription() {
            return mDescription;
        }

        public boolean ismIsDialogPermission() {
            return mIsDialogPermission;
        }

        public int getRequestCode() {
            return mRequestCode;
        }
    }
}

