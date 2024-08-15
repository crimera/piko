package app.revanced.integrations.twitter.settings.featureflags;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import app.revanced.integrations.shared.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class CustomAdapter extends RecyclerView.e<CustomAdapter.ViewHolder> {
    TextView flagTextView;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch enabled;

    ArrayList<FeatureFlag> flags;
    OnItemClickListener itemClickListener;
    OnItemCheckedChangeListener itemCheckedChangeListener;

    public CustomAdapter(ArrayList<FeatureFlag> flags) {
        this.flags = flags;
    }

    public void setItemClickListener(OnItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    public void setItemCheckedChangeListener(OnItemCheckedChangeListener itemCheckedChangeListener) {
        this.itemCheckedChangeListener = itemCheckedChangeListener;
    }

    public void notifyItemChanged(int i) {
        Method notifyMethod = null;
        for (Method declaredMethod : this.getClass().getSuperclass().getDeclaredMethods()) {
            if (declaredMethod.getParameterTypes().length==1) {
                int modifier = declaredMethod.getModifiers();
                Class<?> parameterType = declaredMethod.getParameterTypes()[0];
                if (parameterType==Integer.TYPE && (modifier & Modifier.FINAL) == Modifier.FINAL) {
                    notifyMethod = declaredMethod;
                    break;
                }
            }
        }

        try {
            if (notifyMethod!=null) {
                notifyMethod.invoke(this, i);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public final class ViewHolder extends RecyclerView.c0 {
        private int getAdapterPosition() {
            Method getAdapterPositionMethod = null;
            for (Method declaredMethod : this.getClass().getSuperclass().getDeclaredMethods()) {
                if (declaredMethod.getReturnType()==Integer.TYPE && declaredMethod.getParameterTypes().length==0) {
                    getAdapterPositionMethod = declaredMethod;
                    break;
                }
            }

            if (getAdapterPositionMethod!=null) {
                try {
                    return (int) getAdapterPositionMethod.invoke(this);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            return -1;
        }

        // a0() = getAdapterPosition()
        public ViewHolder(CustomAdapter adapter, View view) {
            super(view);
            flagTextView = view.findViewById(Utils.getResourceIdentifier("textView", "id"));

            view.setOnClickListener(view1 -> {
                if (itemClickListener!=null) itemClickListener.onClick(getAdapterPosition());
            });

            enabled = view.findViewById(Utils.getResourceIdentifier("enabled", "id"));
            enabled.setOnCheckedChangeListener((compoundButton, b) -> {
                if (itemCheckedChangeListener!=null) itemCheckedChangeListener.onCheck(b, getAdapterPosition());
            });
        }
    }

    @Override
    public RecyclerView.c0 onCreateViewHolder(int i, RecyclerView recyclerView){
        View view = LayoutInflater.from(recyclerView.getContext())
                .inflate(
                        Utils.getResourceIdentifier("item_row", "layout"),
                        recyclerView, false
                );
        return new ViewHolder(this, view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        flagTextView.setText(flags.get(i).getName());
        enabled.setChecked(flags.get(i).getEnabled());
    }

    // setCount abstract method, change this in revanced patches
    @Override
    public int getCount() {
        return flags.size();
    }
}

interface OnItemCheckedChangeListener {
    void onCheck(boolean checked, int position);
}

interface OnItemClickListener {
    void onClick(int position);
}