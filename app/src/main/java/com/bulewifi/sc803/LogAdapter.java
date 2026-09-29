package com.bulewifi.sc803;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bulewifi.sc803.model.LogItem;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LogItem> mItems = new ArrayList<>();

    public void setItems(List<LogItem> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void addItem(LogItem item) {
        if (item != null) {
            mItems.add(item);
            notifyItemInserted(mItems.size() - 1);
        }
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogItem item = mItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTimestamp;
        private final TextView tvEventType;
        private final TextView tvDeviceName;
        private final TextView tvDeviceAddress;
        private final TextView tvDetails;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvEventType = itemView.findViewById(R.id.tv_event_type);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvDeviceAddress = itemView.findViewById(R.id.tv_device_address);
            tvDetails = itemView.findViewById(R.id.tv_details);
        }

        public void bind(LogItem item) {
            tvTimestamp.setText(item.getTimestamp());
            tvEventType.setText(item.getEventType());
            tvDeviceName.setText(item.getDeviceName());
            tvDeviceAddress.setText(item.getDeviceAddress());

            if (item.getDetails() != null && !item.getDetails().isEmpty()) {
                tvDetails.setVisibility(View.VISIBLE);
                tvDetails.setText(item.getDetails());
            } else {
                tvDetails.setVisibility(View.GONE);
            }

            // E-ink styling: high contrast black/white distinctions
            if (item.isConnected()) {
                tvEventType.setTypeface(null, Typeface.BOLD);
                tvEventType.setTextColor(Color.WHITE);
                tvEventType.setBackgroundColor(Color.BLACK);
            } else if (item.isDisconnected()) {
                tvEventType.setTypeface(null, Typeface.BOLD);
                tvEventType.setTextColor(Color.BLACK);
                tvEventType.setBackgroundResource(R.drawable.badge_border);
            } else {
                tvEventType.setTypeface(null, Typeface.NORMAL);
                tvEventType.setTextColor(Color.DKGRAY);
                tvEventType.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }
}
