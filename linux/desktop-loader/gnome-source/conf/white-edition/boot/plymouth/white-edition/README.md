# Ubuntu White Edition — Plymouth boot splash

The splash shown during early boot (after GRUB, while the kernel and initramfs
bring the system up). This theme replaces the stock Ubuntu spinner with the White
Edition presentation so the white identity is unbroken from the GRUB menu into the
login greeter.

## Files

```text
plymouth/white-edition/
├── README.md                ← this file
├── white-edition.plymouth   ← theme descriptor (ModuleName=script)
└── white-edition.script     ← the splash: white field, centered mark, red progress
```

An optional `logo.png` (the White Edition mark) is placed next to the script at
install time. If it is absent, the script draws a typographic "Ubuntu White
Edition" mark instead, so the splash always renders.

## What it does

- Fills the screen white (`#ffffff`).
- Centers the White Edition mark, dark gray (`#303030`).
- Draws a thin Ubuntu-red (`#e95420`) progress bar on a neutral-gray (`#dddddd`)
  track.
- Shows boot stage messages in muted gray (`#666666`).
- Keeps the encrypted-disk password prompt white-skinned.
- Honors the single stationary upper-left key light — nothing animates a light
  source; only the progress fill moves.

## Install (done by ../../install-boot-presentation.sh)

1. Copy this directory to `<root>/usr/share/plymouth/themes/white-edition/`.
2. Place the White Edition logo as `logo.png` in that directory (optional).
3. Inside the target root, make it the default theme. On Ubuntu this is done via
   the alternatives system and the plymouth helper:

   ```sh
   update-alternatives --install \
     /usr/share/plymouth/themes/default.plymouth default.plymouth \
     /usr/share/plymouth/themes/white-edition/white-edition.plymouth 200
   plymouth-set-default-theme white-edition
   update-initramfs -u
   ```

   The `update-initramfs -u` step is required so the theme is embedded in the
   initramfs and appears during real early boot.

## Notes

- This is the `script` Plymouth module, which is present in Ubuntu's
  `plymouth-theme-*` packages. It does not require compiling a custom C module.
- The theme is reversible: `plymouth-set-default-theme <other>` +
  `update-initramfs -u` restores any prior theme.
- Colors are the White Edition tokens shared with `../../../theme/white-edition.css`.
