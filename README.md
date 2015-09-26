WindowView
==========

*Window as in windowsill.*

![Tilting to pan images.](/sample/sample_in_action.gif)

An Android `ImageView` that can be panned around by tilting your device,
as if you were looking through a window.

Usage
-----
Simply use in place of an `ImageView`. An example usage in an XML layout file:

    <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:layout_width="match_parent"
        android:layout_height="match_parent" >
    
        <com.jmedeisis.windowview.WindowView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:src="@drawable/my_image"
            app:orientationMode="Relative"
            app:translateMode="Proportional"/>
    
    </FrameLayout>

You may also refer to the included sample application project.

Limitations
-----------
- Only supports the CENTER_CROP scale type.
- Works for API levels 9+.

Known Problems
--------------
- Images with non-standard scaled widths/heights may be translated a few pixels too far at extreme
orientations.

License
-------
WindowView is licensed under the terms of the [MIT License](LICENSE.txt).