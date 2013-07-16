package ie.appz.popupplaces.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationStatusCodes;
import com.google.common.collect.HashBiMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import ie.appz.popupplaces.FireableLocation;
import ie.appz.popupplaces.PlaceOpenHelper;
import ie.appz.popupplaces.R;
import ie.appz.popupplaces.ReminderMap_Activity;

public class PopupTrigger_Geofences extends Service implements TextToSpeech.OnInitListener,
        GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener, LocationClient.OnAddGeofencesResultListener, LocationClient.OnRemoveGeofencesResultListener {
    private static final int POPUP_PLACE_REACHED = 1;
    public static String NotificationLongitude = "notification_longitude";
    public static String NotificationLatitude = "notification_latitude";
    public static String GEOFENCE_COLUMN_NAME_MAP = "geofence_column_name_map";
    public static String POPPED_PLACES = "popped_places";
    public static String BOUNDARY = "boundary";
    public static float ENTRY_BOUNDARY = 40f;
    public static float EXIT_BOUNDARY = 80f;
    public static String GEOFENCE_TRANSITIONED = "geofence_transitioned";
    private static NotificationManager mNotificationManager;
    Location lastLocation;
    TextToSpeech mTextToSpeech;
    boolean textToSpeech_Initialized = false;
    boolean hasRestarted = false;
    // popupTreeMap stores locations keyed to how far the user is from those
    // locations
    private TreeMap<Float, FireableLocation> popupTreeMap;
    private HashBiMap<Integer, Geofence> mIntegerGeofenceHashBiMap;
    private LocationClient mLocationClient;
    private LocationRequest mLocationRequest;
    private PendingIntent mGeofencePendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        lastLocation = new Location("Engage");
        popupTreeMap = new TreeMap<Float, FireableLocation>();
        //mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mTextToSpeech = new TextToSpeech(this, this);
        mLocationClient = new LocationClient(this, this, this);
        mLocationRequest = new LocationRequest()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1);
        Intent intent = new Intent(this, PopupTrigger_Geofences.class);
        intent.putExtra(PopupTrigger_Geofences.GEOFENCE_TRANSITIONED, true);
        mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mIntegerGeofenceHashBiMap = HashBiMap.create();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
        if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
            Log.i(this.getClass().getName(), "PopupTrigger_Geofences Service has received onStartCommand");
            initializeLocation_UpdateFromDatabase();

            ArrayList<Integer> poppedPlaces = new ArrayList<Integer>();
            ArrayList<Integer> unPoppedPlaces = new ArrayList<Integer>();
            if (intent != null) {
                // First check for errors
                if (LocationClient.hasError(intent)) {
                    // Get the error code with a static method
                    int errorCode = LocationClient.getErrorCode(intent);
                    // Log the error
                    Log.e("ReceiveTransitionsIntentService",
                            "Location Services error: " +
                                    Integer.toString(errorCode));
                } else {

                    String[] mapStringArray = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE).getString(PopupTrigger_Geofences.GEOFENCE_COLUMN_NAME_MAP, "").split(" ");

                    List<Geofence> triggerList =
                            LocationClient.getTriggeringGeofences(intent);
                    if (triggerList != null) {
                        for (Geofence triggeredGeofence : triggerList) {
                            Integer columnId = null;

                            for (String string : mapStringArray) {
                                Log.d(this.getClass().getName(), "Mapped String: " + string);
                                String[] splitString = string.split(",");
                                if (splitString[0].equals(triggeredGeofence.getRequestId())) {
                                    columnId = Integer.parseInt(splitString[1]);
                                    if (LocationClient.getGeofenceTransition(intent) == Geofence.GEOFENCE_TRANSITION_ENTER) {
                                        Log.w(this.getClass().getName(), "ColumnId: " + columnId + " Gefence: " + triggeredGeofence.toString());
                                        if (columnId != null) {
                                            poppedPlaces.add(columnId);
                                        }
                                    } else if (LocationClient.getGeofenceTransition(intent) == Geofence.GEOFENCE_TRANSITION_EXIT) {
                                        Log.w(this.getClass().getName(), "ColumnId: " + columnId + " Gefence: " + triggeredGeofence.toString());
                                        if (!triggeredGeofence.getRequestId().equals(PopupTrigger_Geofences.BOUNDARY))
                                            unPoppedPlaces.add(columnId);
                                    }
                                }
                            }
                        }
                        if (poppedPlaces.size() > 0) {
                            for (int columnId : poppedPlaces) {
                                popPlace(columnId);
                            }
                        }
                        if (unPoppedPlaces.size() > 0) {
                            for (int columnId : unPoppedPlaces) {
                                unPopPlace(columnId);
                            }
                        }
                        //initializeLocation_UpdateMap();

                    }
                }
            }
            initializeLocation_UpdateMap();
        }
        return START_STICKY;
    }

    private void initializeLocation_UpdateFromDatabase() {
        Location oldLocation = null;

        if (mLocationClient.isConnected()) {
            oldLocation = mLocationClient.getLastLocation();
            if (oldLocation.getTime() - System.currentTimeMillis() < 4000)
                updateFromDatabase(oldLocation);
            else
                mLocationClient.requestLocationUpdates(mLocationRequest, this);
        } else if (!mLocationClient.isConnecting())
            mLocationClient.connect();
    }

    private void updateFromDatabase(Location oldLocation) {

        TreeMap<Float, FireableLocation> iteratePopupTMap = new TreeMap<Float, FireableLocation>();
        iteratePopupTMap.putAll(popupTreeMap);
        popupTreeMap.clear();
        PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
        Cursor placeCursor = placeOpenHelper.getPlaces();
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        float wifiOffset = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isAvailable() ? 1 : 2;

        if (placeCursor.getCount() >= 1) {
            if (placeCursor.moveToFirst()) {
                do {
                    boolean onTree = false;
                    FireableLocation databaseLocation = new FireableLocation(
                            "FromDatabase");
                    databaseLocation.setLatitude(placeCursor.getDouble(0));
                    databaseLocation.setLongitude(placeCursor.getDouble(1));
                    databaseLocation.setColumnId(placeCursor.getInt(3));
                    if (databaseLocation.distanceTo(oldLocation) < PopupTrigger_Geofences.ENTRY_BOUNDARY * wifiOffset) {
                        databaseLocation.setFired(true);
                    }
                    for (Entry<Float, FireableLocation> entry : iteratePopupTMap
                            .entrySet()) {
                        if (databaseLocation.distanceTo(entry.getValue()) == 0) {
                            Log.d(this.getClass().getName(),
                                    "Keeping "
                                            + databaseLocation.getLatitude()
                                            + ","
                                            + databaseLocation.getLongitude()
                                            + " currently "
                                            + oldLocation
                                            .distanceTo(databaseLocation)
                                            + "m away in PopupTreeMap.");
                            popupTreeMap.put(
                                    oldLocation.distanceTo(databaseLocation),
                                    entry.getValue());
                            onTree = true;
                            break;
                        }
                    }
                    if (!onTree) {
                        Log.d(this.getClass().getName(),
                                "Loading "
                                        + databaseLocation.getLatitude()
                                        + ","
                                        + databaseLocation.getLongitude()
                                        + " currently "
                                        + oldLocation
                                        .distanceTo(databaseLocation)
                                        + "m away into PopupTreeMap.");
                        popupTreeMap.put(
                                oldLocation.distanceTo(databaseLocation),
                                databaseLocation);
                    }
                } while (placeCursor.moveToNext());
            }
        }
        placeCursor.close();
        placeOpenHelper.close();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initializeLocation_UpdateMap() {
        Location oldLocation;

        if (mLocationClient.isConnected()) {
            oldLocation = mLocationClient.getLastLocation();
            updateMap(oldLocation);
        } else if (!mLocationClient.isConnecting())
            mLocationClient.connect();
    }

    private float updateMap(Location currentLocation) {

        float shortestDistance = 20037580;
        if (currentLocation != null) {
            TreeMap<Float, FireableLocation> iteratePopupTMap = new TreeMap<Float, FireableLocation>();
            iteratePopupTMap.putAll(popupTreeMap);
            popupTreeMap.clear();

            float keyVal;

            Log.i(this.getClass().getName(),
                    "Updating PopupTreeMap, which has "
                            + iteratePopupTMap.size() + " entries.");


            for (Entry<Float, FireableLocation> entry : iteratePopupTMap
                    .entrySet()) {

                keyVal = currentLocation.distanceTo(entry.getValue());
                Log.d(this.getClass().getName(), "Popup Place #" + entry.getValue().getColumnId() + " is " + keyVal +
                        " away.");
                popupTreeMap.put(keyVal, entry.getValue());

                // Un-fire places which the user is now more than 100m from.
                if (entry.getValue().isFired() && keyVal >= 100f) {
                    entry.getValue().setFired(false);
                }
                if (!entry.getValue().isFired() && keyVal < shortestDistance) {
                    shortestDistance = keyVal;
                }


            }

            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            float wifiOffset = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isAvailable() ? 1 : 2;

            SharedPreferences sharedPreferences = this.getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            String mapString = "";

            int entryNumber = 0;
            String[] mapStringArray = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE).getString(PopupTrigger_Geofences.GEOFENCE_COLUMN_NAME_MAP, "").split(" ");
            List<String> mapStringList = new ArrayList<String>();
            for (String string : mapStringArray) {
                String[] splitString = string.split(",");
                mapStringList.add(splitString[0]);
            }
            mLocationClient.removeGeofences(mapStringList, this);
            mIntegerGeofenceHashBiMap.clear();


            for (Entry<Float, FireableLocation> entry : popupTreeMap.entrySet()) {

                if (entryNumber < 5) if (entry.getValue().isFired()) {
                    mapString = mapString.concat(String.valueOf(entryNumber) + "," + entry.getValue().getColumnId() + " ");
                    mIntegerGeofenceHashBiMap.put(entry.getValue().getColumnId(), new Geofence.Builder()
                            .setCircularRegion(entry.getValue().getLatitude(), entry.getValue().getLongitude(), PopupTrigger_Geofences.EXIT_BOUNDARY)
                            .setRequestId(String.valueOf(entryNumber))
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .build());
                } else if (!mIntegerGeofenceHashBiMap.containsKey(entry.getValue().getColumnId())) {
                    mapString = mapString.concat(String.valueOf(entryNumber) + "," + entry.getValue().getColumnId() + " ");
                    mIntegerGeofenceHashBiMap.put(entry.getValue().getColumnId(), new Geofence.Builder()
                            .setCircularRegion(entry.getValue().getLatitude(), entry.getValue().getLongitude(), PopupTrigger_Geofences.ENTRY_BOUNDARY * wifiOffset)
                            .setRequestId(String.valueOf(entryNumber))
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .build());

                }
                if (entryNumber > 5) {
                    if (!entry.getValue().isFired()) {
                        mapString = mapString.concat(PopupTrigger_Geofences.BOUNDARY + "," + entry.getValue().getColumnId());
                        mIntegerGeofenceHashBiMap.put(entry.getValue().getColumnId(), new Geofence.Builder()
                                .setCircularRegion(currentLocation.getLatitude(), currentLocation.getLongitude(), entry.getKey() * 0.9f)
                                .setRequestId(PopupTrigger_Geofences.BOUNDARY)
                                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                                .build());
                        break;
                    }
                }
                entryNumber++;
            }
            editor.putString(PopupTrigger_Geofences.GEOFENCE_COLUMN_NAME_MAP, mapString);
            editor.putBoolean(PopupTrigger_Geofences.POPPED_PLACES, false);
            editor.commit();

            List<Geofence> geofenceList = new ArrayList<Geofence>();
            geofenceList.addAll(mIntegerGeofenceHashBiMap.values());

            if (mLocationClient.isConnected() && geofenceList.size() > 0)
                mLocationClient.addGeofences(geofenceList, mGeofencePendingIntent, this);

            Log.i(this.getClass().getName(), "Nearest unfired location is "
                    + shortestDistance + "m away.");
            iteratePopupTMap.clear();
        }
        return shortestDistance;
    }

    private boolean popPlace(int column_ID) {
        if (popupTreeMap != null && popupTreeMap.size() > 0 && mIntegerGeofenceHashBiMap != null && mIntegerGeofenceHashBiMap.size() > 0) {
            Set<Entry<Float, FireableLocation>> entrySet = new HashSet<Entry<Float, FireableLocation>>();
            entrySet.addAll(popupTreeMap.entrySet());
            for (Entry<Float, FireableLocation> entry : entrySet) {
                if (entry.getValue().getColumnId() == column_ID) {
                    FireableLocation popupPlace = popupTreeMap.get(entry.getKey());
                    if (popupPlace.isFired())
                        return false;
                    else
                        popupTreeMap.get(entry.getKey()).setFired(true);
                }
            }
            popupTreeMap.clear();
        }


        PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
        String popupText = placeOpenHelper.getPopupText(column_ID);
        SharedPreferences settings = getSharedPreferences(
                PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
        if (settings.getBoolean(PopupTrigger_Geofences.POPPED_PLACES, false))
            return false;
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PopupTrigger_Geofences.POPPED_PLACES, true);
        editor.commit();

        if (popupText != null
                && !this.hasRestarted) {
            Intent notificationIntent = new Intent(this,
                    ReminderMap_Activity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            notificationIntent.putExtra(PlaceOpenHelper.COLUMN_ID, column_ID);
            PendingIntent contentIntent = PendingIntent.getActivity(this,
                    0, notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder notificationCompatBuilder = new NotificationCompat.Builder(this)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setContentTitle(this.getString(R.string.app_name))
                    .setContentText(popupText)
                    .setContentIntent(contentIntent)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_notification))
                    .setTicker(popupText)
                    .setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
            if (textToSpeech_Initialized) {


                if (settings.getBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED,
                        true)) {
                    mTextToSpeech.speak(popupText,
                            TextToSpeech.QUEUE_ADD, null);
                } else
                    notificationCompatBuilder.setDefaults(Notification.DEFAULT_SOUND);
            } else
                notificationCompatBuilder.setDefaults(Notification.DEFAULT_SOUND);

            Notification notification = notificationCompatBuilder.build();
            mNotificationManager = (NotificationManager) this
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(POPUP_PLACE_REACHED, notification);
        }
        return true;
    }

    private boolean unPopPlace(int column_ID) {
        if (popupTreeMap != null && popupTreeMap.size() > 0) {
            Set<Entry<Float, FireableLocation>> entrySet = new HashSet<Entry<Float, FireableLocation>>();
            entrySet.addAll(popupTreeMap.entrySet());
            for (Entry<Float, FireableLocation> entry : entrySet) {
                if (entry.getValue().getColumnId() == column_ID) {
                    popupTreeMap.get(entry.getKey()).setFired(false);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result;
            if (mTextToSpeech.isLanguageAvailable(Locale.getDefault()) >= 0) {
                result = mTextToSpeech.setLanguage(Locale.getDefault());
            } else {
                Log.w(this.getClass().getName(),
                        "Default Language not available, falling back to US English.");
                result = mTextToSpeech.setLanguage(Locale.US);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(this.getClass().getName(),
                        "TextToSpeech: This Language is not supported");
            } else textToSpeech_Initialized = true;
        } else {
            Log.e(this.getClass().getName(),
                    "TextToSpeech: Initilization Failed!");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(this.getClass().getName(), "PopupTrigger_Geofences Service OnDestroy()'d.");

        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            mTextToSpeech.shutdown();
        }
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
            mLocationClient.disconnect();
        }


    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(this.getClass().getName(),
                "Connected to Google Maps successfully, requesting Location.");
        //initializeLocation_UpdateFromDatabase();
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onDisconnected() {
        Log.d(this.getClass().getName(), "Disconnected from Google Maps.");
        mLocationClient.removeLocationUpdates(this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(this.getClass().getName(), "Location received, Accuracy: " + location.getAccuracy());
        lastLocation = location;
        updateFromDatabase(location);
        updateMap(location);
    }

    @Override
    public void onAddGeofencesResult(int statusCode, String[] geofenceRequestIds) {

        if (statusCode == LocationStatusCodes.SUCCESS)
            Log.d(this.getClass().getName(), geofenceRequestIds.length + " Geofences added.");

        else
            Log.e(this.getClass().getName(), geofenceRequestIds.length + " Geofences failed to add.");

    }

    @Override
    public void onRemoveGeofencesByRequestIdsResult(int i, String[] strings) {

    }

    @Override
    public void onRemoveGeofencesByPendingIntentResult(int i, PendingIntent pendingIntent) {

    }
}
