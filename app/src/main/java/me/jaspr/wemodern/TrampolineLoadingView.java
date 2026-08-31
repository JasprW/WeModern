package me.jaspr.wemodern;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Lightweight first frame shown while the opaque WeChat Activity is starting. */
final class TrampolineLoadingView extends FrameLayout {
    static final long DETAILS_DELAY_MS = 300L;
    private static final long AVATAR_ENTER_DURATION_MS = 160L;

    private final ImageView avatarView;
    private final LinearLayout detailsRow;
    private final TextView statusView;
    private final Runnable revealDetails = this::revealDetails;
    private ViewTreeObserver.OnPreDrawListener pendingFirstDrawListener;

    TrampolineLoadingView(Context context) {
        super(context);
        setBackgroundColor(context.getColor(R.color.trampoline_launch_background));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(content, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));

        avatarView = new ImageView(context);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        avatarView.setClipToOutline(true);
        content.addView(avatarView, new LinearLayout.LayoutParams(dp(76), dp(76)));

        detailsRow = new LinearLayout(context);
        detailsRow.setOrientation(LinearLayout.HORIZONTAL);
        detailsRow.setGravity(Gravity.CENTER_VERTICAL);
        detailsRow.setAlpha(0f);
        detailsRow.setVisibility(INVISIBLE);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        detailsParams.topMargin = dp(18);
        content.addView(detailsRow, detailsParams);

        ProgressBar progress = new ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleSmall
        );
        progress.setIndeterminateTintList(ColorStateList.valueOf(
                context.getColor(R.color.trampoline_launch_foreground)
        ));
        detailsRow.addView(progress, new LinearLayout.LayoutParams(dp(18), dp(18)));

        statusView = new TextView(context);
        statusView.setTextColor(context.getColor(R.color.trampoline_launch_foreground));
        statusView.setTextSize(14f);
        statusView.setMaxLines(2);
        statusView.setMaxWidth(dp(220));
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setTypeface(context.getResources().getFont(R.font.google_sans_flex_variable));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        statusParams.leftMargin = dp(10);
        detailsRow.addView(statusView, statusParams);
    }

    void showConversation(String conversationId, String title) {
        removeCallbacks(revealDetails);
        avatarView.animate().cancel();
        detailsRow.animate().cancel();

        Bitmap avatar = ConversationShortcuts.loadConversationAvatar(
                getContext(),
                conversationId
        );
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(getContext().getColor(R.color.trampoline_launch_container));
        avatarView.setBackground(background);
        if (avatar != null) {
            avatarView.clearColorFilter();
            avatarView.setPadding(0, 0, 0, 0);
            avatarView.setImageBitmap(avatar);
        } else {
            avatarView.setImageResource(R.drawable.ic_trampoline_launch_48);
            avatarView.setColorFilter(getContext().getColor(R.color.trampoline_launch_foreground));
            int padding = dp(20);
            avatarView.setPadding(padding, padding, padding, padding);
        }

        String displayTitle = title == null ? "" : title.trim();
        if (displayTitle.isEmpty()) displayTitle = getContext().getString(R.string.app_name);
        statusView.setText(getContext().getString(
                R.string.bubble_opening_wechat,
                displayTitle
        ));

        detailsRow.setVisibility(INVISIBLE);
        detailsRow.setAlpha(0f);
        if (ValueAnimator.areAnimatorsEnabled()) {
            avatarView.setAlpha(0.86f);
            avatarView.setScaleX(0.94f);
            avatarView.setScaleY(0.94f);
            avatarView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(AVATAR_ENTER_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        } else {
            avatarView.setAlpha(1f);
            avatarView.setScaleX(1f);
            avatarView.setScaleY(1f);
        }
        postDelayed(revealDetails, DETAILS_DELAY_MS);
    }

    void stop() {
        removeCallbacks(revealDetails);
        avatarView.animate().cancel();
        detailsRow.animate().cancel();
    }

    void runAfterNextDraw(Runnable action) {
        clearPendingFirstDrawListener();
        pendingFirstDrawListener = () -> {
            clearPendingFirstDrawListener();
            post(action);
            return true;
        };
        getViewTreeObserver().addOnPreDrawListener(pendingFirstDrawListener);
        invalidate();
    }

    private void revealDetails() {
        detailsRow.setVisibility(VISIBLE);
        if (!ValueAnimator.areAnimatorsEnabled()) {
            detailsRow.setAlpha(1f);
            return;
        }
        detailsRow.animate()
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void clearPendingFirstDrawListener() {
        if (pendingFirstDrawListener == null) return;
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(pendingFirstDrawListener);
        pendingFirstDrawListener = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        clearPendingFirstDrawListener();
        super.onDetachedFromWindow();
    }
}
