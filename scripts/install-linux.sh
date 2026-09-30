#!/usr/bin/env bash
# Builds the native Kommander executable (with a bundled JVM) and installs it for the
# current user, with an entry in the Linux application menu. No root needed.
#
#   ./scripts/install-linux.sh              build and install (or update)
#   ./scripts/install-linux.sh --uninstall  remove the app, icon and menu entry
#
# Install locations (override with PREFIX, default ~/.local):
#   $PREFIX/opt/kommander/                     app + bundled runtime
#   $PREFIX/bin/kommander                      launcher symlink
#   $PREFIX/share/icons/hicolor/.../kommander  icon (svg + 256px png)
#   $PREFIX/share/applications/kommander.desktop
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="${PREFIX:-$HOME/.local}"
APP_DIR="$PREFIX/opt/kommander"
BIN_LINK="$PREFIX/bin/kommander"
ICONS="$PREFIX/share/icons/hicolor"
DESKTOP_FILE="$PREFIX/share/applications/kommander.desktop"

refresh_menu() {
    command -v update-desktop-database >/dev/null && update-desktop-database -q "$PREFIX/share/applications" || true
    command -v gtk-update-icon-cache >/dev/null && gtk-update-icon-cache -q -t "$ICONS" 2>/dev/null || true
    # KDE Plasma caches the menu separately.
    command -v kbuildsycoca6 >/dev/null && kbuildsycoca6 >/dev/null 2>&1 || true
}

if [ "${1:-}" = "--uninstall" ]; then
    rm -rf "$APP_DIR"
    rm -f "$BIN_LINK" "$DESKTOP_FILE" "$ICONS/scalable/apps/kommander.svg" "$ICONS/256x256/apps/kommander.png"
    refresh_menu
    echo "Kommander removed."
    exit 0
fi

echo "==> Building the native executable (first run downloads dependencies)..."
"$ROOT/gradlew" -p "$ROOT" :desktopApp:createDistributable --console=plain -q

BUILT="$ROOT/desktopApp/build/compose/binaries/main/app/kommander"
[ -x "$BUILT/bin/kommander" ] || { echo "Build output not found at $BUILT" >&2; exit 1; }

echo "==> Installing into $APP_DIR"
if pgrep -f "$APP_DIR/" >/dev/null; then
    echo "    Kommander is running; close it to use the new version."
fi
rm -rf "$APP_DIR"
mkdir -p "$(dirname "$APP_DIR")" "$(dirname "$BIN_LINK")"
cp -r "$BUILT" "$APP_DIR"
ln -sf "$APP_DIR/bin/kommander" "$BIN_LINK"

install -Dm644 "$ROOT/desktopApp/icons/kommander.svg" "$ICONS/scalable/apps/kommander.svg"
install -Dm644 "$ROOT/desktopApp/icons/kommander.png" "$ICONS/256x256/apps/kommander.png"

mkdir -p "$(dirname "$DESKTOP_FILE")"
# _JAVA_AWT_WM_NONREPARENTING avoids a blank window on tiling/Wayland compositors;
# StartupWMClass lets the taskbar group the window under this entry's icon.
cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Name=Kommander
GenericName=Claude Code dashboard
Comment=See what Claude Code is doing, and in which repository
Exec=env _JAVA_AWT_WM_NONREPARENTING=1 $APP_DIR/bin/kommander
Icon=kommander
StartupWMClass=kommander
Terminal=false
Categories=Development;
Keywords=claude;agent;dashboard;hooks;
EOF
command -v desktop-file-validate >/dev/null && desktop-file-validate "$DESKTOP_FILE" || true

refresh_menu

echo "==> Done. Open \"Kommander\" from the application menu, or run: kommander"
case ":$PATH:" in
    *":$PREFIX/bin:"*) ;;
    *) echo "    ($PREFIX/bin is not on your PATH; the menu entry works regardless.)" ;;
esac
