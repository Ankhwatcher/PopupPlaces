package ie.appz.popupplaces;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class AboutPage_Activity extends SherlockActivity {
	Context classContext = this;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.aboutpage_layout);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		((TextView) findViewById(R.id.aboutRequest)).setOnTouchListener(aboutRequest_Touch);
		((TextView) findViewById(R.id.aboutReport)).setOnTouchListener(aboutReport_Touch);

		ListView aboutListView = (ListView) findViewById(R.id.aboutListView);
		ArrayList<String> mArrayList = new ArrayList<String>(Arrays.asList(this.getResources().getStringArray(R.array.about_items)));

		String versionName = "unavailable";
		try {
			versionName = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		mArrayList.add("Version: " + versionName);

		ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mArrayList);
		aboutListView.setAdapter(arrayAdapter);
		aboutListView.setOnItemClickListener(this.listViewOnItemClickListener);
	}

	OnItemClickListener listViewOnItemClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			String[] link_array = classContext.getResources().getStringArray(R.array.about_links);
			if (!link_array[position].equals("lemon")) {
				Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link_array[position]));
				startActivity(myIntent);
			}
		}
	};

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			this.onBackPressed();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private OnTouchListener aboutReport_Touch = new OnTouchListener() {
		public boolean onTouch(View v, MotionEvent me) {
			TextView textView = (TextView) v;

			if (me.getAction() == MotionEvent.ACTION_DOWN) {
				textView.setBackgroundColor(getResources().getColor(R.color.solid_red));

				Intent emailIntent = new Intent(Intent.ACTION_SEND);
				emailIntent.setType("message/rfc822");
				emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { getResources().getString(R.string.email_popupplaces_address) });
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getResources().getString(R.string.email_report) + " ");
				startActivity(Intent.createChooser(emailIntent, "Send Email"));
			}
			if (me.getAction() == MotionEvent.ACTION_UP) {
				textView.setBackgroundColor(getResources().getColor(R.color.translucent_red));
			}
			return false;
		}
	};

	private OnTouchListener aboutRequest_Touch = new OnTouchListener() {
		public boolean onTouch(View v, MotionEvent me) {
			TextView textView = (TextView) v;

			if (me.getAction() == MotionEvent.ACTION_DOWN) {
				textView.setBackgroundColor(getResources().getColor(R.color.highlight_cyan));

				Intent emailIntent = new Intent(Intent.ACTION_SEND);
				emailIntent.setType("message/rfc822");
				emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { getResources().getString(R.string.email_popupplaces_address) });
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getResources().getString(R.string.email_request) + " ");
				startActivity(Intent.createChooser(emailIntent, "Send Email"));
			}
			if (me.getAction() == MotionEvent.ACTION_UP) {
				textView.setBackgroundColor(getResources().getColor(R.color.translucent_cyan));
			}
			return false;
		}
	};
}
