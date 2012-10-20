package ie.appz.popupplaces;

import java.util.Map.Entry;
import java.util.TreeMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.maps.GeoPoint;

public class PopupTrigger extends Service {
	private static final int POPUP_PLACE_REACHED = 1;
	private static NotificationManager notificationManager;
	public static String NotificationLongitude = "notification_longitude";
	public static String NotificationLatitude = "notification_latitude";
	/**
		 */
	PlaceOpenHelper placeOpenHelper;
	Location lastLocation;
	public static float oldAccuracy = 12;
	LocationManager mLocationManager;
	LocationListener networkListener;
	LocationListener gpsListener;
	private TreeMap<Float, Location> popupTreeMap;

	@Override
	public void onCreate() {
		super.onCreate();
		lastLocation = new Location("Engage");
		placeOpenHelper = new PlaceOpenHelper(this);
		popupTreeMap = new TreeMap<Float, Location>();
		mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		networkListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				Log.i(this.getClass().toString(),
						"New Network Location: " + location.getLatitude() + "," + location.getLongitude() + " - " + location.getAccuracy());
				if (location.getAccuracy() < 40) {
					if (lastLocation == null || (location.getTime() - lastLocation.getTime()) > 40000) {

						float shortestDistance = updateMap(location);
						if (shortestDistance < 100) {
							if (placeReached(location.getAccuracy())) {
								popupNearest();
							}
							mLocationManager.removeUpdates(gpsListener);
							mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, shortestDistance / 2, gpsListener);
						} else if (shortestDistance < 200) {
							mLocationManager.removeUpdates(networkListener);
							mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
							mLocationManager.removeUpdates(gpsListener);
							mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, shortestDistance / 2, gpsListener);
						} else {
							mLocationManager.removeUpdates(networkListener);
							mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
							mLocationManager.removeUpdates(gpsListener);
						}
						Log.d(this.getClass().toString(), "Changing Minimum Distance on Location Updates to " + (shortestDistance / 2) + "m");
						lastLocation.set(location);
					}

				}
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
				Log.w(this.getClass().toString(), provider + " enabled.");
			}

			public void onProviderDisabled(String provider) {
				Log.w(this.getClass().toString(), provider + " disabled.");
			}
		};
		gpsListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				Log.i(this.getClass().toString(),
						"New GPS Location: " + location.getLatitude() + "," + location.getLongitude() + " - " + location.getAccuracy());
				if (location.getAccuracy() < 40) {
					float shortestDistance = updateMap(location);
					if (shortestDistance < 100) {
						if (placeReached(location.getAccuracy())) {
							popupNearest();
						}
						mLocationManager.removeUpdates(gpsListener);
						mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, shortestDistance / 2, gpsListener);
					} else if (shortestDistance < 200) {
						mLocationManager.removeUpdates(networkListener);
						mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
						mLocationManager.removeUpdates(gpsListener);
						mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, shortestDistance / 2, gpsListener);

					} else {
						mLocationManager.removeUpdates(networkListener);
						mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, shortestDistance / 2, networkListener);
						mLocationManager.removeUpdates(gpsListener);
					}
					Log.d(this.getClass().toString(), "Changing Minimum Distance on Location Updates to " + (shortestDistance / 2) + "m");

					lastLocation.set(location);
				}
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
				Log.w(this.getClass().toString(), provider + " enabled.");
			}

			public void onProviderDisabled(String provider) {
				Log.w(this.getClass().toString(), provider + " disabled.");
			}
		};

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Location oldLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Log.i(this.getClass().toString(), "PopupTrigger Service has recieved onStartCommand");
		if (intent == null) {

			updateFromDatabase();

		} else {

			Bundle extras = intent.getExtras();
			if (extras != null && extras.containsKey(ReminderMap_Activity.ItemChanged) && extras.containsKey(ReminderMap_Activity.NewLongitude)
					&& extras.containsKey(ReminderMap_Activity.NewLatitude)) {
				{
					boolean change = extras.getBoolean(ReminderMap_Activity.ItemChanged);

					if (change) {
						Location newLocation = new Location("FromUser");
						newLocation.setLatitude(extras.getInt(ReminderMap_Activity.NewLatitude) / 1E6);
						newLocation.setLongitude(extras.getInt(ReminderMap_Activity.NewLongitude) / 1E6);
						Log.d(this.getClass().toString(), "Adding Key: " + oldLocation.distanceTo(newLocation) + " Location: " + newLocation.getLatitude()
								+ "," + newLocation.getLongitude());
						popupTreeMap.put(oldLocation.distanceTo(newLocation), newLocation);
					} else {
						Location newLocation = new Location("FromUser");
						newLocation.setLatitude(extras.getInt(ReminderMap_Activity.NewLatitude) / 1E6);
						newLocation.setLongitude(extras.getInt(ReminderMap_Activity.NewLongitude) / 1E6);
						float keyDist = oldLocation.distanceTo(newLocation);
						Log.d(this.getClass().toString(), "Removing Key: " + oldLocation.distanceTo(newLocation) + " Location: " + newLocation.getLatitude()
								+ "," + newLocation.getLongitude());
						if (newLocation.distanceTo(popupTreeMap.get(keyDist)) == 0) {
							popupTreeMap.remove(keyDist);
						} else {
							updateFromDatabase();
						}

					}
				}
			} else {
				updateFromDatabase();
			}
		}
		/*
		 * Register the listener with the Location Manager to receive location
		 * updates
		 */

		mLocationManager.removeUpdates(networkListener);
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 10, networkListener);
		mLocationManager.removeUpdates(gpsListener);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 10, gpsListener);
		Criteria farCriteria = new Criteria();
		farCriteria.setPowerRequirement(Criteria.POWER_LOW);
		String bestProvider = mLocationManager.getBestProvider(farCriteria, true);
		Log.i(this.getClass().toString(), "The Best Provider is " + bestProvider);
		/*
		 * The two integers in this request are the time (ms) and distance (m)
		 * intervals of notifications respectively.
		 */
		updateMap(mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
		return START_STICKY;
	}

	private void updateFromDatabase() {
		Location oldLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		popupTreeMap.clear();

		PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
		Cursor placeCursor = placeOpenHelper.getPlaces();

		if (placeCursor.getCount() >= 1) {
			if (placeCursor.moveToFirst()) {
				do {
					Location databaseLocation = new Location("FromDatabase");
					databaseLocation.setLatitude((Double) (placeCursor.getInt(0) / 1E6));
					databaseLocation.setLongitude((Double) (placeCursor.getInt(1) / 1E6));
					popupTreeMap.put(oldLocation.distanceTo(databaseLocation), databaseLocation);
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
		TreeMap<Float, Location> iterateTreeMap = new TreeMap<Float, Location>();
		iterateTreeMap.putAll(popupTreeMap);
		float shortestDistance = 20037580;
		popupTreeMap.clear();
		Log.d(this.getClass().toString(), "Updating Map");
		for (Entry<Float, Location> entry : iterateTreeMap.entrySet()) {
			float keyVal = currentLocation.distanceTo(entry.getValue());
			Log.d(this.getClass().toString(), "A place is " + keyVal + " away.");
			popupTreeMap.put(keyVal, entry.getValue());
			if (keyVal < shortestDistance) {
				shortestDistance = keyVal;
			}
		}

		Log.d(this.getClass().toString(), "Nearest location is " + shortestDistance + "m away.");

		iterateTreeMap.clear();
		return shortestDistance;
	}

	private boolean placeReached(float accuracy) {
		if (!popupTreeMap.isEmpty()) {
			Float distanceTo = popupTreeMap.firstKey();

			if (distanceTo <= (accuracy * 2)) {
				return true;
			} else
				return false;
		} else
			return false;
	}

	private boolean popupNearest() {
		if (!popupTreeMap.isEmpty()) {
			Log.d(this.getClass().toString(), "Popup Place Reached.");
			Location popupPlace = popupTreeMap.get(popupTreeMap.firstKey());
			GeoPoint popupPlaceGeoPoint = new GeoPoint((int) (popupPlace.getLatitude() * 1E6), (int) (popupPlace.getLongitude() * 1E6));
			PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
			String popupText = placeOpenHelper.getPopupText(popupPlaceGeoPoint);
			if (popupText != null) {
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

				nCompatBuilder.setSmallIcon(R.drawable.ic_notification);

				Notification notification = nCompatBuilder.build();

				notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.notify(POPUP_PLACE_REACHED, notification);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mLocationManager.removeUpdates(networkListener);
		mLocationManager.removeUpdates(gpsListener);

		if (placeOpenHelper != null) {
			placeOpenHelper.close();
		}

	}

}
