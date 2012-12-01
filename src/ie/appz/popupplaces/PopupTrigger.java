package ie.appz.popupplaces;

import java.util.Locale;
import java.util.Map.Entry;
import java.util.TreeMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;

public class PopupTrigger extends Service implements TextToSpeech.OnInitListener {
	private static final int POPUP_PLACE_REACHED = 1;
	private static NotificationManager mNotificationManager;
	public static String NotificationLongitude = "notification_longitude";
	public static String NotificationLatitude = "notification_latitude";

	Location lastLocation;

	public float oldShortestDistance = 10;
	LocationManager mLocationManager;
	LocationListener networkListener;
	LocationListener gpsListener;
	// popupTreeMap stores locations keyed to how far the user is from those
	// locations
	private TreeMap<Float, FireableLocation> popupTreeMap;

	TextToSpeech mTextToSpeech;
	boolean textToSpeech_Initialized = false;
	boolean hasRestarted = false;

	@Override
	public void onCreate() {
		super.onCreate();
		lastLocation = new Location("Engage");

		popupTreeMap = new TreeMap<Float, FireableLocation>();

		mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		mTextToSpeech = new TextToSpeech(this, this);

		networkListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				Log.i(this.getClass().getName(),
						"New Network Location: " + location.getLatitude() + "," + location.getLongitude() + " - " + location.getAccuracy());
				if (location.getAccuracy() < 40) {
					if (lastLocation == null || (location.getTime() - lastLocation.getTime()) > 4000)
						processLocation(location);

				}
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
				Log.w(this.getClass().getName(), provider + " enabled.");
			}

