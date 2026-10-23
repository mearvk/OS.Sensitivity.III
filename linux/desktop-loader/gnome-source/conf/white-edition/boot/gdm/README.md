# Ubuntu White Edition — GDM greeter and session offering

This directory makes the **login greeter** white-skinned and makes the greeter's
session chooser **offer "Ubuntu White"** as the default session. This is the
stage the first-boot requirement calls "the white flavored Ubuntu offering."

## Files

```text
gdm/
├── README.md                ← this file
├── 10-white-edition         ← GDM dconf defaults (styles the greeter itself)
├── ubuntu-white.desktop     ← the session entry shown in the greeter chooser
└── ubuntu-white.session     ← the gnome-session definition it launches
```

## What each file does

- **`10-white-edition`** — greeter presentation. GDM runs under its own dconf
  profile, so these keys (`prefer-light`, `Ubuntu-White` icons, solid white
  background, "Ubuntu White Edition" banner) style the login screen only, not user
  sessions. Installed to `/etc/dconf/db/gdm.d/` and compiled with `dconf update`.
- **`ubuntu-white.desktop`** — the selectable session. Installed to
  `/usr/share/wayland-sessions/ubuntu-white.desktop` (and/or `xsessions/`). Its
  `Name=Ubuntu White` is what the greeter's gear/session menu lists.
- **`ubuntu-white.session`** — the gnome-session component list that
  `gnome-session --session=ubuntu-white` runs. It reuses upstream GNOME Shell and
  the standard settings daemons; the White Edition look comes from the desktop
  dconf/theme layer (`../../db`, `../../theme`). Installed to
  `/usr/share/gnome-session/sessions/ubuntu-white.session`.

## Making it the default session

So first boot lands on the offering without the user choosing it, the installer
sets AccountsService's default session for created users (and the greeter default):

```ini
# /var/lib/AccountsService/users/<user>
[User]
Session=ubuntu-white
XSession=ubuntu-white
```

This is applied by `../../install-boot-presentation.sh` for the installer's
created user, and can also be set as the system-wide default. It remains a
default: the user can pick a different session from the greeter chooser.

## What the user sees

1. White greeter (light, Ubuntu-White accents, "Ubuntu White Edition" banner).
2. A session chooser listing **"Ubuntu White"**, pre-selected.
3. Logging in launches the White Edition GNOME session, which the desktop
   dconf/theme layer paints white (`prefer-light`, `Ubuntu-White`, White Edition
   CSS/lighting).

## Notes

- These are overlays on stock GDM and gnome-session; no component is forked.
- Everything is reversible: remove the dconf drop-in + `dconf update`, remove the
  session/desktop files, and clear the AccountsService `Session` key.
- Colors and scheme match `../../theme/white-edition.css` and
  `../../db/local.d/00-white-edition` so greeter and desktop are one system.
