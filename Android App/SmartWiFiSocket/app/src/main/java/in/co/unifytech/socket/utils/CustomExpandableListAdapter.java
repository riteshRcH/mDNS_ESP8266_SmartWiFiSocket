package in.co.unifytech.socket.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.google.gson.internal.LinkedHashTreeMap;

import java.util.List;

import in.co.unifytech.R;

public class CustomExpandableListAdapter extends BaseExpandableListAdapter
{
    private Context context;
    private List<String> expandableGroupHeadersText;
    private LinkedHashTreeMap<String, List<String>> expandableListViewData;

    public CustomExpandableListAdapter(Context context, List<String> expandableGroupHeadersText, LinkedHashTreeMap<String, List<String>> expandableListViewData)
    {
        this.context = context;
        this.expandableGroupHeadersText = expandableGroupHeadersText;
        this.expandableListViewData = expandableListViewData;
    }

    @Override
    public Object getChild(int listPosition, int expandedListPosition)
    {
        return this.expandableListViewData.get(this.expandableGroupHeadersText.get(listPosition))
                .get(expandedListPosition);
    }

    @Override
    public long getChildId(int listPosition, int expandedListPosition) {
        return expandedListPosition;
    }

    @Override
    public View getChildView(int listPosition, final int expandedListPosition, boolean isLastChild, View convertView, ViewGroup parent)
    {
        final String expandedListText = (String) getChild(listPosition, expandedListPosition);
        if (convertView == null)
        {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (layoutInflater != null)
                convertView = layoutInflater.inflate(R.layout.expandable_list_view_child_item, null);
        }
        if (convertView != null)
        {
            TextView expandedListTextView = convertView.findViewById(R.id.expandedListItem);
            expandedListTextView.setText(expandedListText);
        }
        return convertView;
    }

    @Override
    public int getChildrenCount(int listPosition)
    {
        return this.expandableListViewData.get(this.expandableGroupHeadersText.get(listPosition)).size();
    }

    @Override
    public Object getGroup(int listPosition)
    {
        return this.expandableGroupHeadersText.get(listPosition);
    }

    @Override
    public int getGroupCount()
    {
        return this.expandableGroupHeadersText.size();
    }

    @Override
    public long getGroupId(int listPosition)
    {
        return listPosition;
    }

    @Override
    public View getGroupView(int listPosition, boolean isExpanded, View convertView, ViewGroup parent)
    {
        String listTitle = (String) getGroup(listPosition);
        if (convertView == null)
        {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (layoutInflater != null)
                convertView = layoutInflater.inflate(R.layout.expandable_list_view_group_header, null);
        }
        if (convertView != null)
        {
            TextView listTitleTextView = convertView.findViewById(R.id.listTitle);
            listTitleTextView.setTypeface(null, Typeface.BOLD);
            listTitleTextView.setText(listTitle);
        }
        return convertView;
    }

    @Override
    public boolean hasStableIds()
    {
        return false;
    }

    @Override
    public boolean isChildSelectable(int listPosition, int expandedListPosition)
    {
        return true;
    }
}
