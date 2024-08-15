package androidx.recyclerview.widget;

import android.content.Context;
import android.database.Observable;
import android.view.View;
import android.view.ViewGroup;

@SuppressWarnings("unused")
public class RecyclerView extends ViewGroup {
    public RecyclerView(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {

    }

    // LayoutManager
    public static class m {

    }

    // ViewHolder
    public static abstract class c0 {
        public c0(View view) {
        }

        // getAdapterPosition
        public final int Z() {
            return -1;
        }
    }

    // AdapterDataObservable
    public static class f extends Observable<g> {
        public final void b() {}
        public final void c(int i, int i2) {}
        public final void d(Object obj, int i, int i2) {}
        public final void e(int i, int i2) {}
        // notifyItemRangeRemoved maybe
        public final void f(int i, int i2) {}
    }

    // AdapterDataObserver
    public static abstract class g {}


        // Adapter
    public static abstract class e<VH extends c0> {
        public final f c = new f();

        // get count
        public abstract int getCount();

        public abstract c0 onCreateViewHolder(int i, RecyclerView recyclerView);

        public abstract void onBindViewHolder(VH vh, int i);

        public final void D(int i) {}
    }

    public void setLayoutManager(m m) {

    }

    public void setAdapter(e eVar) {

    }
}
