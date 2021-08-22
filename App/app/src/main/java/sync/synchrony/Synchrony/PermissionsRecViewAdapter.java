package sync.synchrony.Synchrony;

import android.Manifest;
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

import com.example.Synchrony.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;


/**
 * Class used to  display CardViews that allow user to grant permissions on the PermissionsActivity
 */
public class PermissionsRecViewAdapter extends RecyclerView.Adapter<PermissionsRecViewAdapter
        .ViewHolder> {

    // Constructor variables.
    private final Context mContext;
    public ArrayList<PermissionsActivity.Permission> mPermissionsNotGranted;

    public PermissionsRecViewAdapter(Context CONTEXT, ArrayList<PermissionsActivity.Permission>
            permissionsNotGranted) {
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
        PermissionsActivity.Permission permission = mPermissionsNotGranted.get(position);
        holder.permissionNameTxt.setText(permission.getPermissionName());

        // Handle switching of permissionGrantedSwitch
        if (permission.ismIsDialogPermission()) {
            holder.permissionGrantedSwitch
                    .setOnCheckedChangeListener((buttonView, isChecked) -> {
                        String neededPermission = permission.getManifestPermission();
                        if (neededPermission != null) {
                            if (ActivityCompat.checkSelfPermission(mContext, neededPermission)
                                    != PackageManager.PERMISSION_GRANTED) {
                                // Switch isn't checked unless the permission is granted
                                holder.permissionGrantedSwitch.setChecked(false);

                                getPermission(holder.itemView, permission);

                            }
                        }
                    });
        } else {
            if (permission.getManifestPermission().equals(
                    Manifest.permission.ACCESS_NOTIFICATION_POLICY)) {
                holder.permissionGrantedSwitch
                        .setOnCheckedChangeListener((buttonView, isChecked) -> {
                            String neededPermission = permission.getManifestPermission();
                            if (neededPermission != null) {
                                holder.permissionGrantedSwitch.setChecked(false);

                                Intent intent = new Intent(
                                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                mContext.startActivity(intent);
                            }
                        });
            }
        }


        // Description button so users can understand why they need to give permission
        holder.permissionDescriptionButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("Why?");
            builder.setMessage(permission.getDescription());
            builder.setPositiveButton("Close",
                    (dialog, which) -> dialog.cancel());
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    public int getItemCount() {
        return mPermissionsNotGranted.size();
    }

    private void getPermission(View itemView,
                               PermissionsActivity.Permission permission) {
        // Separated entry into separate variables for code readability
        String neededPermission = permission.getManifestPermission();
        String permissionDescription = permission.getDescription();
        int requestCode = permission.getRequestCode();

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

    public ArrayList<PermissionsActivity.Permission> getPermissionsNotGranted() {
        return mPermissionsNotGranted;
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