			public void onProviderDisabled(String provider) {
				Log.w(this.getClass().getName(), provider + " disabled.");
			}
		};
		gpsListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				Log.i(this.getClass().getName(), "New GPS Location: " + location.getLatitude() + "," + location.getLongitude() + " - " + location.getAccuracy());
				if (location.getAccuracy() < 35) {
					if (lastLocation == null || (location.getTime() - lastLocation.getTime()) > 4000)
						processLocation(location);
				}
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
				Log.w(this.getClass().getName(), provider + " enabled.");
			}

			public void onProviderDisabled(String provider) {
				Log.w(this.getClass().getName(), provider + " disabled.");
			}
		};

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
		float shortestDiff = oldShortestDistance - shortestDistance;
		Log.w(this.getClass().getName(), "ShotestDiff= " + shortestDiff + "m");
		shortestDiff = (shortestDiff < 0 ? -shortestDiff : shortestDiff);

		if (shortestDistance < 100) {
			if (placeReached(location.getAccuracy())) {
				popupNearest();
			} else if (shortestDiff > oldShortestDistance / 4) {
				this.hasRestarted = false;
				Log.i(this.getClass().getName(), "Adjusting Network Provider to notify at " + shortestDistance / 2 + "m.");
				mLocationManager.removeUpdates(networkListener);
				mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
				Log.i(this.getClass().getName(), "Adjusting GPS Provider to notify at " + shortestDistance / 2 + "m.");
				mLocationManager.removeUpdates(gpsListener);
				mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 45000, shortestDistance / 2, gpsListener);
			}
		} else if (shortestDistance < 150 && shortestDiff > oldShortestDistance / 4) {
			this.hasRestarted = false;
			Log.i(this.getClass().getName(), "Adjusting Network Provider to notify at " + shortestDistance / 2 + "m.");
			mLocationManager.removeUpdates(networkListener);
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
			Log.i(this.getClass().getName(), "Adjusting GPS Provider to notify at " + shortestDistance / 2 + "m.");
			mLocationManager.removeUpdates(gpsListener);
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 45000, shortestDistance / 2, gpsListener);
		} else if (shortestDiff > oldShortestDistance / 4) {
			this.hasRestarted = false;
			Log.i(this.getClass().getName(), "Adjusting Network Provider to notify at " + shortestDistance / 2 + "m");
			mLocationManager.removeUpdates(networkListener);
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
			mLocationManager.removeUpdates(gpsListener);
		}

		lastLocation.set(location);
		oldShortestDistance = shortestDistance;

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
		if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
			Location oldLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			Log.w(this.getClass().getName(), "PopupTrigger Service has recieved onStartCommand");
			if (intent == null) {
				hasRestarted = true;
				initializeLocation_UpdateFromDatabase();

			} else {

				Bundle extras = intent.getExtras();
				if (extras != null && extras.containsKey(ReminderMap_Activity.ItemChanged) && extras.containsKey(ReminderMap_Activity.NewLongitude)
						&& extras.containsKey(ReminderMap_Activity.NewLatitude)) {
					{
						boolean change = extras.getBoolean(ReminderMap_Activity.ItemChanged);

						if (change) {
							FireableLocation newLocation = new FireableLocation("FromUser");
							newLocation.setLatitude(extras.getInt(ReminderMap_Activity.NewLatitude) / 1E6);
							newLocation.setLongitude(extras.getInt(ReminderMap_Activity.NewLongitude) / 1E6);
							Log.d(this.getClass().getName(), "Adding Key: " + oldLocation.distanceTo(newLocation) + " Location: " + newLocation.getLatitude()
									+ "," + newLocation.getLongitude());
							popupTreeMap.put(oldLocation.distanceTo(newLocation), newLocation);
						} else {
							Location newLocation = new Location("FromUser");
							newLocation.setLatitude(extras.getInt(ReminderMap_Activity.NewLatitude) / 1E6);
							newLocation.setLongitude(extras.getInt(ReminderMap_Activity.NewLongitude) / 1E6);
							Log.d(this.getClass().getName(), "Removing Key: " + oldLocation.distanceTo(newLocation) + " Location: " + newLocation.getLatitude()
									+ "," + newLocation.getLongitude());
							initializeLocation_UpdateFromDatabase();
						}
					}
				} else {
					initializeLocation_UpdateFromDatabase();
				}
			}
			/*
			 * Register the listener with the FireableLocation Manager to
			 * receive location updates
			 */

			mLocationManager.removeUpdates(networkListener);
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 10, networkListener);
			mLocationManager.removeUpdates(gpsListener);
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 10, gpsListener);
			/*
			 * The two integers in this request are the time (ms) and distance
			 * (m) intervals of notifications respectively.
			 */

			/*
			 * This is just me testing the system where location Criteria are
			 * used to determine the best FireableLocation Provider. Criteria
			 * farCriteria = newCriteria();
			 * farCriteria.setPowerRequirement(Criteria.POWER_LOW); String
			 * bestProvider = mLocationManager.getBestProvider(farCriteria,
			 * true); Log.i(this.getClass().getName(), "The Best Provider is " +
			 * bestProvider);
			 */
			if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				updateMap(mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
			} else if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				updateMap(mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
			}
			return START_STICKY;
		} else {
			Log.e(this.getClass().getName(), "onStartCommand called when PopupTrigger is disabled.");

			return START_STICKY;
		}
	}

	private void initializeLocation_UpdateFromDatabase() {
		Location oldLocation = null;
		if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			oldLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		} else if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			oldLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		}
		if (lastLocation != null) {
			if (oldLocation != null) {
				if ((oldLocation.getTime() - lastLocation.getTime()) < 4000) {
					oldLocation = lastLocation;
				}
			} else
				oldLocation = lastLocation;
		} else if (oldLocation == null) {
			Criteria mCriteria = new Criteria();
			if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				mCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
			} else if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				mCriteria.setAccuracy(Criteria.ACCURACY_FINE);
			}
			mCriteria.setCostAllowed(false);
			Toast.makeText(this, "Centering map on your location.", Toast.LENGTH_SHORT).show();

			LocationListener mListener = new LocationListener() {
				public void onLocationChanged(Location location) {
					updateFromDatabase(location);
				}

				public void onStatusChanged(String provider, int status, Bundle extras) {
				}

				public void onProviderEnabled(String provider) {
					Log.w(this.getClass().getName(), provider + " enabled.");
				}

				public void onProviderDisabled(String provider) {
					Log.w(this.getClass().getName(), provider + " disabled.");
				}
			};
			mLocationManager.requestSingleUpdate(mCriteria, mListener, null);
		}
		updateFromDatabase(oldLocation);
	}

	private void updateFromDatabase(Location oldLocation) {

		TreeMap<Float, FireableLocation> iteratePopupTMap = new TreeMap<Float, FireableLocation>();
		iteratePopupTMap.putAll(popupTreeMap);
		popupTreeMap.clear();
		PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
		Cursor placeCursor = placeOpenHelper.getPlaces();

		if (placeCursor.getCount() >= 1) {
			if (placeCursor.moveToFirst()) {
				do {
					boolean onTree = false;
					FireableLocation databaseLocation = new FireableLocation("FromDatabase");
					databaseLocation.setLatitude((Double) (placeCursor.getInt(0) / 1E6));
					databaseLocation.setLongitude((Double) (placeCursor.getInt(1) / 1E6));

					for (Entry<Float, FireableLocation> entry : iteratePopupTMap.entrySet()) {
						if (databaseLocation.distanceTo(entry.getValue()) == 0) {
							Log.d(this.getClass().getName(), "Keeping " + databaseLocation.getLatitude() + "," + databaseLocation.getLongitude()
									+ " currently " + oldLocation.distanceTo(databaseLocation) + "m away in PopupTreeMap.");
							popupTreeMap.put(oldLocation.distanceTo(databaseLocation), entry.getValue());
							onTree = true;
						}
					}
					if (!onTree) {
						Log.d(this.getClass().getName(), "Loading " + databaseLocation.getLatitude() + "," + databaseLocation.getLongitude() + " currently "
								+ oldLocation.distanceTo(databaseLocation) + "m away into PopupTreeMap.");
						popupTreeMap.put(oldLocation.distanceTo(databaseLocation), databaseLocation);
					}
				} while (placeCursor.moveToNext());
			}
		}
		placeCursor.close();
		placeOpenHelper.close();
	}

	@Override
	public IBinder onBind(Intent intent) {

		// TODO Auto-generated method stub
		return null;
	}

	private float updateMap(Location currentLocation) {

		float shortestDistance = 20037580;
		if (currentLocation != null) {
			TreeMap<Float, FireableLocation> iteratePopupTMap = new TreeMap<Float, FireableLocation>();
			iteratePopupTMap.putAll(popupTreeMap);
			popupTreeMap.clear();

			float keyVal;

			Log.i(this.getClass().getName(), "Updating PopupTreeMap, which has " + iteratePopupTMap.size() + " entries.");
			for (Entry<Float, FireableLocation> entry : iteratePopupTMap.entrySet()) {
				keyVal = currentLocation.distanceTo(entry.getValue());
				// Log.d(this.getClass().getName(), "A place is " + keyVal +
				// " away.");
				popupTreeMap.put(keyVal, entry.getValue());
				if (entry.getValue().isFired() && keyVal >= 100f) {
					entry.getValue().setFired(false);
				}
				if (keyVal < shortestDistance) {
					shortestDistance = keyVal;
				}
			}

			Log.d(this.getClass().getName(), "Nearest location is " + shortestDistance + "m away.");
			iteratePopupTMap.clear();
		}
		return shortestDistance;
	}

	private boolean placeReached(float accuracy) {
		if (!popupTreeMap.isEmpty()) {
			Float distanceTo = popupTreeMap.firstKey();

			if (distanceTo <= (accuracy * 2) || distanceTo <= 25f) {
				return true;
			} else
				return false;
		} else
			return false;
	}

	private boolean popupNearest() {
		if (!popupTreeMap.isEmpty()) {
			FireableLocation popupPlace = popupTreeMap.get(popupTreeMap.firstKey());

			GeoPoint popupPlaceGeoPoint = new GeoPoint((int) (popupPlace.getLatitude() * 1E6), (int) (popupPlace.getLongitude() * 1E6));
			PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
			String popupText = placeOpenHelper.getPopupText(popupPlaceGeoPoint);

			if (popupText != null && !popupPlace.isFired() && !this.hasRestarted) {
				Log.i(this.getClass().getName(), "Popping Location " + popupPlace.getLatitude() + "," + popupPlace.getLongitude());
				Intent notificationIntent = new Intent(this, ReminderMap_Activity.class);
				notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

				notificationIntent.putExtra(NotificationLatitude, popupPlaceGeoPoint.getLatitudeE6());
				notificationIntent.putExtra(NotificationLongitude, popupPlaceGeoPoint.getLongitudeE6());
				PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				NotificationCompat.Builder nCompatBuilder = new NotificationCompat.Builder(this);
				nCompatBuilder.setAutoCancel(true);
				nCompatBuilder.setOngoing(false);
				nCompatBuilder.setContentTitle(this.getString(R.string.app_name));
				nCompatBuilder.setContentText(popupText);
				nCompatBuilder.setContentIntent(contentIntent);

				nCompatBuilder.setSmallIcon(R.drawable.ic_launcher);

				Notification notification = nCompatBuilder.build();

				mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationManager.notify(POPUP_PLACE_REACHED, notification);
				if (textToSpeech_Initialized) {
					SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
					if (settings.getBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED, false)) {
						mTextToSpeech.speak(popupText, TextToSpeech.QUEUE_FLUSH, null);
					}
				}
				popupPlace.setFired(true);

			} else {
				popupPlace.setFired(true);
				this.hasRestarted = false;
				Log.i(this.getClass().getName(), "Not Popping Location " + popupPlace.getLatitude() + "," + popupPlace.getLongitude()
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
				Log.w(this.getClass().getName(), "Default Language not available, falling back to US English.");
				result = mTextToSpeech.setLanguage(Locale.US);
			}
			if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
				Log.e(this.getClass().getName(), "TextToSpeech: This Language is not supported");
			} else {
				textToSpeech_Initialized = true;
			}
		} else {
			Log.e(this.getClass().getName(), "TextToSpeech: Initilization Failed!");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(this.getClass().getName(), "PopupTrigger Service OnDestroy()'d.");
		mLocationManager.removeUpdates(networkListener);
		mLocationManager.removeUpdates(gpsListener);
		if (mNotificationManager != null) {
			mNotificationManager.cancelAll();
		}

		if (mTextToSpeech != null) {
			mTextToSpeech.stop();
			mTextToSpeech.shutdown();
		}

	}
}
