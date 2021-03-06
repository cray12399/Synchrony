package sync.synchrony.Synchrony;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.Synchrony.R;

/**
 * This class is used to display the loading page and determine what the app
 * should do on startup.
 */
public class StartupActivity extends AppCompatActivity {
    // Logging tag variables.
    String TAG = "StartupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        // TODO Remove handler when app finished. I just like looking at the logo screen.
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            // Check for first run to determine if app should show PermissionsActivity, etc.
            if (Utils.isFirstRun()) {
                // If first run, go to PermissionsActivity.
                Log.d(TAG, "Not First startup, navigating to PermissionsActivity");
                Intent permissionIntent = new Intent(StartupActivity.this,
                        PermissionsActivity.class);
                startActivity(permissionIntent);
            } else {
                // If not, go to MainActivity.
                Log.d(TAG, "Subsequent startup, navigating to MainActivity");
                Intent mainActivityIntent = new Intent(StartupActivity.this,
                        MainActivity.class);
                startActivity(mainActivityIntent);
            }
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }, 1500);


    }
}