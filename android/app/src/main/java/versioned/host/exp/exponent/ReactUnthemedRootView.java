package versioned.host.exp.exponent;

import android.content.Context;
import android.view.ContextThemeWrapper;

import com.facebook.react.ReactRootView;
import host.exp.exponent.R;

public class ReactUnthemedRootView extends ReactRootView {
  public ReactUnthemedRootView(Context context) {
    super(new ContextThemeWrapper(
        context,
        R.style.Theme_Exponent_None
    ));
  }
}
