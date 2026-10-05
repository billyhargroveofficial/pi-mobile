package ru.billyhargrove.pimobile.ui;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.core.ChatMessage;
import ru.billyhargrove.pimobile.core.ImageRef;
import ru.billyhargrove.pimobile.core.TextSanitizer;
import ru.billyhargrove.pimobile.net.MediaLoader;

/**
 * Chat transcript adapter.
 *
 * <p>Text from the server is rendered as plain text through
 * {@link TextSanitizer} – no HTML is ever parsed. Images are loaded through
 * {@link MediaLoader}, which refuses any URL that is not on the configured
 * origin.</p>
 */
public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {

    /** Supplies locally staged bitmaps for messages that have not been echoed yet. */
    public interface LocalThumbProvider {
        Bitmap thumbnail(String requestId, int index);
    }

    public interface Listener {
        void onRetry(ChatMessage message);

        void onRestore(ChatMessage message);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout bubble;
        final TextView roleLabel;
        final TextView text;
        final LinearLayout images;
        final TextView toolName;
        final TextView localStatus;
        final LinearLayout localActions;

        Holder(View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.messageBubble);
            roleLabel = itemView.findViewById(R.id.messageRoleLabel);
            text = itemView.findViewById(R.id.messageText);
            images = itemView.findViewById(R.id.messageImages);
            toolName = itemView.findViewById(R.id.messageToolName);
            localStatus = itemView.findViewById(R.id.messageLocalStatus);
            localActions = itemView.findViewById(R.id.messageLocalActions);
        }
    }

    private final MediaLoader mediaLoader;
    private final LocalThumbProvider thumbProvider;
    private final Listener listener;
    private final List<ChatMessage> items = new ArrayList<>();
    private final java.util.Set<String> expandedTools = new java.util.HashSet<>();

    public MessageAdapter(MediaLoader mediaLoader, LocalThumbProvider thumbProvider, Listener listener) {
        this.mediaLoader = mediaLoader;
        this.thumbProvider = thumbProvider;
        this.listener = listener;
        setHasStableIds(false);
    }

    public void submit(List<ChatMessage> newItems) {
        List<ChatMessage> target = new ArrayList<>(newItems);
        if (submitPrefixUpdate(target)) {
            return;
        }
        submitWithDiff(target);
    }

    /**
     * Fast path for the streaming case: when the new list shares the exact prefix
     * of keys with the current one, only changed rows are rebound and only the new
     * tail is inserted. No full diff, no scroll jump, no view rebuild.
     */
    private boolean submitPrefixUpdate(List<ChatMessage> target) {
        if (target.size() < items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).stableKey().equals(target.get(i).stableKey())) {
                return false;
            }
        }
        int previousSize = items.size();
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < previousSize; i++) {
            if (!items.get(i).equals(target.get(i))) {
                changed.add(i);
            }
        }
        items.clear();
        items.addAll(target);
        for (Integer index : changed) {
            notifyItemChanged(index);
        }
        if (target.size() > previousSize) {
            notifyItemRangeInserted(previousSize, target.size() - previousSize);
        }
        return true;
    }

    private void submitWithDiff(List<ChatMessage> target) {
        final List<ChatMessage> oldItems = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return target.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos).stableKey().equals(target.get(newPos).stableKey());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos).equals(target.get(newPos));
            }
        });
        items.clear();
        items.addAll(target);
        diff.dispatchUpdatesTo(this);
    }

    public int size() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final ChatMessage message = items.get(position);
        final View row = holder.itemView;

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubble.getLayoutParams();
        params.gravity = message.role() == ChatMessage.Role.USER ? Gravity.END : Gravity.START;
        holder.bubble.setLayoutParams(params);
        holder.bubble.setBackgroundResource(bubbleBackground(message.role()));
        int available = row.getResources().getDisplayMetrics().widthPixels - dp(row.getContext(), 84);
        holder.text.setMaxWidth(Math.min(dp(row.getContext(), 560), available));
        holder.text.setTextSize(message.role() == ChatMessage.Role.TOOL_RESULT ? 12f : 15f);
        holder.text.setTypeface(message.role() == ChatMessage.Role.TOOL_RESULT ? android.graphics.Typeface.MONOSPACE : android.graphics.Typeface.DEFAULT);
        holder.roleLabel.setTextColor(row.getContext().getColor(message.role() == ChatMessage.Role.USER ? R.color.on_accent_soft : R.color.accent));

        holder.roleLabel.setText(StatusUi.roleLabel(row.getContext(), message.role()));

        String safeText = TextSanitizer.safe(message.text());
        if (safeText.isEmpty()) {
            holder.text.setVisibility(View.GONE);
        } else {
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(safeText);
        }

        if (message.toolName().isEmpty()) {
            holder.toolName.setVisibility(View.GONE);
        } else {
            holder.toolName.setVisibility(View.VISIBLE);
            holder.toolName.setText(row.getContext().getString(R.string.tool_name_format, message.toolName()));
        }

        boolean tool = message.role() == ChatMessage.Role.TOOL_RESULT;
        int padding = dp(row.getContext(), tool ? 4 : 12);
        holder.bubble.setPadding(padding, padding, padding, padding);
        android.widget.Button toggle = holder.itemView.findViewById(R.id.toolToggle);
        toggle.setVisibility(tool ? View.VISIBLE : View.GONE);
        holder.roleLabel.setVisibility(tool ? View.GONE : View.VISIBLE);
        if (tool) {
            boolean expanded = expandedTools.contains(message.stableKey());
            String state = "running".equals(message.toolStatus()) ? "выполняется…" : "pending".equals(message.toolStatus()) ? "ожидает" : "error".equals(message.toolStatus()) ? "ошибка" : "interrupted".equals(message.toolStatus()) ? "прерван" : "готово";
            toggle.setText((expanded ? "▾  " : "▸  ") + message.toolName() + " · " + state);
            toggle.setContentDescription((expanded ? "Свернуть " : "Развернуть ") + message.toolName() + ", " + state);
            toggle.setOnClickListener(v -> {
                if (!expandedTools.remove(message.stableKey())) expandedTools.add(message.stableKey());
                if (ExpressiveMotion.enabled()) androidx.transition.TransitionManager.beginDelayedTransition(holder.bubble, new androidx.transition.AutoTransition().setDuration(180));
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) notifyItemChanged(index);
            });
            holder.toolName.setVisibility(View.GONE);
            holder.text.setVisibility(expanded && !safeText.isEmpty() ? View.VISIBLE : View.GONE);
            if (expanded) bindImages(holder.images, message); else holder.images.setVisibility(View.GONE);
        } else {
            toggle.setOnClickListener(null);
            bindImages(holder.images, message);
        }

        if (message.localState() == ChatMessage.LocalState.NONE) {
            holder.localStatus.setVisibility(View.GONE);
            holder.localActions.setVisibility(View.GONE);
        } else {
            holder.localStatus.setVisibility(View.VISIBLE);
            holder.localStatus.setText(StatusUi.localStateLabel(row.getContext(), message.localState()));
            holder.localStatus.setTextColor(StatusUi.localStateColor(row.getContext(), message.localState()));
            boolean actionable = message.localState() == ChatMessage.LocalState.FAILED
                    || message.localState() == ChatMessage.LocalState.UNCERTAIN;
            holder.localActions.setVisibility(actionable ? View.VISIBLE : View.GONE);
            holder.itemView.findViewById(R.id.messageRetryButton).setOnClickListener(
                    v -> listener.onRetry(message));
            holder.itemView.findViewById(R.id.messageRestoreButton).setOnClickListener(
                    v -> listener.onRestore(message));
        }
    }

    private void bindImages(LinearLayout container, ChatMessage message) {
        List<ImageRef> images = message.images();
        StringBuilder imageKey = new StringBuilder(message.stableKey());
        for (ImageRef ref : images) imageKey.append('\n').append(ref.url());
        if (imageKey.toString().equals(container.getTag())) {container.setVisibility(images.isEmpty() ? View.GONE : View.VISIBLE);return;}
        container.setTag(imageKey.toString());
        container.removeAllViews();
        if (images.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        int limit = Math.min(images.size(), 3);
        for (int i = 0; i < limit; i++) {
            ImageRef ref = images.get(i);
            container.addView(createImageSlot(container, message, ref, i));
        }
    }

    private View createImageSlot(LinearLayout parent, ChatMessage message, ImageRef ref, int index) {
        final android.content.Context context = parent.getContext();
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        ImageView imageView = new ImageView(context);
        int height = context.getResources().getDimensionPixelSize(R.dimen.message_image_height);
        int width = Math.min(dp(context, 480), context.getResources().getDisplayMetrics().widthPixels - dp(context, 84));
        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(width, height);
        imageParams.topMargin = index == 0 ? 0 : dp(context, 6);
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setClipToOutline(true);
        imageView.setBackgroundResource(R.drawable.bg_image_placeholder);
        imageView.setContentDescription(context.getString(R.string.cd_message_image, index + 1));
        imageView.setImageDrawable(null);

        TextView errorView = new TextView(context);
        errorView.setTextSize(11f);
        errorView.setTextColor(context.getColor(R.color.danger));
        errorView.setVisibility(View.GONE);
        errorView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        wrapper.addView(imageView);
        wrapper.addView(errorView);

        final String tag = message.stableKey() + "#" + index;
        imageView.setTag(tag);

        if (isLocalRef(ref)) {
            Bitmap bitmap = thumbProvider == null ? null : thumbProvider.thumbnail(message.requestId(), index);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setOnClickListener(v -> ImageViewer.show(context, ref.url(), bitmap));
            } else {
                errorView.setVisibility(View.VISIBLE);
                errorView.setText(context.getString(R.string.image_unavailable));
            }
            return wrapper;
        }

        imageView.setImageDrawable(null);
        mediaLoader.load(ref.url(), new MediaLoader.Callback() {
            @Override
            public void onLoaded(String resolvedUrl, Bitmap bitmap) {
                if (tag.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setOnClickListener(v -> ImageViewer.show(context, ref.url(), null));
                }
            }

            @Override
            public void onFailed(String resolvedUrl, String errorMessage) {
                if (tag.equals(imageView.getTag())) {
                    imageView.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    errorView.setText(context.getString(R.string.image_blocked) + ": " + errorMessage);
                }
            }
        });
        return wrapper;
    }

    private static boolean isLocalRef(ImageRef ref) {
        return ref.url().startsWith(LOCAL_PREFIX);
    }

    /** Local (not yet echoed) attachments use this url prefix. */
    public static final String LOCAL_PREFIX = "local:";

    public static ImageRef localRef(int index, String mimeType) {
        return new ImageRef(LOCAL_PREFIX + index, mimeType);
    }

    private static int bubbleBackground(ChatMessage.Role role) {
        switch (role) {
            case USER:
                return R.drawable.bg_bubble_user;
            case TOOL_RESULT:
                return R.drawable.bg_bubble_tool;
            case CUSTOM:
                return R.drawable.bg_bubble_custom;
            case ASSISTANT:
            case UNKNOWN:
            default:
                return R.drawable.bg_bubble_assistant;
        }
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
