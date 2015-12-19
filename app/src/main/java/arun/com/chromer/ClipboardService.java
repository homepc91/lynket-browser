package arun.com.chromer;

import android.app.IntentService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class ClipboardService extends IntentService {
    public ClipboardService() {
        super("ClipboardService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            if (intent != null && intent.hasExtra(Intent.EXTRA_TEXT)) {
                String urlToCopy = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (urlToCopy != null) {
                    ClipboardManager clipboard = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(getPackageName(), urlToCopy);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied " + urlToCopy, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
