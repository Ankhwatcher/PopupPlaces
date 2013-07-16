package ie.appz.popupplaces.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

public class ResultAdapter extends BaseAdapter {
    ArrayList<String> items;
    Context context;

    public ResultAdapter(Context context, ArrayList<String> items) {
        this.items = items;
        this.context = context;
    }

    @Override
    public int getCount() {

        return items.size();
    }

    @Override
    public String getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // TODO Auto-generated method stub
        return null;
    }

    public void changeDataSet(ArrayList<String> mDataSearchList) {
        this.items = new ArrayList<String>();
        this.notifyDataSetChanged();
    }
}
