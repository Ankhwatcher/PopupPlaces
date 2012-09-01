package ie.appz.popupplaces;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;

public class ReminderMap_Activity extends MapActivity {

	private MapView mapView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.remindermap_layout);
		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		GeoPoint oldGeo = new GeoPoint((int) (locationManager
				.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
				.getLatitude() * 1E6), (int) (locationManager
				.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
				.getLongitude() * 1E6));

		mapView.getController().animateTo(oldGeo);
		mapView.getController().setZoom(14);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.remindermap_menu, menu);
		return true;
	}

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}
}
