package ie.appz.popupplaces;

import java.util.ArrayList;

import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

public class PlacesItemizedOverlay extends ItemizedOverlay<OverlayItem> {
	private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
	private Runnable runDraw;
	Context clientContext;
	private static final int POPUP_PLACE_REACHED = 1;
	private static NotificationManager notificationManager;

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
	protected boolean onTap(int index) {
		final OverlayItem item = mOverlays.get(index);

		final Dialog dialog = new Dialog(this.clientContext);
		dialog.setContentView(R.layout.oldplacedialog_layout);
		dialog.setTitle(item.getTitle());
		TextView textView = (TextView) dialog.findViewById(R.id.popupText);
		textView.setText(item.getSnippet());
		dialog.setCancelable(true);
		Notification(item.getSnippet());

		Button okayButton = (Button) dialog.findViewById(R.id.okayButton);

		okayButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				dialog.dismiss();
			}
		});

		/* Click on the Delete Button */
		Button deleteButton = (Button) dialog.findViewById(R.id.deleteButton);
		deleteButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				PlaceOpenHelper placeHelper = new PlaceOpenHelper(
						PlacesItemizedOverlay.this.clientContext);
				placeHelper.deletePlace(item.getPoint(), item.getSnippet());
				placeHelper.close();
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

	public void draw(android.graphics.Canvas canvas, MapView mapView,
			boolean shadow) {

		super.draw(canvas, mapView, false);

	}

	private void Notification(CharSequence contentText) {
		Intent notificationIntent = new Intent(this.clientContext,
				ReminderMap_Activity.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(
				this.clientContext, 0, notificationIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder nCompatBuilder = new NotificationCompat.Builder(
				this.clientContext);
		nCompatBuilder.setAutoCancel(true);
		nCompatBuilder.setOngoing(false);
		// nCompatBuilder.setContentTitle(this.clientContext.getString(R.string.app_name));
		nCompatBuilder.setContentText(contentText);
		nCompatBuilder.setContentIntent(contentIntent);

		nCompatBuilder.setSmallIcon(R.drawable.ic_launcher);

		Notification notification = nCompatBuilder.build();

		notificationManager = (NotificationManager) this.clientContext.getSystemService(this.clientContext.NOTIFICATION_SERVICE);
		notificationManager.notify(POPUP_PLACE_REACHED, notification);
	}
}
