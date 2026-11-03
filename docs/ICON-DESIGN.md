# Red ribbon launcher icon

Version 1.9.36 adopts the user's selected concept 2: an ivory folded ribbon
combining a play cutout and a downward arrow on a red background. The source
concept was generated with OpenAI ImageGen for this project on 2026-11-03;
no external stock image, font or downloaded logo was added. This records asset
provenance and does not imply trademark clearance or commercial-rights review.

The selected image is preserved in the legacy launcher bitmap. For Android 8+
adaptive icons, it occupies the central 72dp of the 108dp layer; repeated edge
pixels fill the overscan area. This keeps the arrow inside circular masks.
Android 13+ themed icons use a separate monochrome alpha mask of the ribbon.
Both application icon attributes still resolve to the same versioned resource.
No dependency, permission, signing identity or download behavior changed.

## Previous vector design (1.9.35)

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
