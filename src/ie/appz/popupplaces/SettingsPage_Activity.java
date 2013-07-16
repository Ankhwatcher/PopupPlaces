package ie.appz.popupplaces;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

/**
 * Created by rory on 09/07/13.
 */
public class SettingsPage_Activity extends SherlockActivity {
    private static int mSeekBarMin = 20;
    SeekBar.OnSeekBarChangeListener mSeekbarOnChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(PlaceOpenHelper.POP_RADIUS, i + mSeekBarMin)
                    .commit();
            mPopRadiusText.setText(getResources().getText(R.string.settingspage_popradius) + String.valueOf(mSeekBarMin + i));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };
    private SeekBar mSeekBar;
    private TextView mPopRadiusText;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settingspage_layout);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mSeekBar = (SeekBar) findViewById(R.id.seekBar);
        mPopRadiusText = (TextView) findViewById(R.id.popRadiusText);

    }

    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences(PlaceOpenHelper.PREFS_NAME, Context.MODE_PRIVATE);

        mSeekBar.setMax(130);
        int popRadius = sharedPreferences.getInt(PlaceOpenHelper.POP_RADIUS, 25);
        mSeekBar.setProgress(popRadius - mSeekBarMin);
        mSeekBar.setOnSeekBarChangeListener(mSeekbarOnChangeListener);
        mPopRadiusText.setText(getResources().getText(R.string.settingspage_popradius) + String.valueOf(popRadius));
    }


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
}