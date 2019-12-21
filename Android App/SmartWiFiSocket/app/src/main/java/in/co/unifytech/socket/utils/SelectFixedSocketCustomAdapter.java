package in.co.unifytech.socket.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import in.co.unifytech.R;
import in.co.unifytech.socket.pojos.PerFixedSocketConfig;

public class SelectFixedSocketCustomAdapter extends BaseAdapter
{
    private List<PerFixedSocketConfig> configuredFixedSockets;
    private static LayoutInflater inflater = null;

    public SelectFixedSocketCustomAdapter(Context context, List<PerFixedSocketConfig> configuredFixedSockets)
    {
        this.configuredFixedSockets = configuredFixedSockets;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount()
    {
        return configuredFixedSockets.size();
    }

    @Override
    public Object getItem(int position)
    {
        return configuredFixedSockets.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        if (convertView == null)
            convertView = inflater.inflate(R.layout.dialog_select_fixed_socket_list_view_item, null);

        TextView txtViewSocketName = convertView.findViewById(R.id.txtViewSocketName);
        txtViewSocketName.setText(configuredFixedSockets.get(position).toString());

        ImageView imgViewIsInternetModeConfiguredIndicator = convertView.findViewById(R.id.imgViewIsInternetModeConfiguredIndicator);
        imgViewIsInternetModeConfiguredIndicator.setVisibility(configuredFixedSockets.get(position).isInternetModeConfigured() ? View.VISIBLE : View.GONE);

        return convertView;
    }
}
