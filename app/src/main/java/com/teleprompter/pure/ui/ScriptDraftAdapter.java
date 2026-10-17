package com.teleprompter.pure.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teleprompter.pure.R;
import com.teleprompter.pure.database.ScriptEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史台词卡片流适配器 (ScriptDraftAdapter)
 * 极致响应：卡片即看即播，三行摘要，一键悬浮开播与全屏开播
 */
public class ScriptDraftAdapter extends RecyclerView.Adapter<ScriptDraftAdapter.ViewHolder> {

    public interface OnDraftActionListener {
        void onEdit(ScriptEntity script);
        void onLaunchFloat(ScriptEntity script);
        void onLaunchBoard(ScriptEntity script);
        void onDelete(ScriptEntity script);
    }

    private final Context mContext;
    private final List<ScriptEntity> mDataList = new ArrayList<>();
    private final OnDraftActionListener mListener;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault());

    public ScriptDraftAdapter(Context context, OnDraftActionListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }

    public void setScripts(List<ScriptEntity> list) {
        mDataList.clear();
        if (list != null) {
            mDataList.addAll(list);
        }
        notifyDataSetChanged();
    }

    public List<ScriptEntity> getScripts() {
        return mDataList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_script_draft, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScriptEntity script = mDataList.get(position);

        // 1. 标题
        holder.tvTitle.setText(TextUtils.isEmpty(script.title) ? "未命名台词" : script.title);

        // 2. 正文摘要 (最多3行)
        if (!TextUtils.isEmpty(script.content)) {
            holder.tvPreview.setText(script.content.trim());
            holder.tvPreview.setVisibility(View.VISIBLE);
        } else {
            holder.tvPreview.setText("暂无正文内容");
            holder.tvPreview.setVisibility(View.VISIBLE);
        }

        // 3. 更新日期
        long time = script.updateTime > 0 ? script.updateTime : System.currentTimeMillis();
        holder.tvDate.setText(mDateFormat.format(new Date(time)));

        // 4. 点击卡片主体 -> 编辑台词
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) mListener.onEdit(script);
        });

        // 5. 点击悬浮开播 -> 呼出悬浮窗
        holder.btnFloat.setOnClickListener(v -> {
            if (mListener != null) mListener.onLaunchFloat(script);
        });

        // 6. 点击全屏开播 -> 进入全屏提词板
        holder.btnBoard.setOnClickListener(v -> {
            if (mListener != null) mListener.onLaunchBoard(script);
        });

        // 7. 点击删除 -> 确认后删除
        holder.btnDelete.setOnClickListener(v -> {
            if (mListener != null) mListener.onDelete(script);
        });
    }

    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvPreview;
        TextView tvDate;
        View btnFloat;
        ImageView btnBoard;
        ImageView btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvPreview = itemView.findViewById(R.id.tv_preview);
            tvDate = itemView.findViewById(R.id.tv_date);
            btnFloat = itemView.findViewById(R.id.btn_float);
            btnBoard = itemView.findViewById(R.id.btn_board);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
