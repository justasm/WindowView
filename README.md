WindowView
==========

*Window as in windowsill.*

![Tilting to pan images.](/sample/sample_in_action.gif)

An Android `ImageView` that can be panned around by tilting your device, as if you were looking
through a window.

Usage
-----
Add it to your project using Gradle:

```groovy
compile 'com.jmedeisis:windowview:0.1.0'
```

Use in place of an `ImageView`. Example XML layout file:

```xml
<com.jmedeisis.windowview.WindowView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:src="@drawable/my_image" />
```

Please refer to the included [sample application project](sample/) for a complete example.

Configuration
-------------
You will typically want to configure the following attributes for the `WindowView` class:

- `max_pitch` - maximum angle (in degrees) from origin for vertical device tilts. *Default - 30&deg;*

- `max_roll` - maximum angle (in degrees) from origin for horizontal device tilts. *Default - 30&deg;*

- `vertical_origin` - (in degrees) when device pitch equals this value, the image is centered
vertically. *Default - 0&deg;*

- `horizontal_origin` - (in degrees) when device roll equals this value, the image is centered
horizontally. *Default - 0&deg;*

You may also want to configure more advanced attributes:

- `orientation_mode` - `Absolute` or `Relative` (default). Specifies whether device tilt should be
tracked with respect to `Absolute` world coordinates (i.e. pitch, roll w.r.t. ground plane) or with
respect to the device orientation when `WindowView` is created, which `WindowView` refers to as the
'orientation origin'. If using the latter, i.e. `Relative`, you may use
`WindowView#resetOrientationOrigin(boolean)` to set the orientation origin to that of the device
when the method is called.

- `translate_mode` - `Constant` or `Proportional` (default). Specifies how much the image is
translated in response to device tilt. If `Proportional`, the image moves within the full range
defined by `max_pitch` / `max_roll`, with the extremities of the image visible when device pitch /
roll is at those angles. If `Constant`, the image moves a constant amount per unit of tilt which is
defined by `max_constant_translation`, achieved when pitch / roll are at `max_pitch` / `max_roll`.

- `max_constant_translation` - see above. *Default - 150dp*

- `sensor_sampling_period` - the desired rate of sensor events. In microseconds or one of `fast`,
`normal` (default) or `slow`. If using microsecond values, higher values result in slower sensor
updates. Directly related to the rate at which `WindowView` updates in response to device tilt.

Example configuration:

```xml
<com.jmedeisis.windowview.WindowView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:src="@drawable/my_image"
    app:orientation_mode="Relative"
    app:translate_mode="Constant"
    app:max_constant_translation="100dp"
    app:sensor_sampling_period="fast"
    app:max_pitch="15"
    app:max_roll="15"
    app:vertical_origin="0"
    app:horizontal_origin="0" />
```

Limitations
-----------
- Only supports the CENTER_CROP scale type.
- Works for API levels 9+.

Development
-----------
Pull requests are welcome and encouraged for bugfixes and features such as:

- adaptive smoothing filters tuned for different sensor accuracy and rates
- bi-directional image panning

License
-------
WindowView is licensed under the terms of the [MIT License](LICENSE.txt).