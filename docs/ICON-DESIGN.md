# Red launcher icon

Version 1.9.35 uses original Android vector artwork: a bright red background
(`#E92338`), a darker red lower sweep (`#C61930`), and white play, download and
tray shapes. The icon contains no text, external image, font or borrowed logo.

Android 8 and newer use independent background and foreground layers. The
foreground artwork stays inside the central adaptive-icon safe area so launcher
masks can vary without cutting off the symbols. Android 13 themed icons reuse
the foreground as a monochrome layer; the launcher chooses that optional theme's
color. With themed icons disabled, the icon remains red.

Android 7 uses the same artwork as a single vector with a rounded square
background. Both `android:icon` and `android:roundIcon` resolve through the
versioned mipmap resource. No dependency, permission, network behavior or signing
identity changes are needed for this design.

The icon resources are original project artwork. Existing third-party notices
and store/commercial distribution gates continue to apply to the rest of the app.
