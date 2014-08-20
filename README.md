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
inaccuracies.

License
-------
    Copyright 2014 Justas Medeisis
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.