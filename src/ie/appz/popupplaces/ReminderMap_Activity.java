package ie.appz.popupplaces;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class ReminderMap_Activity extends MapActivity implements OnItemClickListener {

	public static final String NewLongitude = "new_longitude";
	public static final String NewLatitude = "new_latitude";

	private MapView mapView;
	private MapOverlay mapOverlay;
	private ListView searchResultsListView;
	private List<Address> foundAddresses = null;
	private Geocoder mGeoCoder = null;

	private boolean nowRunning = false;
	public static String ItemChanged = "item_changed";
	private Context parentContext = this;

	public class MapOverlay extends com.google.android.maps.Overlay {
		@Override
		public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
			super.draw(canvas, mapView, shadow);
			return true;
		}

		boolean tap = false;

		/*
		 * This code filters out pinch-zoom motions from being mistaken for taps
		 * read more here: http://j.mp/TrNFBo
		 */
		@Override
		public boolean onTouchEvent(MotionEvent motionEvent, MapView mv) {
			if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
				tap = true;
			}
			if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && motionEvent.getPointerCount() > 1) {
				tap = false;
			}
			return super.onTouchEvent(motionEvent, mv);

		}

		@Override
		public boolean onTap(final GeoPoint tappedPoint, MapView mapView) {

			if (tap == true) {
				final Dialog dialog = new Dialog(ReminderMap_Activity.this);
				dialog.setContentView(R.layout.newplacedialog_layout);
				dialog.setTitle(R.string.newplacedialog_text);
				dialog.setCancelable(true);
				final PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(ReminderMap_Activity.this);
				final EditText inputBox = (EditText) dialog.findViewById(R.id.editText1);

				/* Click on the Save Button */
				Button saveButton = (Button) dialog.findViewById(R.id.saveButton);
				saveButton.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View arg0) {

						String popupNote = inputBox.getText().toString();
						if (popupNote.length() > 0) {

							placeOpenHelper.addPlace(tappedPoint, popupNote);

							Toast toast = Toast.makeText(getApplicationContext(), tappedPoint.toString() + ", \"" + popupNote + "\" added.", Toast.LENGTH_LONG);
							toast.show();
							Intent intent = new Intent(ReminderMap_Activity.this, PopupTrigger.class);
							Bundle extrasBundle = new Bundle();
							extrasBundle.putBoolean(ReminderMap_Activity.ItemChanged, true);
							extrasBundle.putInt(PopupTrigger.NotificationLatitude, tappedPoint.getLatitudeE6());
							extrasBundle.putInt(PopupTrigger.NotificationLongitude, tappedPoint.getLongitudeE6());
							intent.putExtras(extrasBundle);
							SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
							if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
								startService(intent);
							}
							drawPlaces();
						}
						dialog.dismiss();

					}
				});

				/* Click on the Cancel Button */
				Button cancelButton = (Button) dialog.findViewById(R.id.cancelButton);
				cancelButton.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View arg0) {
						dialog.cancel();
					}
				});
				dialog.show();
				placeOpenHelper.close();
				return true;
			} else {
				return false;
			}

		}

	}

	LocationListener mListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			GeoPoint mGeoPoint = new GeoPoint((int) (location.getLatitude() * 1E6), (int) (location.getLongitude() * 1E6));
			if (mGeoPoint != null) {
				mapView.getController().animateTo(mGeoPoint);
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

	/*
	 * This runnable is used to create a thread that allows
	 * PlacesItemizedOverlay to call the drawPlaces() function after it has
	 * removed a Place from the database
	 */
	public Runnable mUpdate = new Runnable() {
		public void run() {
			while (nowRunning) {
				synchronized (this) {
					try {
						wait();
					} catch (InterruptedException e) {

						e.printStackTrace();
					}
				}
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						drawPlaces();
					}
				});
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.remindermap_layout);
		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);

		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		GeoPoint oldGeo = null;

		if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			try {
				oldGeo = new GeoPoint((int) (locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER).getLatitude() * 1E6),
						(int) (locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER).getLongitude() * 1E6));
				mapView.getController().animateTo(oldGeo);
			} catch (NullPointerException e) {
				Log.e(this.getClass().getName(), "No valid previous location, requesting a single location update instead.");
				Toast.makeText(this, "No valid previous location, requesting a single location update instead.", Toast.LENGTH_LONG).show();
				if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
					locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mListener, null);
				} else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
					locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mListener, null);
				}

			}
			mapView.getController().setZoom(18);
		}
		mapOverlay = new MapOverlay();
		List<Overlay> listOfOverlays = mapView.getOverlays();
		listOfOverlays.clear();
		listOfOverlays.add(mapOverlay);

		mapView.invalidate();
		PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);

		SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);

		if (placeOpenHelper.numberOfPlaces() >= 1 && !settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
			startService(new Intent(ReminderMap_Activity.this, PopupTrigger.class));
		}

		searchResultsListView = (ListView) findViewById(R.id.listview);
		searchResultsListView.setOnItemClickListener(this);

		mGeoCoder = new Geocoder(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		drawPlaces();
		nowRunning = true;
		new Thread(mUpdate).start();
	}

	@Override
	public void onNewIntent(Intent intent) {
		Log.i(this.getClass().getName(), "onNewIntent hit");
		Bundle extras = intent.getExtras();
		if (extras != null && extras.containsKey(PopupTrigger.NotificationLongitude) && extras.containsKey(PopupTrigger.NotificationLatitude)) {

			GeoPoint notificationPoint = new GeoPoint(extras.getInt(PopupTrigger.NotificationLatitude), extras.getInt(PopupTrigger.NotificationLongitude));
			mapView.getController().animateTo(notificationPoint);
			mapView.getController().setZoom(17);
			mapView.getOverlays().get(mapView.getOverlays().size() - 1).onTap(notificationPoint, mapView);

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.remindermap_menu, menu);
		SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
		if (settings.getBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED, false)) {
			MenuItem read_aloud_item = (MenuItem) menu.findItem(R.id.menu_ReadAloud);
			read_aloud_item.setChecked(true);
		}
		if (settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
			MenuItem disable_service_item = (MenuItem) menu.findItem(R.id.menu_DisableService);
			disable_service_item.setCheckable(true);
			disable_service_item.setChecked(true);
		}
		SearchView avSearch = (SearchView) menu.findItem(R.id.menu_Search).getActionView();

		avSearch.setIconifiedByDefault(true);

		avSearch.setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextChange(String query) {
				if (query.isEmpty())
					searchResultsListView.setVisibility(View.GONE);
				else {
					searchResultsListView.setVisibility(View.VISIBLE);
					submitLocationQuery(query);
				}
				return true;
			}

			@Override
			public boolean onQueryTextSubmit(String query) {
				if (!query.isEmpty()) {

					searchResultsListView.setVisibility(View.VISIBLE);
					submitLocationQuery(query);
				}

				return true;
			}

		});

		return true;
	}

	/*
	 * This function creates an launches a thread to request a geocoder response
	 * for an address.
	 */
	private void submitLocationQuery(final String query) {
		Thread thrd = new Thread() {
			public void run() {
				Message threadMessage = new Message();
				try {
					foundAddresses = mGeoCoder.getFromLocationName(query, 5);
					// Addresses have been GeoCoded, notify gcCallbackHandler of
					// success.
					threadMessage.arg1 = 1;
				} catch (IOException e) {
					foundAddresses = null;
					Log.e(this.getClass().getName(), "Failed to connect to geocoder service", e);
					// Addresses have not been GeoCoded, notify
					// gcCallbackHandler of failure.
					threadMessage.arg1 = 0;
				}
				gcCallbackHandler.sendMessage(threadMessage);
			}
		};
		thrd.start();

	}

	private Handler gcCallbackHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			ArrayList<String> stringArray = new ArrayList<String>();
			if (msg.arg1 == 1) {
				if (foundAddresses != null && !foundAddresses.isEmpty()) {

					for (int i = 0; i < foundAddresses.size(); i++) {

						stringArray.add(foundAddresses.get(i).getAddressLine(0) + nullFilter(foundAddresses.get(i).getAddressLine(1))
								+ nullFilter(foundAddresses.get(i).getLocality()) + nullFilter(foundAddresses.get(i).getSubAdminArea())
								+ nullFilter(foundAddresses.get(i).getCountryName()));
					}
				} else {
					stringArray.add("Address Not Found");
				}
			} else if (msg.arg1 == 0) {

				stringArray.add("Failed to connect to network.");
				stringArray.add("Please check your network connection and retry.");

			}
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(parentContext, android.R.layout.simple_list_item_1, stringArray);
			searchResultsListView.setAdapter(adapter);
		}

	};

	public String nullFilter(String s) {
		if (s != null)
			return ", " + s;
		else
			return "";
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	public void drawPlaces() {
		PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
		Cursor placeCursor = placeOpenHelper.getPlaces();

		Drawable drawable = getResources().getDrawable(R.drawable.ic_launcher);
		PlacesItemizedOverlay placeOverlay = new PlacesItemizedOverlay(drawable, this);

		int rows = placeCursor.getCount();
		if (rows >= 1) {

			if (placeCursor.moveToFirst()) {
				do {
					placeOverlay.addOverlay(
							new OverlayItem(new GeoPoint(placeCursor.getInt(0), placeCursor.getInt(1)), "Popup Note:", placeCursor.getString(2)), mUpdate);
				} while (placeCursor.moveToNext());
			}
		}
		placeCursor.close();
		placeOpenHelper.close();

		mapOverlay = new MapOverlay();
		mapView.getOverlays().clear();

		mapView.getOverlays().add(mapOverlay);
		mapView.getOverlays().add(placeOverlay);
		mapView.invalidate();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		SharedPreferences settings = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		boolean checkstatus;
		switch (item.getItemId()) {
		case R.id.menu_Search:

			return true;
		case R.id.menu_CenterMap:
			LocationManager mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

			Criteria mCriteria = new Criteria();
			if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				mCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
			} else if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				mCriteria.setAccuracy(Criteria.ACCURACY_FINE);
			}
			mCriteria.setCostAllowed(false);
			Toast.makeText(this, "Centering map on your location.", Toast.LENGTH_SHORT).show();

			mLocationManager.requestSingleUpdate(mCriteria, mListener, null);
			return true;

		case R.id.menu_ReadAloud:
			boolean read_aloud_result = settings.getBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED, false);
			editor.putBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED, !read_aloud_result);
			editor.commit();

			Log.i(this.getClass().getName(), "Read_Aloud_Enabled changed to " + !read_aloud_result);
			checkstatus = item.isChecked();
			item.setChecked(!checkstatus);
			return true;
		case R.id.menu_DisableService:
			boolean service_disabled_result = settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false);
			editor.putBoolean(PlaceOpenHelper.SERVICE_DISABLED, !service_disabled_result);
			editor.commit();

			Log.i(this.getClass().getName(), "Service Disabled " + !service_disabled_result);
			checkstatus = item.isChecked();
			item.setChecked(!checkstatus);
			if (service_disabled_result) {
				startService(new Intent(ReminderMap_Activity.this, PopupTrigger.class));
			} else {
				stopService(new Intent(ReminderMap_Activity.this, PopupTrigger.class));
			}
			return true;
		case R.id.menu_About:
			Intent intent = new Intent(this, AboutPage_Activity.class);
			startActivity(intent);
			return true;
		}

		return false;
	}

	/*
	 * This code tells the Thread that it should break it's loop and stop when
	 * the Activity Pauses, I'm not sure if this code is necessary, because
	 * Android appears to clean up this thread itself.
	 */
	@Override
	protected void onPause() {
		super.onPause();
		nowRunning = false;
		synchronized (mUpdate) {
			mUpdate.notify();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (foundAddresses != null && foundAddresses.size() > position) {
			searchResultsListView.setVisibility(View.GONE);

			GeoPoint foundGeo = new GeoPoint((int) (foundAddresses.get(position).getLatitude() * 1E6),
					(int) (foundAddresses.get(position).getLongitude() * 1E6));

			Log.i(this.getClass().toString(), "Zooming to " + foundAddresses.get(position).getLatitude() + "," + foundAddresses.get(position).getLongitude());
			mapView.getController().animateTo(foundGeo);
		}
	}
}
