package com.zostio.master;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

public class MasterGrid extends RelativeLayout {

    int xGrids;

    public MasterGrid(Context context) {
        super(context);
    }

    public MasterGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MasterGrid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setup(int xGrids) {
        this.xGrids = xGrids;
    }

    public void addItem(View view) {

    }
}
