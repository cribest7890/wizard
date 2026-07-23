# Application Wizard

A modern Java 21 / JavaFX desktop installer wizard for Linux, distributed as a
self-contained **AppImage** (bundling Eclipse Temurin JRE 21, so end users do
**not** need Java installed) or as a plain executable JAR.

This project is a full GUI re-implementation of an original bash installer
script, generalised to work across the Linux/freedesktop ecosystem.

> **Linux only.** This application intentionally does not support Windows or macOS.

## Supported distributions

Tested/designed for, and generally compatible with any freedesktop-compliant
Linux distribution:

- Arch Linux (`pacman`, `yay`)
- Debian (`apt`, `apt-get`)
- Ubuntu (`apt`, `apt-get`)
- Linux Mint (`apt`, `apt-get`)
- Pop!_OS (`apt`, `apt-get`)
- Fedora (`dnf`)
- openSUSE (`zypper`)
- Other freedesktop-compatible distributions (best-effort fallback detection)

Desktop environments: GNOME, KDE Plasma, XFCE, Cinnamon, MATE, and other
freedesktop-compliant environments (via standard `.desktop` files and XDG
base directories).

## What it does

1. **Lets you pick a file to install** — a generic executable/binary, an
   `.AppImage`, or a native distribution package (`.pkg`-style archive) — via
   drag & drop or a file chooser.
2. **Collects application metadata**: name, comment/description, category.
3. **Resolves an icon**: automatic web lookup, a local image/URL you provide
   (with drag & drop and live preview), or the default system icon.
4. **Detects your system**: Linux distribution (`/etc/os-release`), available
   package manager, and desktop environment — no assumptions are hard-coded.
5. **Installs the file**:
   - Native package → installed via the detected package manager (through
     `pkexec` for privilege escalation, the freedesktop/PolicyKit-standard
     graphical replacement for `sudo`).
   - AppImage → copied into `~/.local/share/wizard/<app>/bin/`, marked
     executable; checks for FUSE and offers to install it if missing.
   - Generic executable → copied (optionally together with its whole source
     folder), scanned for missing shared libraries via `ldd`, and any
     resolvable missing packages are installed automatically.
6. **Creates a `.desktop` launcher** in `~/.local/share/applications/`
   (and optionally on your Desktop, auto-detecting `~/Desktop`, `~/Scrivania`,
   or your configured `XDG_DESKTOP_DIR`), following the freedesktop.org
   Desktop Entry Specification.
7. **Self-integrates on first AppImage launch**: when *this* application is
   itself run from its own AppImage for the first time, it transparently
   copies itself to a permanent location, registers its own menu entry and
   icon, and only removes the original AppImage file once every step above
   has been verified to have succeeded. If anything fails, the original
   AppImage is left untouched and the process is retried on the next launch.

If no supported package manager is found on the system, the wizard shows a
clear on-screen message and continues with whatever steps are still possible
(e.g. copying files and creating the menu entry) rather than aborting.

## Architecture

```
com.wizard
├── Main                       entry point; OS guard, launches the JavaFX app
├── WizardApplication           JavaFX Application; first-run check + window setup
├── InstallerService             orchestrates the full install pipeline
├── ui/
│   ├── WizardController         wizard pages, navigation, drag & drop, dialogs
│   ├── DialogFactory             error/info/confirmation dialogs
│   └── ThemeManager              CSS theme application
├── system/
│   ├── LinuxSystemDetector       desktop environment + XDG paths
│   ├── DistributionDetector      /etc/os-release parsing
│   ├── PackageManager            pacman/yay/apt/apt-get/dnf/zypper abstraction
│   └── DependencyScanner         ldd-based missing-library detection
├── desktop/
│   └── DesktopEntryService       .desktop file generation & installation
├── appimage/
│   ├── AppImageService           AppImage runtime detection
│   └── FirstRunManager           self-installation on first AppImage run
├── icon/
│   ├── IconManager               icon resolution strategy
│   └── ImageDownloader           HTTP icon download / auto-lookup
├── settings/
│   └── SettingsManager           persisted settings (XDG config dir)
├── logging/
│   └── LoggerService             file + console + live UI logging
├── util/
│   ├── FileManager                file/directory copy, permissions
│   └── ValidationUtils            path expansion, sanitisation, validation
└── model/
    ├── InstallationRequest        data collected across wizard pages
    └── InstallationResult         outcome of an installation run
```

## Building

Requirements on the **build machine**: JDK 21 and Maven. (End users of the
resulting AppImage need neither.)

```bash
mvn clean package
```

This produces `target/wizard-app.jar`, an executable fat-jar (requires a
Java 21+ runtime to run directly).

### Building the AppImage

```bash
./build.sh
```

This runs `mvn clean package` and then `appimage/build-appimage.sh`, which:

1. Downloads **Eclipse Temurin JRE 21** (Adoptium) for the current CPU
   architecture (x86_64 or aarch64) directly from the official Adoptium API.
2. Assembles an `AppDir` containing the JRE, the application jar, the
   `AppRun` launcher script, the icon, and the AppImage-required `.desktop`
   file.
3. Downloads `appimagetool` (from the official AppImageKit GitHub releases)
   if not already cached in `target/`.
4. Produces `target/Application_Wizard-<arch>.AppImage`.

The build machine needs internet access to `api.adoptium.net` and
`github.com` for this step.

### Running for development

```bash
mvn javafx:run
```

## Running

- **AppImage** (recommended for end users): `chmod +x Application_Wizard-x86_64.AppImage && ./Application_Wizard-x86_64.AppImage`
  No Java installation is required.
- **JAR** (requires Java 21+ installed): `java -jar wizard-app.jar`

## License

see /LICENSE 
