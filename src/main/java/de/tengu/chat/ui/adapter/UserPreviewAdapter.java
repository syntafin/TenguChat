package de.tengu.chat.ui.adapter;

import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.tengu.chat.R;
import de.tengu.chat.databinding.UserPreviewBinding;
import de.tengu.chat.entities.MucOptions;
import de.tengu.chat.ui.XmppActivity;
import de.tengu.chat.ui.util.AvatarWorkerTask;
import de.tengu.chat.ui.util.MucDetailsContextMenuHelper;

public class UserPreviewAdapter extends ListAdapter<MucOptions.User, UserPreviewAdapter.ViewHolder> implements View.OnCreateContextMenuListener {

    private MucOptions.User selectedUser = null;

    public UserPreviewAdapter() {
        super(UserAdapter.DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.user_preview, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MucOptions.User user = getItem(position);
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.avatar, R.dimen.media_size);
        viewHolder.binding.getRoot().setOnClickListener(v -> {
            final XmppActivity activity = XmppActivity.find(v);
            if (activity != null) {
                activity.highlightInMuc(user.getConversation(), user.getName());
            }
        });
        viewHolder.binding.getRoot().setOnCreateContextMenuListener(this);
        viewHolder.binding.getRoot().setTag(user);
        viewHolder.binding.getRoot().setOnLongClickListener(v -> {
            selectedUser = user;
            return false;
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MucDetailsContextMenuHelper.onCreateContextMenu(menu, v);
    }

    public MucOptions.User getSelectedUser() {
        return selectedUser;
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final UserPreviewBinding binding;

        private ViewHolder(UserPreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
