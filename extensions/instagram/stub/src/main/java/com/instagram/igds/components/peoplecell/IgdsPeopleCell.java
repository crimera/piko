package com.instagram.igds.components.peoplecell;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;
import com.instagram.ui.widget.gradientspinneravatarview.GradientSpinnerAvatarView;

public class IgdsPeopleCell extends LinearLayout {
    public IgdsPeopleCell(Context context) {
        super(context);
    }

    public final GradientSpinnerAvatarView getImageView(){return null;}
    public final TextView getPrimaryTextView(){return null;}
    public final TextView getSecondaryTextView(){return null;}
    public final TextView getAdditionalSupportingTextView(){return null;}
}
