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
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.Locale;
import java.util.Map.Entry;
import java.util.TreeMap;

import ie.appz.popupplaces.FireableLocation;
import ie.appz.popupplaces.PlaceOpenHelper;
import ie.appz.popupplaces.R;
import ie.appz.popupplaces.ReminderMap_Activity;

public class PopupTrigger extends Service implements
        TextToSpeech.OnInitListener {
    private static final int POPUP_PLACE_REACHED = 1;
    public static String NotificationLongitude = "notification_longitude";
    public static String NotificationLatitude = "notification_latitude";
    private static NotificationManager mNotificationManager;
    boolean textToSpeech_Initialized = false;
    boolean hasRestarted = false;
    private float oldShortestDistance = 1000;
    private Location lastLocation;
    private LocationManager mLocationManager;
    private TextToSpeech mTextToSpeech;
    //boolean usingGPSAndNetwork = false;
    private float trendingNetworkAccuracy = 20;
    // popupTreeMap stores locations keyed to how far the user is from those
    // locations
    private TreeMap<Float, FireableLocation> popupTreeMap;
    private LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            Log.d(this.getClass().getName(),
                    "New " + location.getProvider() + " location: " + location.getLatitude() + ","
                            + location.getLongitude() + " - "
                            + location.getAccuracy());
            SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
            if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER))
                trendingNetworkAccuracy = (trendingNetworkAccuracy + location.getAccuracy()) / 2;

            if (location.getAccuracy() < 40 || location.getAccuracy() < sharedPreferences.getInt(PlaceOpenHelper.POP_RADIUS, 25)) {
                //if (lastLocation == null || location.getTime() - lastLocation.getTime() > 4000)
                processLocation(location);

            }
        }

        public void onStatusChanged(String provider, int status,
                                    Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
            Log.w(this.getClass().getName(), provider + " enabled.");
        }

        public void onProviderDisabled(String provider) {
            Log.w(this.getClass().getName(), provider + " disabled.");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        lastLocation = new Location("Engage");
        popupTreeMap = new TreeMap<Float, FireableLocation>();
        mLocationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);
        mTextToSpeech = new TextToSpeech(this, this);
    }

	/*
     * The proccessLocation() function is where all of the battery usage
	 * optimization is. Decisions are based on the distance to the closest Popup
	 * Place, this value is called shortestDistance. shortestDistance falls into
	 * one of three categories: less than 100 meters, less than 200 meters and
	 * more than 200 meters. If the distance is less than 100m, then a check is
	 * performed to see if the place has been reached, if not then location
	 * updates are requested from the GPS And Network Providers. If the distance
	 * is less than 200m, then location updates are requested from the GPS and
	 * Network providers. If the distance is greater than 200m, then the
	 * fidelity of the GPS provider is not required and only the Network
	 * provider is utilized. shortestDiff is the difference between the current
	 * shortestDistance and the previous shortestDistance (oldShortestDistance)
	 * it is used to make sure that multiple update requests are not being made
	 * while the user is stationary.
	 */

    public void processLocation(Location location) {

        float shortestDistance = updateMap(location);
        chooseLocationSource(shortestDistance);

        lastLocation.set(location);
        oldShortestDistance = shortestDistance;

    }

    private void chooseLocationSource(float shortestDistance) {

        SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
        int popRadius = sharedPreferences.getInt(PlaceOpenHelper.POP_RADIUS, 25);
        if (Math.abs(shortestDistance - oldShortestDistance) > 10f) {
            mLocationManager.removeUpdates(mLocationListener);
            if (shortestDistance > (popRadius * 2)) {
                if (shortestDistance > trendingNetworkAccuracy && trendingNetworkAccuracy <= popRadius)
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 0, mLocationListener);
                else {
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 0, mLocationListener);
                    if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 45000, 0, mLocationListener);
                }
            } else {
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 0, mLocationListener);
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 45000, 0, mLocationListener);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences settings = getSharedPreferences(
                PlaceOpenHelper.PREFS_NAME, 0);
        if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
            Log.i(this.getClass().getName(),
                    "PopupTrigger Service has recieved onStartCommand");
            if (intent == null)
                hasRestarted = true;
            initializeLocation_UpdateFromDatabase();

            /*
             * Register the listener with the LocationManager to
			 * receive location updates
			 */
            mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, mLocationListener);
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 10, mLocationListener);


            return START_STICKY;
        } else {
            Log.e(this.getClass().getName(),
                    "onStartCommand called when PopupTrigger is disabled.");

            return START_STICKY;
        }
    }

    private void initializeLocation_UpdateFromDatabase() {
        Location oldLocation = null;
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            oldLocation = mLocationManager
                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } else if (mLocationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            oldLocation = mLocationManager
                    .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (lastLocation != null) {
            if (oldLocation != null) {
                if ((oldLocation.getTime() - lastLocation.getTime()) < 4000) {
                    oldLocation = lastLocation;
                }
            } else
                oldLocation = lastLocation;

        } else if (oldLocation == null) {


            LocationListener mListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    updateFromDatabase(location);
                    mLocationManager.removeUpdates(this);
                }

                public void onStatusChanged(String provider, int status,
                                            Bundle extras) {
                }

                public void onProviderEnabled(String provider) {
                    Log.w(this.getClass().getName(), provider + " enabled.");
                }

                public void onProviderDisabled(String provider) {
                    Log.w(this.getClass().getName(), provider + " disabled.");
                }
            };
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0, mListener);
            return;
        }
        updateFromDatabase(oldLocation);
    }

    /*private void updateFromDatabase(Location oldLocation) {

        TreeMap<Float, FireableLocation> iteratePopupTMap = new TreeMap<Float, FireableLocation>();
        iteratePopupTMap.putAll(popupTreeMap);
        popupTreeMap.clear();
        PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
        Cursor placeCursor = placeOpenHelper.getPlaces();

        if (placeCursor.getCount() >= 1) {
            if (placeCursor.moveToFirst()) {
                do {
                    boolean onTree = false;
                    FireableLocation databaseLocation = new FireableLocation(
                            "FromDatabase");
                    databaseLocation.setLatitude(placeCursor.getDouble(0));
                    databaseLocation.setLongitude(placeCursor.getDouble(1));
                    databaseLocation.setColumnId(placeCursor.getInt(3));

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
    }*/
    private void updateFromDatabase(Location oldLocation) {

        TreeMap<Float, FireableLocation> iteratePopupTMap = new TreeMap<Float, FireableLocation>();
        iteratePopupTMap.putAll(popupTreeMap);
        popupTreeMap.clear();
        PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
        Cursor placeCursor = placeOpenHelper.getPlaces();
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
        int popRadius = sharedPreferences.getInt(PlaceOpenHelper.POP_RADIUS, 25);

        if (placeCursor.getCount() >= 1) {
            if (placeCursor.moveToFirst()) {
                do {
                    boolean onTree = false;
                    FireableLocation databaseLocation = new FireableLocation(
                            "FromDatabase");
                    databaseLocation.setLatitude(placeCursor.getDouble(0));
                    databaseLocation.setLongitude(placeCursor.getDouble(1));
                    databaseLocation.setColumnId(placeCursor.getInt(3));
                    if (databaseLocation.distanceTo(oldLocation) < popRadius) {
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
        processLocation(oldLocation);
    }

    @Override
    public IBinder onBind(Intent intent) {
        /*This component is not needed*/
        return null;
    }

    private float updateMap(Location currentLocation) {
        SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);

        float unPopRadius = sharedPreferences.getInt(PlaceOpenHelper.POP_RADIUS, 25) + 75f;
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
                // Log.d(this.getClass().getName(), "A place is " + keyVal +
                // " away.");
                popupTreeMap.put(keyVal, entry.getValue());
                if (entry.getValue().isFired()) {
                    if (keyVal >= unPopRadius)
                        entry.getValue().setFired(false);
                } else if (keyVal < shortestDistance) {
                    shortestDistance = keyVal;
                }
            }

            Log.d(this.getClass().getName(), "Nearest location is "
                    + shortestDistance + "m away.");
            iteratePopupTMap.clear();
            if (placeReached(currentLocation.getAccuracy()))
                popupNearest();
        }
        return shortestDistance;
    }

    private boolean placeReached(float accuracy) {
        if (!popupTreeMap.isEmpty()) {
            Float distanceTo = popupTreeMap.firstKey();

            SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
            int popRadius = sharedPreferences.getInt(PlaceOpenHelper.POP_RADIUS, 25);

            return distanceTo <= (accuracy * 1.5) || distanceTo <= popRadius;
        } else
            return false;
    }

    private boolean popupNearest() {
        if (!popupTreeMap.isEmpty()) {
            FireableLocation popupPlace = popupTreeMap.get(popupTreeMap
                    .firstKey());
            LatLng popupPlaceLatLng = new LatLng(popupPlace.getLatitude(), popupPlace.getLongitude());

            PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
            String popupText = placeOpenHelper.getPopupText(popupPlaceLatLng);

            SharedPreferences settings = getSharedPreferences(
                    PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);

            if (popupText != null && !popupPlace.isFired()
                    && !this.hasRestarted) {
                Log.i(this.getClass().getName(),
                        "Popping Location " + popupPlace.getLatitude() + ","
                                + popupPlace.getLongitude());
                Intent notificationIntent = new Intent(this,
                        ReminderMap_Activity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                notificationIntent.putExtra(PlaceOpenHelper.COLUMN_ID, popupPlace.getColumnId());
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
                popupPlace.setFired(true);

            } else {
                popupPlace.setFired(true);
                this.hasRestarted = false;
                Log.i(this.getClass().getName(),
                        "Not Popping Location " + popupPlace.getLatitude()
                                + "," + popupPlace.getLongitude()
                                + " it has already fired.");
            }
            return !popupPlace.isFired();
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
            } else {
                textToSpeech_Initialized = true;
            }
        } else {
            Log.e(this.getClass().getName(),
                    "TextToSpeech: Initilization Failed!");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(this.getClass().getName(), "PopupTrigger Service OnDestroy()'d.");
        mLocationManager.removeUpdates(mLocationListener);
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            mTextToSpeech.shutdown();
        }

    }
}