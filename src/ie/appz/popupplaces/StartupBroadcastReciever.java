package ie.appz.popupplaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import ie.appz.popupplaces.services.PopupTrigger;

public class StartupBroadcastReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences settings = context.getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
        Log.i(this.getClass().getName(), "Startup Broadcast Recieved. Notification Service Disabled: " + settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false));
        if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
            Log.i(this.getClass().getName(), "Starting PopupTrigger.");
            context.startService(new Intent(context, PopupTrigger.class));
        }
    }
}
