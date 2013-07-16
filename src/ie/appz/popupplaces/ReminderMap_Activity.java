package ie.appz.popupplaces;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;
import com.actionbarsherlock.widget.SearchView.OnQueryTextListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.HashBiMap;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import ie.appz.popupplaces.services.PopupTrigger;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static android.widget.Toast.makeText;

public class ReminderMap_Activity extends SherlockFragmentActivity implements
        OnItemClickListener, GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener, LocationListener {
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    // private boolean nowRunning = false;
    private static String ItemChanged = "item_changed";
    private OnMarkerDragListener onMarkerDragListener = new OnMarkerDragListener() {
        LatLng startingLatLng;

        @Override
        public void onMarkerDrag(Marker marker) {

        }

        @Override
        public void onMarkerDragEnd(Marker marker) {
            if (!marker.getPosition().equals(startingLatLng)) {
                PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(
                        ReminderMap_Activity.this);
                placeOpenHelper.movePlace(markerBiMap.inverse().get(marker),
                        marker.getPosition());
                Log.i(this.getClass().getName(), "Popup Place #"
                        + markerBiMap.inverse().get(marker) + " moved.");
                /*
                 * Tell PopupTrigger that this item has been removed from the
				 * Database.
				 */
                Intent intent = new Intent(parentContext, PopupTrigger.class);
                SharedPreferences settings = parentContext
                        .getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
                if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED,
                        false)) {
                    parentContext.startService(intent);
                }
                placeOpenHelper.close();
            }
            //marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_launcher));

        }

        @Override
        public void onMarkerDragStart(Marker marker) {
            startingLatLng = marker.getPosition();
            //marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_notification));
        }
    };
    private OnMapLongClickListener onMapLongClickListener = new OnMapLongClickListener() {
        @Override
        public void onMapLongClick(final LatLng point) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    parentContext);
            alertDialogBuilder.setInverseBackgroundForced(true);
            TextView dialogCustomTitle = (TextView) getLayoutInflater()
                    .inflate(R.layout.dialog_custom_title, null);

            assert dialogCustomTitle != null;
            dialogCustomTitle.setText(R.string.newplacedialog_text);

            final EditText dialogCustomEditText = (EditText) getLayoutInflater()
                    .inflate(R.layout.dialog_custom_edittext, null);

            alertDialogBuilder.setCustomTitle(dialogCustomTitle).setView(
                    dialogCustomEditText);

            final PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(
                    ReminderMap_Activity.this);

            // Click on the Save Button
            alertDialogBuilder.setPositiveButton(
                    getResources().getText(R.string.save),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            searchResultsListView.setVisibility(View.GONE);

                            String popupNote = dialogCustomEditText.getText()
                                    .toString();
                            if (popupNote.length() > 0) {

                                placeOpenHelper.addPlace(point, popupNote);

                                Toast toast = makeText(
                                        parentContext, point.latitude
                                        + "," + point.longitude
                                        + ": \"" + popupNote
                                        + "\" added.",
                                        LENGTH_LONG);
                                toast.show();
                                Intent intent = new Intent(
                                        ReminderMap_Activity.this,
                                        PopupTrigger.class);

                                SharedPreferences settings = getSharedPreferences(
                                        PlaceOpenHelper.PREFS_NAME, 0);
                                if (!settings
                                        .getBoolean(
                                                PlaceOpenHelper.SERVICE_DISABLED,
                                                false)) {
                                    startService(intent);
                                }
                                drawPlaces();
                            }
                            dialog.dismiss();

                        }
                    }).setNegativeButton(
                    getResources().getText(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

            // Click on the Cancel Button
            alertDialogBuilder.create().show();
            placeOpenHelper.close();

        }

    };
    private OnInfoWindowClickListener onInfoWindowClickListener;

    {
        onInfoWindowClickListener = new OnInfoWindowClickListener() {

            @Override
            public void onInfoWindowClick(final Marker marker) {
                if (marker != null) {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                            parentContext);
                    alertDialogBuilder.setInverseBackgroundForced(true);
                    TextView dialogCustomTitle = (TextView) getLayoutInflater()
                            .inflate(R.layout.dialog_custom_title, null);
                    dialogCustomTitle.setText(R.string.newplacedialog_text);

                    final EditText dialogCustomEditText = (EditText) getLayoutInflater()
                            .inflate(R.layout.dialog_custom_edittext, null);
                    dialogCustomEditText.setText(marker.getSnippet());

                    alertDialogBuilder.setCustomTitle(dialogCustomTitle).setView(
                            dialogCustomEditText);
                    final PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(
                            parentContext);


                    alertDialogBuilder.setPositiveButton(
                            getResources().getText(R.string.save),
                            new DialogInterface.OnClickListener() {

                                // Click on the Save Button
                                @Override

                                public void onClick(DialogInterface dialog, int which) {

                                    String popupNote = dialogCustomEditText.getText()
                                            .toString();
                                    if (popupNote.length() > 0) {
                                        int columnId = markerBiMap.inverse()
                                                .get(marker);
                                        placeOpenHelper.editPlace(columnId, popupNote);

                                        Toast toast = makeText(parentContext,
                                                marker.getPosition().toString()
                                                        + ", \"" + popupNote
                                                        + "\" added.",
                                                LENGTH_LONG);
                                        toast.show();

                                    }
                                    drawPlaces();

                                    dialog.dismiss();

                                }
                            }).setNegativeButton(
                            getResources().getText(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                // Click on the Cancel Button
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    dialog.cancel();
                                }
                            });
                    alertDialogBuilder.setNeutralButton(
                            getResources().getText(R.string.delete),
                            new DialogInterface.OnClickListener() {
                                // Click on the Delete Button
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    PlaceOpenHelper placeHelper = new PlaceOpenHelper(
                                            parentContext);

                                    placeHelper.deletePlace(markerBiMap.inverse().get(
                                            marker));
                                    placeHelper.close();

                                    // Tell PopupTrigger that this item has been removed
                                    // from
                                    // the Database.
                                    Intent intent = new Intent(parentContext,
                                            PopupTrigger.class);
                                    Bundle extrasBundle = new Bundle();
                                    extrasBundle.putBoolean(
                                            ReminderMap_Activity.ItemChanged, false);

                                    extrasBundle
                                            .putInt(PopupTrigger.NotificationLatitude,
                                                    (int) (marker.getPosition().latitude * 1E6));

                                    extrasBundle
                                            .putInt(PopupTrigger.NotificationLongitude,
                                                    (int) (marker.getPosition().longitude * 1E6));
                                    intent.putExtras(extrasBundle);
                                    SharedPreferences settings = parentContext
                                            .getSharedPreferences(
                                                    PlaceOpenHelper.PREFS_NAME, 0);
                                    if (!settings.getBoolean(
                                            PlaceOpenHelper.SERVICE_DISABLED, false)) {
                                        parentContext.startService(intent);
                                    }

                                    drawPlaces();
                                    dialog.dismiss();

                                }
                            });
                    alertDialogBuilder.create().show();
                    placeOpenHelper.close();
                }
            }
        };
    }

    private GoogleMap googleMap;
    // private MapOverlay mapOverlay;
    private Menu mMenu = null;
    private ListView searchResultsListView;
    private List<Address> foundAddresses = null;
    private Geocoder mGeoCoder = null;
    private HashBiMap<Integer, Marker> markerBiMap;
    private Context parentContext = this;
    private ConnectionResult connectionResult;
    // Define an object that holds accuracy and frequency parameters
    private LocationRequest mLocationRequest;
    private LocationClient mLocationClient;
    private Handler gcCallbackHandler = new GCCustomHandler(this);

    public static String nullFilter(String s) {
        if (s != null)
            return ", " + s;
        else
            return "";
    }

    // Define a callback method that receives location updates
    @Override
    public void onLocationChanged(Location location) {
        LatLng mLatLng = new LatLng(location.getLatitude(),
                location.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(mLatLng));
        mLocationClient.removeLocationUpdates(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remindermap_layout);

        googleMap = ((SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment)).getMap();

        googleMap.clear();
        googleMap.setOnMapLongClickListener(this.onMapLongClickListener);
        googleMap.setOnInfoWindowClickListener(onInfoWindowClickListener);
        googleMap.setOnMarkerDragListener(onMarkerDragListener);
        googleMap.setMyLocationEnabled(true);

        PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);

        SharedPreferences settings = getSharedPreferences(
                PlaceOpenHelper.PREFS_NAME, 0);

        if (placeOpenHelper.numberOfPlaces() >= 1
                && !settings
                .getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
            startService(new Intent(ReminderMap_Activity.this,
                    PopupTrigger.class));
        }

        searchResultsListView = (ListView) findViewById(R.id.listview);
        searchResultsListView.setOnItemClickListener(this);

        mGeoCoder = new Geocoder(this);
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();

        mLocationRequest
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1);

        mLocationClient = new LocationClient(this, this, this);

    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        drawPlaces();

        Bundle extras = getIntent().getExtras();

        if (extras != null && extras.containsKey(PlaceOpenHelper.COLUMN_ID)) {

            int notificationColumnId = extras.getInt(PlaceOpenHelper.COLUMN_ID);
            Marker extraMarker = markerBiMap.get(notificationColumnId);
            if (extraMarker != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        extraMarker.getPosition(), 16));
                extraMarker.showInfoWindow();
            }

        }
        SharedPreferences settings = getSharedPreferences(
                PlaceOpenHelper.PREFS_NAME, 0);
        if (settings.getBoolean(PlaceOpenHelper.FIRST_RUN, true)) {

            searchResultsListView.setVisibility(View.VISIBLE);
            ArrayList<String> stringArrayList = new ArrayList<String>();
            stringArrayList.add(getString(R.string.user_intruction_1));
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    parentContext, android.R.layout.simple_list_item_1,
                    stringArrayList);
            searchResultsListView.setAdapter(adapter);
            SharedPreferences.Editor mEditor = settings.edit();
            mEditor.putBoolean(PlaceOpenHelper.FIRST_RUN, false);
            mEditor.commit();
        }
    }

    @Override
    protected void onStart() {
        mLocationClient.connect();
        super.onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        mMenu = menu;
        getSupportMenuInflater().inflate(R.menu.remindermap_menu, menu);

        SharedPreferences settings = getSharedPreferences(
                PlaceOpenHelper.PREFS_NAME, 0);
        if (settings.getBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED, true)) {
            MenuItem read_aloud_item = menu
                    .findItem(R.id.menu_ReadAloud);
            read_aloud_item.setChecked(true);
        }
        if (settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
            MenuItem disable_service_item = menu
                    .findItem(R.id.menu_DisableService);
            disable_service_item.setCheckable(true);
            disable_service_item.setChecked(true);
        }

        MenuItem searchMenuItem = menu
                .findItem(R.id.action_search);

        SearchView avSearch = (com.actionbarsherlock.widget.SearchView) searchMenuItem
                .getActionView();
        avSearch.setIconifiedByDefault(true);

        avSearch.setOnQueryTextListener(new OnQueryTextListener() {

            @Override
            public boolean onQueryTextChange(String query) {
                if (query.length() == 0)
                    searchResultsListView.setVisibility(View.GONE);
                else {
                    searchResultsListView.setVisibility(View.VISIBLE);
                    submitLocationQuery(query);
                }
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query.length() >= 1) {
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
                    Log.e(this.getClass().getName(),
                            "Failed to connect to geocoder service", e);
                    // Addresses have not been GeoCoded, notify
                    // gcCallbackHandler of failure.
                    threadMessage.arg1 = 0;
                }
                gcCallbackHandler.sendMessage(threadMessage);
            }
        };
        thrd.start();

    }

    private void drawPlaces() {
        PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(this);
        Cursor placeCursor = placeOpenHelper.getPlaces();

        googleMap.clear();
        int rows = placeCursor.getCount();
        if (rows >= 1) {
            markerBiMap = HashBiMap.create(rows);
            if (placeCursor.moveToFirst()) {
                do {
                    LatLng latLng = new LatLng(placeCursor.getDouble(0),
                            placeCursor.getDouble(1));
                    markerBiMap
                            .put(placeCursor.getInt(3),
                                    googleMap.addMarker(new MarkerOptions()
                                            .icon(BitmapDescriptorFactory
                                                    .fromResource(R.drawable.ic_launcher))
                                            .title("Popup Note:")
                                            .snippet(placeCursor.getString(2))
                                            .draggable(true).position(latLng)));
                } while (placeCursor.moveToNext());
            }
        }
        placeCursor.close();
        placeOpenHelper.close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences settings = getSharedPreferences(
                PlaceOpenHelper.PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        boolean checkStatus;
        switch (item.getItemId()) {
            case R.id.action_search:

                return true;

            case R.id.menu_ReadAloud:
                boolean read_aloud_result = settings.getBoolean(
                        PlaceOpenHelper.READ_ALOUD_ENABLED, true);
                editor.putBoolean(PlaceOpenHelper.READ_ALOUD_ENABLED,
                        !read_aloud_result);
                editor.commit();

                Log.i(((Object) this).getClass().getName(), "Read_Aloud_Enabled changed to "
                        + !read_aloud_result);
                if (!read_aloud_result) {
                    makeText(parentContext, "Read Aloud enabled.",
                            LENGTH_SHORT).show();
                } else
                    makeText(parentContext, "Read Aloud disabled.",
                            LENGTH_SHORT).show();
                checkStatus = item.isChecked();
                item.setChecked(!checkStatus);
                return true;
            case R.id.menu_DisableService:
                boolean service_disabled_result = settings.getBoolean(
                        PlaceOpenHelper.SERVICE_DISABLED, false);
                editor.putBoolean(PlaceOpenHelper.SERVICE_DISABLED,
                        !service_disabled_result);
                editor.commit();

                Log.i(((Object) this).getClass().getName(), "Service Disabled "
                        + !service_disabled_result);
                checkStatus = item.isChecked();
                item.setChecked(!checkStatus);
                if (service_disabled_result) {
                    startService(new Intent(ReminderMap_Activity.this,
                            PopupTrigger.class));
                    makeText(parentContext, "Notifications enabled.",
                            LENGTH_SHORT).show();
                } else {
                    stopService(new Intent(ReminderMap_Activity.this,
                            PopupTrigger.class));
                    makeText(parentContext, "Notifications disabled.",
                            LENGTH_SHORT).show();
                }
                return true;
            case R.id.menu_Settings: {
                Intent intent = new Intent(this, SettingsPage_Activity.class);
                startActivity(intent);
                return true;
            }
            case R.id.menu_About: {
                Intent intent = new Intent(this, AboutPage_Activity.class);
                startActivity(intent);
                return true;
            }
        }

        return false;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        if (foundAddresses != null && foundAddresses.size() > position) {
            searchResultsListView.setVisibility(View.GONE);

            LatLng foundLatLng = new LatLng(foundAddresses.get(position)
                    .getLatitude(), foundAddresses.get(position).getLongitude());

            Log.i(((Object) this).getClass().getName(), "Zooming to "
                    + foundAddresses.get(position).getLatitude() + ","
                    + foundAddresses.get(position).getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(foundLatLng));
        }
    }

    @Override
    public boolean onSearchRequested() {
        mMenu.findItem(R.id.action_search).expandActionView();
        return false;
    }

    /*
     * Handle results returned to the FragmentActivity by Google Play services
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {

            case CONNECTION_FAILURE_RESOLUTION_REQUEST:
            /*
             * If the result code is Activity.RESULT_OK, try to connect again
			 */
                switch (resultCode) {
                    case Activity.RESULT_OK:
                /*
                 * Try the request again
				 */

                        break;
                }

        }
    }

    @SuppressWarnings("unused")
    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates", "Google Play services is available.");
            // Continue
            return true;
            // Google Play services was not available for some reason
        } else {
            // Get the error code
            int errorCode = connectionResult.getErrorCode();
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                    errorCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);
                // Show the error dialog in the DialogFragment
                errorFragment.show(getSupportFragmentManager(),
                        "Location Updates");
            }
            return false;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.w(((Object) this).getClass().getName(), "Connection Faied with result: "
                + result.toString());

    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(((Object) this).getClass().getName(),
                "Connected to Google Maps successfully, requesting Location.");
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onDisconnected() {
        Log.d(((Object) this).getClass().getName(), "Disconnected from Google Maps.");
        mLocationClient.removeLocationUpdates(this);
    }

    @Override
    protected void onStop() {
        // If the client is connected
        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }
        /*
         * After disconnect() is called, the client is considered "dead".
		 */
        mLocationClient.disconnect();
        super.onStop();
    }

    static private class GCCustomHandler extends Handler {
        // private Handler gcCallbackHandler = new Handler() {

        private WeakReference<ReminderMap_Activity> activityReference;

        public GCCustomHandler(ReminderMap_Activity mainActivity) {
            super();
            this.activityReference = new WeakReference<ReminderMap_Activity>(
                    mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            ReminderMap_Activity activity = activityReference.get();
            if (activity != null) {
                ArrayList<String> stringArray = new ArrayList<String>();
                if (msg.arg1 == 1) {
                    if (activity.foundAddresses != null
                            && !activity.foundAddresses.isEmpty()) {

                        for (int i = 0; i < activity.foundAddresses.size(); i++) {

                            stringArray.add(activity.foundAddresses.get(i)
                                    .getAddressLine(0)
                                    + nullFilter(activity.foundAddresses.get(i)
                                    .getAddressLine(1))
                                    + nullFilter(activity.foundAddresses.get(i)
                                    .getLocality())
                                    + nullFilter(activity.foundAddresses.get(i)
                                    .getSubAdminArea())
                                    + nullFilter(activity.foundAddresses.get(i)
                                    .getCountryName()));
                        }
                    } else {
                        stringArray.add("Address Not Found");
                    }
                } else if (msg.arg1 == 0) {

                    stringArray.add("Failed to connect to network.");
                    stringArray
                            .add("Please check your network connection and retry.");

                }
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        activity, android.R.layout.simple_list_item_1,
                        stringArray);
                activity.searchResultsListView.setAdapter(adapter);
            }
        }
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;

        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
}
