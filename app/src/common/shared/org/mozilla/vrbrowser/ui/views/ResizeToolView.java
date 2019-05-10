package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.Widget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class ResizeToolView extends FrameLayout {
    private Widget mTargetWidget;
    private WidgetManagerDelegate mWidgetDelegate;
    private TextView mEditX;
    private TextView mEditY;
    private TextView mEditZ;
    private TextView mEditW;
    private TextView mEditA;

    public ResizeToolView(@NonNull Context context) {
        super(context);
        initialize();
    }

    public ResizeToolView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ResizeToolView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private static final float delta_t = 0.05f / WidgetPlacement.WORLD_DPI_RATIO;
    private static final float delta_w = 0.05f;
    private static final float delta_a = (float)Math.toRadians(0.5f);

    private void initialize() {
        inflate(getContext(), R.layout.resize_tool, this);
        mEditX = findViewById(R.id.editX);
        mEditY = findViewById(R.id.editY);
        mEditZ = findViewById(R.id.editZ);
        mEditW = findViewById(R.id.editW);
        mEditA = findViewById(R.id.editA);

        Button xPlus = findViewById(R.id.btnXPlus);
        xPlus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().translationX += delta_t;
            updateWidget();
        });

        Button yPlus = findViewById(R.id.btnYPlus);
        yPlus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().translationY += delta_t;
            updateWidget();
        });

        Button zPlus = findViewById(R.id.btnZPlus);
        zPlus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().translationZ += delta_t;
            updateWidget();
        });

        Button wPlus = findViewById(R.id.btnWPlus);
        wPlus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().worldWidth += delta_w;
            updateWidget();
        });

        Button aPlus = findViewById(R.id.btnAPlus);
        aPlus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().rotation += delta_a;
            updateWidget();
        });

        Button xMinus = findViewById(R.id.btnXMinus);
        xMinus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().translationX -= delta_t;
            updateWidget();
        });

        Button yMinus = findViewById(R.id.btnYMinus);
        yMinus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().translationY -= delta_t;
            updateWidget();
        });

        Button zMinus = findViewById(R.id.btnZMinus);
        zMinus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().translationZ -= delta_t;
            updateWidget();
        });

        Button wMinus = findViewById(R.id.btnWMinus);
        wMinus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().worldWidth -= delta_w;
            updateWidget();
        });

        Button aMinus = findViewById(R.id.btnAMinus);
        aMinus.setOnClickListener(v -> {
            mTargetWidget.getPlacement().rotation -= delta_a;
            updateWidget();
        });

    }

    public void setTargetWidget(Widget aWidget, WidgetManagerDelegate aDelegate) {
        mTargetWidget = aWidget;
        mWidgetDelegate = aDelegate;
        displayTexts();
    }

    private String strFloat(float value) {
        return String.format ("%.2f", value);
    }

    private void displayTexts() {
        WidgetPlacement placement = mTargetWidget.getPlacement();
        mEditX.setText("x: " + strFloat(placement.translationX * WidgetPlacement.WORLD_DPI_RATIO) + "m");
        mEditY.setText("y: " + strFloat(placement.translationY * WidgetPlacement.WORLD_DPI_RATIO) + "m");
        mEditZ.setText("z: " + strFloat(placement.translationZ * WidgetPlacement.WORLD_DPI_RATIO) + "m");
        mEditW.setText("width: " + strFloat(placement.worldWidth) + "m");
        mEditA.setText("angle: " + strFloat((float)Math.toDegrees(placement.rotation)) + "º");
    }

    private void updateWidget() {
        displayTexts();
        mWidgetDelegate.updateWidget(mTargetWidget);
    }



}
