package com.ashen.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import java.io.IOException;

public final class MainActivity extends Activity {
    private GameView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            Data data = new Data(name -> Assets.readAsset(getAssets(), "data/" + name));
            Assets assets = new Assets(getAssets());
            assets.enabled = Json.getBool(data.game, "useTextures", true);
            World world = new World(data);
            view = new GameView(this, world, assets);
            setContentView(view);
        } catch (IOException e) {
            throw new RuntimeException("Could not load game data from assets/data", e);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onResume() {
        super.onResume();
        if (view != null) view.start();
    }

    @Override protected void onPause() {
        if (view != null) view.stop();
        super.onPause();
    }
}
