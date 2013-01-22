package ie.appz.popupplaces;

import java.util.ArrayList;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

public class PlacesItemizedOverlay extends ItemizedOverlay<OverlayItem> {
	private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
	private Runnable runDraw;
	Context clientContext;

	public PlacesItemizedOverlay(Drawable routeIcon) {
		super(boundCenter(routeIcon));
		populate();
	}

	public void addOverlay(OverlayItem overlay, Runnable mUpdate) {

		mOverlays.add(overlay);
		runDraw = mUpdate;
		populate();
	}

	@Override
	protected OverlayItem createItem(int i) {
		return mOverlays.get(i);
	}

	@Override
	public int size() {
		return mOverlays.size();
	}

	public PlacesItemizedOverlay(Drawable routeIcon, Context context) {
		super(boundCenter(routeIcon));
		this.clientContext = context;
		populate();
	}

	@Override
	protected boolean onTap(final int index) {
		final OverlayItem item = mOverlays.get(index);

		final Dialog dialog = new Dialog(this.clientContext);
		dialog.setContentView(R.layout.oldplacedialog_layout);
		dialog.setTitle(item.getTitle());
		final TextView textView = (TextView) dialog.findViewById(R.id.popupText);
		textView.setText(item.getSnippet());

		dialog.setCancelable(true);
		WindowManager.LayoutParams WMLP = dialog.getWindow().getAttributes();
		dialog.getWindow().setAttributes(WMLP);
		WMLP.gravity = Gravity.BOTTOM;
		WMLP.verticalMargin = 0.60f;

		Button okayButton = (Button) dialog.findViewById(R.id.okayButton);

		okayButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				dialog.dismiss();
			}
		});

		final Button editButton = (Button) dialog.findViewById(R.id.editButton);

		editButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				dialog.dismiss();
				onEdit(index);

			}
		});

		/* Click on the Delete Button */
		Button deleteButton = (Button) dialog.findViewById(R.id.deleteButton);
		deleteButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				PlaceOpenHelper placeHelper = new PlaceOpenHelper(PlacesItemizedOverlay.this.clientContext);
				placeHelper.deletePlace(item.getPoint(), item.getSnippet());
				placeHelper.close();
				/*
				 * Tell PopupTrigger that this item has been removed from the
				 * Database.
				 */
				Intent intent = new Intent(clientContext, PopupTrigger.class);
				Bundle extrasBundle = new Bundle();
				extrasBundle.putBoolean(ReminderMap_Activity.ItemChanged, false);
				extrasBundle.putInt(PopupTrigger.NotificationLatitude, item.getPoint().getLatitudeE6());
				extrasBundle.putInt(PopupTrigger.NotificationLongitude, item.getPoint().getLongitudeE6());
				intent.putExtras(extrasBundle);
				SharedPreferences settings = clientContext.getSharedPreferences(PlaceOpenHelper.PREFS_NAME, 0);
				if (!settings.getBoolean(PlaceOpenHelper.SERVICE_DISABLED, false)) {
					clientContext.startService(intent);
				}

				/*
				 * Notify the asynchronous thread that drawPlaces() needs to be
				 * run
				 */

				synchronized (runDraw) {
					runDraw.notify();
				}
				dialog.dismiss();

			}
		});
		dialog.show();
		return true;
	}

	public boolean onEdit(int index) {
		final OverlayItem item = mOverlays.get(index);

		final Dialog dialog = new Dialog(clientContext);
		dialog.setContentView(R.layout.newplacedialog_layout);
		dialog.setTitle(R.string.newplacedialog_text);
		dialog.setCancelable(true);
		final PlaceOpenHelper placeOpenHelper = new PlaceOpenHelper(clientContext);
		final EditText inputBox = (EditText) dialog.findViewById(R.id.editText1);
		inputBox.setText(item.getSnippet());

		/* Click on the Save Button */
		Button saveButton = (Button) dialog.findViewById(R.id.saveButton);
		saveButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				GeoPoint tappedPoint = item.getPoint();

				String popupNote = inputBox.getText().toString();
				if (popupNote.length() > 0) {

					placeOpenHelper.editPlace(tappedPoint, popupNote);

					Toast toast = Toast.makeText(clientContext, tappedPoint.toString() + ", \"" + popupNote + "\" added.", Toast.LENGTH_LONG);
					toast.show();

				}
				/*
				 * Notify the asynchronous thread that drawPlaces() needs to be
				 * run
				 */

				synchronized (runDraw) {
					runDraw.notify();
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

	}

	public void draw(android.graphics.Canvas canvas, MapView mapView, boolean shadow) {

		super.draw(canvas, mapView, false);

	}

}
