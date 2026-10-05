package ru.billyhargrove.pimobile.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.core.CatalogRow;

/** Renders the grouped workspace / session list. */
public final class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.Holder> {

    public interface Listener {
        void onSessionClick(CatalogRow row);

        void onTerminalClick(CatalogRow row);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    public static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }

    private final Listener listener;
    private final List<CatalogRow> rows = new ArrayList<>();

    public CatalogAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<CatalogRow> newRows) {
        final List<CatalogRow> oldRows = new ArrayList<>(rows);
        final List<CatalogRow> target = new ArrayList<>(newRows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldRows.size();
            }

            @Override
            public int getNewListSize() {
                return target.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldRows.get(oldPos).key().equals(target.get(newPos).key());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return fingerprint(oldRows.get(oldPos)).equals(fingerprint(target.get(newPos)));
            }
        });
        rows.clear();
        rows.addAll(target);
        diff.dispatchUpdatesTo(this);
    }

    public int size() {
        return rows.size();
    }

    private static String fingerprint(CatalogRow row) {
        return String.format(Locale.ROOT, "%s|%s|%s|%s|%s|%s|%d|%s",
                row.kind(), row.title(), row.subtitle(), row.badge(),
                row.status(), row.connected(), row.count(), row.readOnly());
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        int layout = viewType == TYPE_HEADER ? R.layout.item_catalog_header : R.layout.item_catalog_row;
        Holder holder = new Holder(inflater.inflate(layout, parent, false));
        if (viewType == TYPE_ROW) ExpressiveMotion.press(holder.itemView);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final CatalogRow row = rows.get(position);
        final View view = holder.itemView;
        if (row.isHeader()) {
            TextView title = view.findViewById(R.id.groupTitle);
            TextView count = view.findViewById(R.id.groupCount);
            title.setText(row.title());
            count.setText(String.valueOf(row.count()));
            view.setOnClickListener(null);
            view.setClickable(false);
            view.setContentDescription(row.title());
            return;
        }

        TextView title = view.findViewById(R.id.rowTitle);
        TextView subtitle = view.findViewById(R.id.rowSubtitle);
        TextView badge = view.findViewById(R.id.rowBadge);
        TextView badgeExtra = view.findViewById(R.id.rowBadgeExtra);
        View dot = view.findViewById(R.id.rowStatusDot);

        title.setText(row.title());
        String detail = row.subtitle();
        int modelSeparator = detail.lastIndexOf(" · ");
        if (modelSeparator >= 0) detail = detail.substring(modelSeparator + 3);
        subtitle.setText(detail);
        badge.setText(row.badge());
        badge.setBackgroundResource(StatusUi.sessionBadgeBackground(row.status()));
        badge.setTextColor(StatusUi.sessionBadgeColor(view.getContext(), row.status()));
        dot.setBackgroundTintList(ColorStateList.valueOf(
                StatusUi.sessionDotColor(view.getContext(), row.status(), row.connected())));

        if (row.readOnly()) {
            badgeExtra.setVisibility(View.GONE);
            badgeExtra.setText(R.string.read_only_badge);
        } else {
            badgeExtra.setVisibility(View.GONE);
            badgeExtra.setText("");
        }

        view.setContentDescription(row.readOnly()
                ? view.getContext().getString(R.string.cd_terminal_row, row.title())
                : view.getContext().getString(R.string.cd_session_row, row.title()));

        view.setOnClickListener(v -> {
            if (row.readOnly()) {
                listener.onTerminalClick(row);
            } else {
                listener.onSessionClick(row);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }
}
