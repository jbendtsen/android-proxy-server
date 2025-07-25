package com.jackbendtsen.aserver;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import java.util.ArrayList;

public class StringDropDown implements SpinnerAdapter {
	public final Context ctx;
	public ArrayList<String> items = new ArrayList<String>();
	public ArrayList<DataSetObserver> observers = new ArrayList<DataSetObserver>();

	public StringDropDown(Context context) {
		this.ctx = context;
	}

	public void notifyItemsUpdated() {
		for (DataSetObserver o : observers) {
			o.onChanged();
		}
	}

	@Override
	public int getCount() {
		return items.size();
	}

	@Override
	public Object getItem(int position) {
		return items.get(position);
	}

	@Override
	public long getItemId(int position) {
		return (long)position;
	}

	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView != null) {
			((TextView)convertView).setText(items.get(position));
			return convertView;
		}
		TextView tv = new TextView(ctx);
		tv.setText(items.get(position));
		return tv;
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return getView(position, convertView, parent);
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return items.isEmpty();
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		observers.add(observer);
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		observers.remove(observer);
	}

	public CharSequence[] getAutofillOptions() {
		return null;
	}
}
