WindowView
==========

*Window as in windowsill.*

An Android `ImageView` that can be panned around by tilting your device,
as if you were looking through a window.

Usage
-----
Simply use in place of an `ImageView`. An example usage in an XML layout file:

    <com.jmedeisis.windowview.WindowView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:src="@drawable/my_image" />

You may also refer to the included sample application project.

Limitations
-----------
- Only supports the CENTER_CROP scale type.
- Work for API levels 9+.

Known Problems
--------------
- Images with non-standard scaled widths/heights may be translated a few pixels too far at extreme
orientations.
- Images with large/extreme aspect ratios may experience slight jitters due to sensor data
inaccuracies. Tweaking the `NUM_FILTER_SAMPLES` and `LOW_PASS_COEFF` values in `WindowView` may
reduce jitter at the expense of responsiveness.

License
-------
This project is licensed under the terms of the MIT license. You may find a copy of the license
in the included LICENSE file.